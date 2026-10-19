package civil.respawn;

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

/**
 * Client-side particle effects and ambient sound for undying anchors.
 *
 * <p>CHARGE tier: mini vertical pillar + enchant-like particles (emerald block glow).
 * READY tier: loading-style spinning arc — arc length pulses 0→60°, gap rotates slowly,
 * above block. Sparse spawn to avoid cloud (MC particles linger).
 *
 * <p>Ambient sound: RESPAWN_ANCHOR_AMBIENT plays periodically when in range (looping effect).
 * Ticked from render callback; receives state from {@link UndyingAnchorParticlePayload}.
 */
public final class UndyingAnchorParticleEffect {

    private UndyingAnchorParticleEffect() {}

    private static final double MAX_RENDER_DIST = 32.0;
    private static final double MAX_RENDER_DIST_SQ = MAX_RENDER_DIST * MAX_RENDER_DIST;

    /** Mini charge pillar: much smaller than sonar (0.6 blocks tall). */
    private static final float CHARGE_VERTICAL_SPREAD = 0.6f;
    private static final int CHARGE_PARTICLES_PER_TICK = 3;
    private static final int ENCHANT_PARTICLES_PER_TICK = 2;

    /** Loading-style spinning arc: arc length 0→60° only, gap rotates slowly — avoids cloud. */
    private static final float READY_ARC_HEIGHT_ABOVE = 1.2f;
    private static final float READY_ARC_RADIUS = 0.55f;
    /** More particles for finer arc; 60° arc keeps density reasonable. */
    private static final int READY_ARC_PARTICLES = 10;
    private static final float GAP_ROTATION_RADS_PER_SEC = (float) (Math.PI * 0.5);  // Slow gap rotation
    private static final float ARC_LENGTH_PULSE_PER_SEC = (float) (Math.PI * 0.6);  // Arc 0→60° cycle

    private static final List<AnchorState> ACTIVE_ANCHORS = new CopyOnWriteArrayList<>();
    private static long lastTickNano = 0;
    /** ~20 Hz update for smooth arc motion; 60° arc prevents cloud buildup. */
    private static final long TICK_INTERVAL_NS = 50_000_000L;  // 50ms

    /** Ambient sound: RESPAWN_ANCHOR_AMBIENT (soothing hum), subtle background loop. */
    private static final float AMBIENT_VOLUME = 0.5f;
    private static final long AMBIENT_INTERVAL_NS = 4_000_000_000L;  // 4 seconds
    private static final Map<Long, Long> LAST_AMBIENT_NS = new ConcurrentHashMap<>();

    private record AnchorState(int x, int y, int z, byte tier) {}

    /**
     * Update active anchors from server payload. Replaces previous state.
     */
    public static void updateFromPayload(UndyingAnchorParticlePayload payload) {
        List<AnchorState> next = new ArrayList<>(payload.entries().size());
        for (var e : payload.entries()) {
            next.add(new AnchorState(e.x(), e.y(), e.z(), e.tier()));
        }
        ACTIVE_ANCHORS.clear();
        ACTIVE_ANCHORS.addAll(next);
        LAST_AMBIENT_NS.clear();  // avoid stale cooldown entries when anchors change
    }

