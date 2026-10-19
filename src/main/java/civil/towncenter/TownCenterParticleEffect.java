package civil.towncenter;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client: town center lectern ring + column (active) or hexagonal falling enchant lines (shutdown).
 */
public final class TownCenterParticleEffect {

    private TownCenterParticleEffect() {
    }

    private static final ColorParticleOption GREEN_MIST =
            ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.35f, 0.85f, 0.45f);

    private static final double MAX_RENDER_DIST = 48.0;
    private static final double MAX_RENDER_DIST_SQ = MAX_RENDER_DIST * MAX_RENDER_DIST;

    private static final double RING_Y_OFFSET = 1.35;
    private static final double COLUMN_BOTTOM_OFFSET = 0.5;
    private static final double COLUMN_RADIUS = 0.12;

    private static final double SHUTDOWN_VERTEX_RADIUS = 1.55;
    private static final double SHUTDOWN_LINE_JITTER = 0.06;
    private static final double SHUTDOWN_Y_START_OFFSET = 3.2;
    private static final double SHUTDOWN_Y_END_OFFSET = 0.35;
    private static final long SHUTDOWN_FALL_DURATION_NS = 400_000_000L;
    private static final long SHUTDOWN_SPAWN_MIN_NS = 280_000_000L;
    private static final long SHUTDOWN_SPAWN_MAX_NS = 520_000_000L;

    private static final long ACTIVE_TICK_INTERVAL_NS = 125_000_000L;
    private static final long SHUTDOWN_TICK_INTERVAL_NS = 50_000_000L;

    // Beacon ambient（频率约为原先一倍：6–12s）
    private static final float AMBIENT_BASE_VOLUME = 0.40f;
    private static final float AMBIENT_VOLUME_PER_LEVEL = 0.05f;
    private static final long AMBIENT_INTERVAL_MIN_NS = 6_000_000_000L;
    private static final long AMBIENT_INTERVAL_MAX_NS = 12_000_000_000L;

    private static final long CHIME_ROLL_INTERVAL_MIN_NS = 1_000_000_000L;
    private static final long CHIME_ROLL_INTERVAL_MAX_NS = 2_000_000_000L;
    private static final double CHIME_START_PROBABILITY = 0.42;
    private static final double CHIME_TRIPLE_CHECKMARK_PROBABILITY = 0.28;

    private static final long PAGE_TURN_ROLL_INTERVAL_MIN_NS = 800_000_000L;
    private static final long PAGE_TURN_ROLL_INTERVAL_MAX_NS = 1_800_000_000L;
    private static final double PAGE_TURN_PROBABILITY = 0.78;

    private static final float FIFTH = (float) Math.pow(2.0, 7.0 / 12.0);
    private static final float MINOR_THIRD_UP = (float) Math.pow(2.0, 3.0 / 12.0);
    private static final float MINOR_THIRD_DOWN = 1f / MINOR_THIRD_UP;

    private static final int CHIME_DOUBLE_UP = 1;
    private static final int CHIME_TRIPLE_UP = 2;
    private static final int CHIME_TRIPLE_CHECK = 3;
    private static final int CHIME_DOUBLE_DOWN = 4;
    private static final int CHIME_TRIPLE_DOWN = 5;

    /** 环 burst 竖向散布高度（偏向上方展开）。 */
    private static final double RING_BURST_VERTICAL_SPAN = 1.25;
    private static final double RING_BURST_VERTICAL_BIAS = 0.28;

    // Ambient 插入的 chime 间隔（ms）
    private static final long CHIME_DOUBLE_STEP_MS = 300L;
    private static final long CHIME_TRIPLE_STEP2_MS = 250L;
    private static final long CHIME_TRIPLE_STEP3_MS = 450L;

    private static final int SHUTDOWN_VERTEX_COUNT = 6;
    private static final double[] SHUTDOWN_ANGLES_RAD = {
            0.0,
            Math.toRadians(60.0),
            Math.toRadians(120.0),
            Math.toRadians(180.0),
            Math.toRadians(240.0),
            Math.toRadians(300.0),
    };

    private static final class LevelParams {
        final double ringRadius;
        final double ringSpawnChance;
        final double ringSecondChance;
        final double columnTopOffset;
        final double columnSpawnChance;

        LevelParams(double ringRadius, double ringSpawnChance, double ringSecondChance,
                    double columnTopOffset, double columnSpawnChance) {
            this.ringRadius = ringRadius;
            this.ringSpawnChance = ringSpawnChance;
            this.ringSecondChance = ringSecondChance;
            this.columnTopOffset = columnTopOffset;
            this.columnSpawnChance = columnSpawnChance;
        }
    }

    private static final LevelParams[] LEVEL_PARAMS = {
            null,
            new LevelParams(1.2, 0.28, 0.05, 2.2, 0.38),
            new LevelParams(1.35, 0.32, 0.06, 2.4, 0.42),
            new LevelParams(1.5, 0.36, 0.06, 2.6, 0.46),
            new LevelParams(1.65, 0.40, 0.07, 2.8, 0.50),
            new LevelParams(1.8, 0.44, 0.08, 3.0, 0.54),
    };

    private record TcState(int x, int y, int z, byte tier) {
    }

    private static final class ShutdownLineState {
        final double vertexX;
        final double vertexZ;
        long nextSpawnAtNs;
        long fallActiveUntilNs;
        long fallStartNs;

        ShutdownLineState(double vertexX, double vertexZ, long nowNs, ThreadLocalRandom rng) {
            this.vertexX = vertexX;
            this.vertexZ = vertexZ;
            scheduleNextSpawn(nowNs, rng);
        }

        void scheduleNextSpawn(long nowNs, ThreadLocalRandom rng) {
            long delay = SHUTDOWN_SPAWN_MIN_NS + (long) (rng.nextDouble() * (SHUTDOWN_SPAWN_MAX_NS - SHUTDOWN_SPAWN_MIN_NS));
            nextSpawnAtNs = nowNs + delay;
        }

        void beginFall(long nowNs) {
            fallStartNs = nowNs;
            fallActiveUntilNs = nowNs + SHUTDOWN_FALL_DURATION_NS;
        }

        boolean isFallActive(long nowNs) {
            return fallActiveUntilNs > nowNs;
        }
    }

    private static final List<TcState> ACTIVE = new CopyOnWriteArrayList<>();
    private static final Map<Long, ShutdownLineState[]> SHUTDOWN_LINES = new ConcurrentHashMap<>();
    private static final Map<Long, Long> LAST_ACTIVE_PARTICLE_NS = new ConcurrentHashMap<>();
    private static final Map<Long, AmbientState> AMBIENT_STATE = new ConcurrentHashMap<>();

    private static final class AmbientState {
        long nextAmbientAtNs;
        long nextChimeRollAtNs;
        long nextPageTurnRollAtNs;

        int chimeVariant;
        int chimeStepIndex;
        long nextChimeStepAtNs;
        float chimeRootPitch;
        float chimePitch2;
        float chimePitch3;

        boolean shutdownMode;

        AmbientState(long nowNs, ThreadLocalRandom rng, boolean shutdownMode) {
            this.shutdownMode = shutdownMode;
            scheduleNextAmbient(nowNs, rng);
            scheduleNextChimeRoll(nowNs, rng);
            if (!shutdownMode) {
                scheduleNextPageTurnRoll(nowNs, rng);
            }
        }

        void scheduleNextAmbient(long nowNs, ThreadLocalRandom rng) {
            long interval = AMBIENT_INTERVAL_MIN_NS
                    + (long) (rng.nextDouble() * (AMBIENT_INTERVAL_MAX_NS - AMBIENT_INTERVAL_MIN_NS));
            nextAmbientAtNs = nowNs + interval;
        }

        void scheduleNextChimeRoll(long nowNs, ThreadLocalRandom rng) {
            long interval = CHIME_ROLL_INTERVAL_MIN_NS
                    + (long) (rng.nextDouble() * (CHIME_ROLL_INTERVAL_MAX_NS - CHIME_ROLL_INTERVAL_MIN_NS));
            nextChimeRollAtNs = nowNs + interval;
        }

        void scheduleNextPageTurnRoll(long nowNs, ThreadLocalRandom rng) {
            long interval = PAGE_TURN_ROLL_INTERVAL_MIN_NS
                    + (long) (rng.nextDouble() * (PAGE_TURN_ROLL_INTERVAL_MAX_NS - PAGE_TURN_ROLL_INTERVAL_MIN_NS));
            nextPageTurnRollAtNs = nowNs + interval;
        }

        void beginChime(int variant, long nowNs, ThreadLocalRandom rng) {
            chimeVariant = variant;
            chimeStepIndex = 0;
            chimeRootPitch = 0.90f + rng.nextFloat() * 0.22f;
            nextChimeStepAtNs = nowNs;

            switch (variant) {
                case CHIME_DOUBLE_UP -> chimePitch2 = chimeRootPitch * FIFTH;
                case CHIME_TRIPLE_UP -> {
                    chimePitch2 = chimeRootPitch * MINOR_THIRD_UP;
                    chimePitch3 = chimeRootPitch * FIFTH;
                }
                case CHIME_TRIPLE_CHECK -> {
                    chimePitch2 = chimeRootPitch;
                    chimePitch3 = chimeRootPitch * FIFTH;
                }
                case CHIME_DOUBLE_DOWN -> chimePitch2 = chimeRootPitch / FIFTH;
                case CHIME_TRIPLE_DOWN -> {
                    chimePitch2 = chimeRootPitch * MINOR_THIRD_DOWN;
                    chimePitch3 = chimePitch2 * MINOR_THIRD_DOWN;
                }
                default -> chimePitch2 = chimeRootPitch;
            }
        }

        void finishChime(long nowNs, ThreadLocalRandom rng) {
            chimeVariant = 0;
            chimeStepIndex = 0;
            nextChimeStepAtNs = 0L;
            scheduleNextChimeRoll(nowNs, rng);
        }
    }

    private static long lastShutdownTickNano = 0;

    public static void updateFromPayload(TownCenterParticlePayload payload) {
        List<TcState> next = new ArrayList<>(payload.entries().size());
        Map<Long, ShutdownLineState[]> nextShutdown = new ConcurrentHashMap<>();
        long now = System.nanoTime();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (var e : payload.entries()) {
            next.add(new TcState(e.x(), e.y(), e.z(), e.tier()));
            if (e.tier() == TownCenterParticlePayload.Entry.TIER_SHUTDOWN) {
                long key = BlockPos.asLong(e.x(), e.y(), e.z());
                double cx = e.x() + 0.5;
                double cz = e.z() + 0.5;
                ShutdownLineState[] lines = new ShutdownLineState[SHUTDOWN_VERTEX_COUNT];
                for (int i = 0; i < SHUTDOWN_VERTEX_COUNT; i++) {
                    double ang = SHUTDOWN_ANGLES_RAD[i];
                    double vx = cx + SHUTDOWN_VERTEX_RADIUS * Math.cos(ang);
                    double vz = cz + SHUTDOWN_VERTEX_RADIUS * Math.sin(ang);
                    lines[i] = new ShutdownLineState(vx, vz, now, rng);
                }
                nextShutdown.put(key, lines);
            }
        }

        ACTIVE.clear();
        ACTIVE.addAll(next);
        SHUTDOWN_LINES.clear();
        SHUTDOWN_LINES.putAll(nextShutdown);
        LAST_ACTIVE_PARTICLE_NS.clear();
        syncAmbientStateForPayload(next);
        lastShutdownTickNano = 0;
    }

    private static void syncAmbientStateForPayload(List<TcState> states) {
        Set<Long> keys = new HashSet<>();
        long now = System.nanoTime();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (TcState s : states) {
            long key = BlockPos.asLong(s.x(), s.y(), s.z());
            keys.add(key);
            boolean shutdown = s.tier() == TownCenterParticlePayload.Entry.TIER_SHUTDOWN;
            AMBIENT_STATE.compute(key, (k, st) -> {
                if (st == null) {
                    return new AmbientState(now, rng, shutdown);
                }
                st.shutdownMode = shutdown;
                return st;
            });
        }
        AMBIENT_STATE.keySet().removeIf(k -> !keys.contains(k));
    }

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.isPaused()) {
            return;
        }
        if (client.level == null || client.player == null) {
            return;
        }

        long now = System.nanoTime();
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
        Level world = client.level;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        boolean shutdownFrame = now - lastShutdownTickNano >= SHUTDOWN_TICK_INTERVAL_NS;
        if (shutdownFrame) {
            lastShutdownTickNano = now;
        }

        for (TcState s : ACTIVE) {
            double cx = s.x() + 0.5;
            double ringCy = s.y() + RING_Y_OFFSET;
            double cz = s.z() + 0.5;
            double dx = cx - px;
            double dy = ringCy - py;
            double dz = cz - pz;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DIST_SQ) {
                continue;
            }

            if (s.tier() == TownCenterParticlePayload.Entry.TIER_SHUTDOWN) {
                if (shutdownFrame) {
                    tickShutdown(world, now, s, rng);
                }
                tickAmbientSound(world, now, s.x(), s.y(), s.z(), cx, ringCy, cz, 3, true);
                continue;
            }

            int level = Math.clamp(s.tier(), 1, 5);
            tickAmbientSound(world, now, s.x(), s.y(), s.z(), cx, ringCy, cz, level, false);

            long key = BlockPos.asLong(s.x(), s.y(), s.z());
            long lastActive = LAST_ACTIVE_PARTICLE_NS.getOrDefault(key, 0L);
            if (now - lastActive < ACTIVE_TICK_INTERVAL_NS) {
                continue;
            }
            LAST_ACTIVE_PARTICLE_NS.put(key, now);

            LevelParams params = LEVEL_PARAMS[level];
            if (rng.nextDouble() < params.ringSpawnChance) {
                spawnRingBurst(world, rng, cx, ringCy, cz, params.ringRadius);
            }

            double columnMidY = s.y() + COLUMN_BOTTOM_OFFSET + 0.5 * (params.columnTopOffset - COLUMN_BOTTOM_OFFSET);
            double dcx = cx - px;
            double dcy = columnMidY - py;
            double dcz = cz - pz;
            if (dcx * dcx + dcy * dcy + dcz * dcz <= MAX_RENDER_DIST_SQ
                    && rng.nextDouble() < params.columnSpawnChance) {
                spawnOneColumnParticle(world, rng, s.y(), cx, cz, params.columnTopOffset);
            }
        }
    }

    private static void tickShutdown(Level world, long nowNs, TcState s, ThreadLocalRandom rng) {
        ShutdownLineState[] lines = SHUTDOWN_LINES.get(BlockPos.asLong(s.x(), s.y(), s.z()));
        if (lines == null) {
            return;
        }

        double yStart = s.y() + SHUTDOWN_Y_START_OFFSET;
        double yEnd = s.y() + SHUTDOWN_Y_END_OFFSET;

        for (ShutdownLineState line : lines) {
            if (line.isFallActive(nowNs)) {
                float t = (float) (nowNs - line.fallStartNs) / (float) SHUTDOWN_FALL_DURATION_NS;
                if (t > 1.0f) {
                    t = 1.0f;
                }
                int count = 2 + rng.nextInt(3);
                for (int i = 0; i < count; i++) {
                    float sampleT = Math.min(1.0f, t + rng.nextFloat() * 0.08f);
                    double py = yStart + (yEnd - yStart) * sampleT;
                    double jx = (rng.nextDouble() - 0.5) * 2.0 * SHUTDOWN_LINE_JITTER;
                    double jz = (rng.nextDouble() - 0.5) * 2.0 * SHUTDOWN_LINE_JITTER;
                    world.addParticle(ParticleTypes.ENCHANT,
                            line.vertexX + jx, py, line.vertexZ + jz,
                            0.0, -0.02, 0.0);
                }
            } else if (nowNs >= line.nextSpawnAtNs) {
                line.beginFall(nowNs);
                line.scheduleNextSpawn(nowNs, rng);
            }
        }
    }

    private static void spawnRingBurst(Level world, ThreadLocalRandom rng, double cx, double cy, double cz, double radius) {
        int count = 2 + rng.nextInt(4);
        double ang = rng.nextDouble() * Math.PI * 2.0;
        double rx = cx + radius * Math.cos(ang);
        double rz = cz + radius * Math.sin(ang);
        for (int i = 0; i < count; i++) {
            double ry = cy + (rng.nextDouble() - RING_BURST_VERTICAL_BIAS) * RING_BURST_VERTICAL_SPAN;
            double jx = (rng.nextDouble() - 0.5) * 0.06;
            double jz = (rng.nextDouble() - 0.5) * 0.06;
            if (rng.nextBoolean()) {
                world.addParticle(ParticleTypes.HAPPY_VILLAGER, rx + jx, ry, rz + jz, 0.0, 0.01, 0.0);
            } else {
                world.addParticle(ParticleTypes.ENCHANT, rx + jx, ry, rz + jz, 0.0, 0.015, 0.0);
            }
        }
    }

    private static void spawnOneColumnParticle(Level world, ThreadLocalRandom rng, int blockMinY,
                                               double cx, double cz, double columnTopOffset) {
        double baseY = blockMinY + COLUMN_BOTTOM_OFFSET;
        double height = columnTopOffset - COLUMN_BOTTOM_OFFSET;
        double py = baseY + rng.nextDouble() * height;
        double ang = rng.nextDouble() * Math.PI * 2.0;
        double rr = Math.sqrt(rng.nextDouble()) * COLUMN_RADIUS;
        double px = cx + rr * Math.cos(ang);
        double pz = cz + rr * Math.sin(ang);
        if (rng.nextBoolean()) {
            world.addParticle(ParticleTypes.END_ROD, px, py, pz, 0.0, 0.006, 0.0);
        } else {
            world.addParticle(GREEN_MIST, px, py, pz, 0.0, 0.015, 0.0);
        }
    }

    private static void tickAmbientSound(Level world, long nowNs, int bx, int by, int bz,
                                          double ax, double ay, double az, int level, boolean shutdown) {
        long key = BlockPos.asLong(bx, by, bz);
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        AmbientState st = AMBIENT_STATE.get(key);
        if (st == null) {
            st = new AmbientState(nowNs, rng, shutdown);
            AMBIENT_STATE.put(key, st);
        }
        st.shutdownMode = shutdown;

        // 1) 正在播 chime 序列：按步进推进（与 ambient 无关）
        if (st.chimeVariant != 0 && nowNs >= st.nextChimeStepAtNs) {
            advanceChimeSequence(world, nowNs, ax, ay, az, st, rng);
        }

        // 2) 独立 chime roll
        if (st.chimeVariant == 0 && nowNs >= st.nextChimeRollAtNs) {
            st.scheduleNextChimeRoll(nowNs, rng);
            if (rng.nextDouble() < CHIME_START_PROBABILITY) {
                int variant;
                if (st.shutdownMode) {
                    variant = rng.nextDouble() < 0.55 ? CHIME_DOUBLE_DOWN : CHIME_TRIPLE_DOWN;
                } else if (rng.nextDouble() < 0.55) {
                    variant = CHIME_DOUBLE_UP;
                } else if (rng.nextDouble() < CHIME_TRIPLE_CHECKMARK_PROBABILITY) {
                    variant = CHIME_TRIPLE_CHECK;
                } else {
                    variant = CHIME_TRIPLE_UP;
                }
                st.beginChime(variant, nowNs, rng);
                advanceChimeSequence(world, nowNs, ax, ay, az, st, rng);
            }
        }

        if (!st.shutdownMode && nowNs >= st.nextPageTurnRollAtNs) {
            st.scheduleNextPageTurnRoll(nowNs, rng);
            if (rng.nextDouble() < PAGE_TURN_PROBABILITY) {
                float pitch = 0.88f + rng.nextFloat() * 0.24f;
                world.playLocalSound(ax, ay, az,
                        SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS,
                        0.34f, pitch, false);
            }
        }

        // 4) Beacon ambient
        if (nowNs >= st.nextAmbientAtNs) {
            float volume = AMBIENT_BASE_VOLUME + AMBIENT_VOLUME_PER_LEVEL * (level - 1);
            float pitch = 0.92f + rng.nextFloat() * 0.08f;
            world.playLocalSound(ax, ay, az,
                    SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS,
                    volume, pitch, false);
            st.scheduleNextAmbient(nowNs, rng);
        }
    }

    private static void advanceChimeSequence(Level world, long nowNs, double ax, double ay, double az,
                                             AmbientState st, ThreadLocalRandom rng) {
        if (st.chimeVariant == CHIME_DOUBLE_UP || st.chimeVariant == CHIME_DOUBLE_DOWN) {
            if (st.chimeStepIndex == 0) {
                playChime(world, ax, ay, az, 0.26f, st.chimeRootPitch);
                st.chimeStepIndex = 1;
                st.nextChimeStepAtNs = nowNs + CHIME_DOUBLE_STEP_MS * 1_000_000L;
            } else {
                playChime(world, ax, ay, az, 0.18f, st.chimePitch2);
                st.finishChime(nowNs, rng);
            }
            return;
        }

        if (st.chimeStepIndex == 0) {
            float pitch = st.chimeVariant == CHIME_TRIPLE_CHECK ? st.chimePitch3 : st.chimeRootPitch;
            playChime(world, ax, ay, az, 0.24f, pitch);
            st.chimeStepIndex = 1;
            st.nextChimeStepAtNs = nowNs + CHIME_TRIPLE_STEP2_MS * 1_000_000L;
        } else if (st.chimeStepIndex == 1) {
            playChime(world, ax, ay, az, 0.20f, st.chimePitch2);
            st.chimeStepIndex = 2;
            st.nextChimeStepAtNs = nowNs + CHIME_TRIPLE_STEP3_MS * 1_000_000L;
        } else {
            playChime(world, ax, ay, az, 0.16f, st.chimePitch3);
            st.finishChime(nowNs, rng);
        }
    }

    private static void playChime(Level world, double ax, double ay, double az, float volume, float pitch) {
        world.playLocalSound(ax, ay, az,
                SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS,
                volume, pitch, false);
    }
}
