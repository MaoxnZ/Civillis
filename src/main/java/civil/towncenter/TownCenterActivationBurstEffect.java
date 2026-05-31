package civil.towncenter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Client: slow END_ROD ring shockwave on first emerald activation (sonar-like, short range).
 */
public final class TownCenterActivationBurstEffect {

    private TownCenterActivationBurstEffect() {
    }

    /** Half a block above {@link TownCenterParticleEffect} ambient ring. */
    private static final double RING_Y_OFFSET = 1.85;

    private static final float EXPAND_DURATION = 1.6f;
    private static final float WAVE2_DELAY = 0.2f;
    /** Initial ring radius (does not spawn from block center). */
    private static final float MIN_RADIUS = 1.25f;
    private static final float MAX_RADIUS = 5.2f;
    private static final float OUTWARD_VELOCITY = 0.014f;

    private static final long TICK_INTERVAL_NS = 90_000_000L;

    private static boolean active;
    private static long startNano;
    private static long lastTickNano;
    private static double cx;
    private static double cy;
    private static double cz;

    public static void start(int blockX, int blockY, int blockZ) {
        cx = blockX + 0.5;
        cy = blockY + RING_Y_OFFSET;
        cz = blockZ + 0.5;
        startNano = System.nanoTime();
        lastTickNano = 0;
        active = true;
    }

    public static void tick() {
        if (!active) {
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            return;
        }

        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            active = false;
            return;
        }

        long now = System.nanoTime();
        if (now - lastTickNano < TICK_INTERVAL_NS) {
            return;
        }
        lastTickNano = now;

        float elapsed = (now - startNano) / 1_000_000_000f;
        float totalDuration = EXPAND_DURATION + WAVE2_DELAY;
        if (elapsed > totalDuration) {
            active = false;
            return;
        }

        float expandSpeed = (MAX_RADIUS - MIN_RADIUS) / EXPAND_DURATION;
        float radius1 = MIN_RADIUS + elapsed * expandSpeed;
        if (radius1 <= MAX_RADIUS) {
            spawnRing(world, radius1, ringCount(radius1));
        }

        float wave2Elapsed = elapsed - WAVE2_DELAY;
        if (wave2Elapsed > 0) {
            float radius2 = MIN_RADIUS + wave2Elapsed * expandSpeed;
            if (radius2 <= MAX_RADIUS) {
                spawnRing(world, radius2, ringCount(radius2) * 2 / 3);
            }
        }
    }

    private static int ringCount(float radius) {
        return Math.max(8, (int) (6 + radius * 2.5f));
    }

    private static void spawnRing(ClientLevel world, float radius, int count) {
        double baseAngle = Math.random() * Math.PI * 2.0;
        for (int i = 0; i < count; i++) {
            double angle = baseAngle + (2.0 * Math.PI * i / count);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double px = cx + radius * cos;
            double pz = cz + radius * sin;
            double py = cy + (Math.random() - 0.5) * 0.12;
            double vx = cos * OUTWARD_VELOCITY;
            double vz = sin * OUTWARD_VELOCITY;
            world.addParticle(ParticleTypes.END_ROD, px, py, pz, vx, 0.006, vz);
        }
    }
}