    /**
     * Tick particle spawning. Call from render callback.
     */
    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.isPaused()) return;  // Level doesn't tick when paused; particles don't age → accumulation
        if (client.level == null || client.player == null) return;

        long now = System.nanoTime();
        if (now - lastTickNano < TICK_INTERVAL_NS) return;
        lastTickNano = now;

        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        float totalSec = now / 1_000_000_000f;

        for (AnchorState a : ACTIVE_ANCHORS) {
            double ax = a.x() + 0.5;
            double ay = a.y() + 0.5;
            double az = a.z() + 0.5;
            double dx = ax - px;
            double dy = ay - py;
            double dz = az - pz;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DIST_SQ) continue;

            Level world = client.level;
            if (a.tier() == UndyingAnchorParticlePayload.Entry.TIER_CHARGE) {
                tickChargeParticles(world, ax, ay, az, totalSec);
            } else {
                tickReadyParticles(world, ax, ay, az, totalSec);
            }
            tickAmbientSound(world, now, a.x(), a.y(), a.z(), ax, ay, az);
        }
    }

    private static void tickAmbientSound(Level world, long nowNs, int bx, int by, int bz, double ax, double ay, double az) {
        long key = BlockPos.asLong(bx, by, bz);
        long last = LAST_AMBIENT_NS.getOrDefault(key, 0L);
        if (nowNs - last < AMBIENT_INTERVAL_NS) return;
        LAST_AMBIENT_NS.put(key, nowNs);
        world.playLocalSound(ax, ay, az, SoundEvents.RESPAWN_ANCHOR_AMBIENT, SoundSource.BLOCKS, AMBIENT_VOLUME, 1.0f, false);
    }

    private static void tickChargeParticles(Level world, double cx, double cy, double cz, float totalSec) {
        double baseY = cy + 1.0 + CHARGE_VERTICAL_SPREAD * 0.5;  // Above block surface
        float intensity = 0.5f + 0.5f * (float) Math.sin(totalSec * Math.PI * 1.3);
        int count = (int) (CHARGE_PARTICLES_PER_TICK * (0.4f + 0.6f * intensity));

        for (int i = 0; i < count; i++) {
            double offsetY = (Math.random() * 2.0 - 1.0) * CHARGE_VERTICAL_SPREAD * 0.5;
            double offsetX = (Math.random() - 0.5) * 0.2;
            double offsetZ = (Math.random() - 0.5) * 0.2;
            double vy = (Math.random() * 0.05 + 0.01) * (offsetY > 0 ? 1 : -1);
            world.addParticle(ParticleTypes.END_ROD,
                    cx + offsetX, baseY + offsetY, cz + offsetZ,
                    0.0, vy, 0.0);
        }

        for (int i = 0; i < ENCHANT_PARTICLES_PER_TICK; i++) {
            double offsetX = (Math.random() - 0.5) * 1.2;
            double offsetY = Math.random() * 0.4;
            double offsetZ = (Math.random() - 0.5) * 1.2;
            world.addParticle(ParticleTypes.ENCHANT,
                    cx + offsetX, baseY + offsetY, cz + offsetZ,
                    (Math.random() - 0.5) * 0.02, 0.02, (Math.random() - 0.5) * 0.02);
        }
    }

    private static void tickReadyParticles(Level world, double cx, double cy, double cz, float totalSec) {
        // Loading-style arc: arc length 0→60° only, gap rotates slowly. Short arc avoids cloud.
        float baseAngle = totalSec * GAP_ROTATION_RADS_PER_SEC;
        float pulse = (float) Math.sin(totalSec * ARC_LENGTH_PULSE_PER_SEC);
        float arcLength = (float) (Math.PI / 3.0) * (0.5f + 0.5f * pulse);  // 0 to 60°

        if (arcLength < 0.05f) return;  // Skip when nearly empty

        double centerY = cy + READY_ARC_HEIGHT_ABOVE;

        for (int i = 0; i < READY_ARC_PARTICLES; i++) {
            float t = (float) i / (READY_ARC_PARTICLES - 1);
            double a = baseAngle + t * arcLength;
            double px = cx + READY_ARC_RADIUS * Math.cos(a);
            double pz = cz + READY_ARC_RADIUS * Math.sin(a);
            double py = centerY + (Math.random() - 0.5) * 0.06;
            world.addParticle(ParticleTypes.END_ROD, px, py, pz, 0, 0.005, 0);
        }

        var entityEffect = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.33f, 1f, 0.5f);
        for (int i = 0; i < 2; i++) {
            double offsetX = (Math.random() - 0.5) * 0.5;
            double offsetZ = (Math.random() - 0.5) * 0.5;
            double px = cx + offsetX;
            double pz = cz + offsetZ;
            double py = centerY + (Math.random() - 0.5) * 0.3;
            double vx = (Math.random() - 0.5) * 0.02;
            double vy = Math.random() * 0.03 + 0.01;
            double vz = (Math.random() - 0.5) * 0.02;
            if (Math.random() < 0.5) {
                world.addParticle(ParticleTypes.TOTEM_OF_UNDYING, px, py, pz, vx, vy, vz);
            } else {
                world.addParticle(entityEffect, px, py, pz, vx, vy, vz);
            }
        }
    }
}
