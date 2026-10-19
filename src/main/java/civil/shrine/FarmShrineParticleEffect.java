package civil.shrine;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client: activated farm shrine visuals — (1) sparse sparkles on a ~1.5 block horizontal ring
 * around the anchor, and (2) a slightly denser column of soul / tinted {@link ParticleTypes#ENTITY_EFFECT}
 * particles in a thin vertical volume: from the anchor block’s vertical center (Y = block min + 0.5)
 * up to Y = block min + 3.0, thin radius in XZ around the block center.
 *
 * <p>Ambient: periodic short conduit ambience ({@link net.minecraft.sounds.SoundEvents#CONDUIT_AMBIENT_SHORT})
 * at each shrine (client, distance-culled), similar cadence to {@link civil.respawn.UndyingAnchorParticleEffect}.
 */
public final class FarmShrineParticleEffect {

    private FarmShrineParticleEffect() {
    }

    /** Purple channel (approx. ender / dragon breath vibe; ENTITY_EFFECT is typed for addParticle). */
    private static final ColorParticleOption PURPLE_MIST =
            ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.55f, 0.12f, 0.95f);

    private static final double MAX_RENDER_DIST = 48.0;
    private static final double MAX_RENDER_DIST_SQ = MAX_RENDER_DIST * MAX_RENDER_DIST;

    /** Horizontal ring radius in blocks (1.5 → ~3 block diameter). */
    private static final double RING_RADIUS = 1.5;
    /** Base ring sparkle: ~50% purple / soul. */
    private static final double BASE_PURPLE_CHANCE = 0.5;
    /** Vertical companions (+0.2 / +0.4): favour ENTITY_EFFECT. */
    private static final double COLUMN_PURPLE_CHANCE = 0.82;
    /** Slightly above block base (soul campfire). */
    private static final double RING_Y_OFFSET = 0.35;
    /** Per shrine per tick: probability of spawning at least one sparkle on the ring. */
    private static final double RING_SPAWN_CHANCE = 0.38;
    /** Rare second sparkle at another random angle on the same tick. */
    private static final double RING_SECOND_SPARKLE_CHANCE = 0.06;

    /**
     * Vertical column: Y from (block min + 0.5) through (block min + 3.0), i.e. height 2.5;
     * horizontal radius 0.1 around block XZ center.
     */
    private static final double COLUMN_BOTTOM_OFFSET = 0.5;
    private static final double COLUMN_TOP_OFFSET = 3.0;
    private static final double COLUMN_HEIGHT = COLUMN_TOP_OFFSET - COLUMN_BOTTOM_OFFSET;
    private static final double COLUMN_RADIUS = 0.1;
    /** Upper column: strict 50% soul fire / 50% purple. */
    private static final double COLUMN_MIX_PURPLE_CHANCE = 0.5;
    /** Slightly higher hit rate than {@link #RING_SPAWN_CHANCE}. */
    private static final double COLUMN_SPAWN_CHANCE = 0.5;
    private static final double COLUMN_SECOND_SPARKLE_CHANCE = 0.09;

    private static final List<ShrinePos> ACTIVE = new CopyOnWriteArrayList<>();
    private static long lastTickNano = 0;
    /** ~8 Hz — sparse enough that the ring is felt as occasional hits, not a band. */
    private static final long TICK_INTERVAL_NS = 125_000_000L;

    private static final float AMBIENT_VOLUME = 0.75f;
    private static final long AMBIENT_INTERVAL_NS = 5_000_000_000L;
    private static final Map<Long, Long> LAST_AMBIENT_NS = new ConcurrentHashMap<>();

    private record ShrinePos(int x, int y, int z) {
    }

    public static void updateFromPayload(FarmShrineParticlePayload payload) {
        List<ShrinePos> next = new ArrayList<>(payload.entries().size());
        for (var e : payload.entries()) {
            next.add(new ShrinePos(e.x(), e.y(), e.z()));
        }
        ACTIVE.clear();
        ACTIVE.addAll(next);
        LAST_AMBIENT_NS.clear();
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
        if (now - lastTickNano < TICK_INTERVAL_NS) {
            return;
        }
        lastTickNano = now;

        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
        Level world = client.level;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (ShrinePos s : ACTIVE) {
            double cx = s.x() + 0.5;
            double ringCy = s.y() + RING_Y_OFFSET;
            double cz = s.z() + 0.5;
            double dx = cx - px;
            double dy = ringCy - py;
            double dz = cz - pz;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DIST_SQ) {
                continue;
            }

            tickAmbientSound(world, now, s.x(), s.y(), s.z(), cx, ringCy, cz);

            if (rng.nextDouble() < RING_SPAWN_CHANCE) {
                spawnOneRingSparkle(world, rng, cx, ringCy, cz);
                if (rng.nextDouble() < RING_SECOND_SPARKLE_CHANCE) {
                    spawnOneRingSparkle(world, rng, cx, ringCy, cz);
                }
            }

            double columnMidY = s.y() + COLUMN_BOTTOM_OFFSET + 0.5 * COLUMN_HEIGHT;
            double dcx = cx - px;
            double dcy = columnMidY - py;
            double dcz = cz - pz;
            if (dcx * dcx + dcy * dcy + dcz * dcz <= MAX_RENDER_DIST_SQ && rng.nextDouble() < COLUMN_SPAWN_CHANCE) {
                spawnOneUpperColumnParticle(world, rng, s.y(), cx, cz);
                if (rng.nextDouble() < COLUMN_SECOND_SPARKLE_CHANCE) {
                    spawnOneUpperColumnParticle(world, rng, s.y(), cx, cz);
                }
            }
        }
    }

    private static void spawnOneRingSparkle(Level world, ThreadLocalRandom rng, double cx, double cy, double cz) {
        double ang = rng.nextDouble() * Math.PI * 2.0;
        double rx = cx + RING_RADIUS * Math.cos(ang);
        double rz = cz + RING_RADIUS * Math.sin(ang);
        double ry = cy + (rng.nextDouble() - 0.5) * 0.08;
        spawnSparkle(world, rng, rx, ry, rz, BASE_PURPLE_CHANCE);
        spawnSparkle(world, rng, rx, ry + 0.2, rz, COLUMN_PURPLE_CHANCE);
        spawnSparkle(world, rng, rx, ry + 0.4, rz, COLUMN_PURPLE_CHANCE);
    }

    /** One particle at a random point inside the thin vertical cylinder (XZ radius, {@link #COLUMN_HEIGHT} tall). */
    private static void spawnOneUpperColumnParticle(Level world, ThreadLocalRandom rng, int blockMinY, double cx, double cz) {
        double baseY = blockMinY + COLUMN_BOTTOM_OFFSET;
        double py = baseY + rng.nextDouble() * COLUMN_HEIGHT;
        double ang = rng.nextDouble() * Math.PI * 2.0;
        double rr = Math.sqrt(rng.nextDouble()) * COLUMN_RADIUS;
        double px = cx + rr * Math.cos(ang);
        double pz = cz + rr * Math.sin(ang);
        spawnSparkle(world, rng, px, py, pz, COLUMN_MIX_PURPLE_CHANCE);
    }

    private static void spawnSparkle(Level world, ThreadLocalRandom rng, double x, double y, double z, double purpleChance) {
        if (rng.nextDouble() < purpleChance) {
            world.addParticle(PURPLE_MIST, x, y, z, 0.0, 0.015, 0.0);
        } else {
            world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0.0, 0.006, 0.0);
        }
    }

    private static void tickAmbientSound(Level world, long nowNs, int bx, int by, int bz, double ax, double ay, double az) {
        long key = BlockPos.asLong(bx, by, bz);
        long last = LAST_AMBIENT_NS.getOrDefault(key, 0L);
        if (nowNs - last < AMBIENT_INTERVAL_NS) {
            return;
        }
        LAST_AMBIENT_NS.put(key, nowNs);
        float pitch = 0.92f + ThreadLocalRandom.current().nextFloat() * 0.08f;
        world.playLocalSound(ax, ay, az, SoundEvents.CONDUIT_AMBIENT_SHORT, SoundSource.BLOCKS, AMBIENT_VOLUME, pitch, false);
    }
}
