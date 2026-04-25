package civil.aura;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import java.util.Map;
import java.util.Set;

/**
 * Client-side dramatic particle animation for the Civil Detector sonar pulse.
 *
 * <p>Two independently triggered phases, each synced with its server-side sound:
 * <ol>
 *   <li><b>Charge-up</b> (triggered by {@link SonarChargePayload}): Vertical column of
 *       particles at the player position, intensity pulsing. Particle type depends on
 *       the player's zone: {@code FLAME} in shrine bypass zones, {@code END_ROD} in HIGH zones,
 *       {@code SOUL_FIRE_FLAME} in LOW/wilderness. Syncs with the charge-up sound
 *       (beacon activate).</li>
 *   <li><b>Expanding double ring</b> (triggered by {@link SonarBoundaryPayload}): Two
 *       tight concentric rings expanding outward like a shockwave. Each particle's type
 *       is determined by its world position using zone data from the payload. Syncs with
 *       the boom sound (breeze shoot).</li>
 * </ol>
 *
 * <p>All particles are spawned client-side, visible only to the detecting player.
 * Ticked from the render callback at ~20 Hz.
 */
public final class SonarShockwaveEffect {

    private SonarShockwaveEffect() {}

    private static final int PHASE_NONE   = 0;
    private static final int PHASE_CHARGE = 1;
    private static final int PHASE_RING   = 2;

    private static final float CHARGE_SAFETY_TIMEOUT = 10.0f;

    private static final float CHARGE_VERTICAL_SPREAD = 3.0f;

    private static final int GOLD_COLOR = 0xFFE4A0;
    private static final int LIGHT_GOLD_COLOR = 0xFFF2D9;
    private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(GOLD_COLOR, 1.2f);
    private static final DustColorTransitionOptions GOLD_TRANSITION =
            new DustColorTransitionOptions(GOLD_COLOR, LIGHT_GOLD_COLOR, 1.4f);

    private static final float RING_PAUSE = 0.25f;
    private static final float RING_EXPAND_DURATION = 1.50f;
    private static final float RING_TOTAL = RING_PAUSE + RING_EXPAND_DURATION;

    private static final float RING_MIN_RADIUS = 2.0f;
    private static final float RING_MAX_RADIUS = 120.0f;
    private static final float RING_EXPAND_SPEED = (RING_MAX_RADIUS - RING_MIN_RADIUS) / RING_EXPAND_DURATION;

    private static final float WAVE2_DELAY = 0.08f;

    private static int phase = PHASE_NONE;
    private static long phaseStartNano = 0;
    private static double cx, cy, cz;

    private static final long TICK_INTERVAL_NS = 50_000_000L;
    private static long lastTickNano = 0;

    private static boolean chargePlayerInHigh = true;
    private static boolean chargePlayerInShrineZone = false;
    private static SonarType activeSonarType = SonarType.DETECTOR;

    @SuppressWarnings("unused")
    private static boolean playerInHigh = true;

    private static Map<Long, float[]> shrineZoneYMap = Map.of();

    private static Set<Long> civHighZone2D = Set.of();

    public static void startCharge(double centerX, double centerY, double centerZ,
                                   boolean inHigh, boolean inShrineZone, SonarType type) {
        cx = centerX;
        cy = centerY + 1.0;
        cz = centerZ;
        chargePlayerInHigh = inHigh;
        chargePlayerInShrineZone = inShrineZone;
        activeSonarType = type;
        phaseStartNano = System.nanoTime();
        lastTickNano = 0;
        phase = PHASE_CHARGE;
    }

    public static void startRing(boolean inHigh, Map<Long, float[]> shrineZonesYMap,
                                 Set<Long> civHighZones, SonarType type) {
        playerInHigh = inHigh;
        shrineZoneYMap = shrineZonesYMap;
        civHighZone2D = civHighZones;
        activeSonarType = type;
        phaseStartNano = System.nanoTime();
        lastTickNano = 0;
        phase = PHASE_RING;
    }

    public static void tick() {
        if (phase == PHASE_NONE) return;
        if (Minecraft.getInstance().isPaused()) return;

        long now = System.nanoTime();
        if (now - lastTickNano < TICK_INTERVAL_NS) return;
        lastTickNano = now;

        float elapsed = (now - phaseStartNano) / 1_000_000_000f;

        Minecraft client = Minecraft.getInstance();
        ClientLevel world = client.level;
        if (world == null) {
            phase = PHASE_NONE;
            return;
        }

        if (phase == PHASE_CHARGE) {
            if (elapsed > CHARGE_SAFETY_TIMEOUT) {
                phase = PHASE_NONE;
                return;
            }
            tickChargeUp(world, elapsed);
        } else if (phase == PHASE_RING) {
            if (elapsed > RING_TOTAL) {
                phase = PHASE_NONE;
                return;
            }
            if (elapsed >= RING_PAUSE) {
                tickExpandingRings(world, elapsed - RING_PAUSE);
            }
        }
    }

    public static boolean isActive() {
        return phase != PHASE_NONE;
    }

    private static boolean isInShrineZone(double worldX, double worldY, double worldZ) {
        if (shrineZoneYMap.isEmpty()) return false;
        int vcx = ((int) Math.floor(worldX)) >> 4;
        int vcz = ((int) Math.floor(worldZ)) >> 4;
        long key = ((long) vcx << 32) | (vcz & 0xFFFFFFFFL);
        float[] yRange = shrineZoneYMap.get(key);
        if (yRange == null) return false;
        return worldY >= yRange[0] && worldY < yRange[1];
    }

    private static boolean isInHighZone(double worldX, double worldZ) {
        if (civHighZone2D.isEmpty()) return false;
        return zoneLookup(civHighZone2D, worldX, worldZ);
    }

    private static boolean zoneLookup(Set<Long> set, double worldX, double worldZ) {
        int vcx = ((int) Math.floor(worldX)) >> 4;
        int vcz = ((int) Math.floor(worldZ)) >> 4;
        return set.contains(((long) vcx << 32) | (vcz & 0xFFFFFFFFL));
    }

    private static void tickChargeUp(ClientLevel world, float elapsed) {
        float intensity = 0.5f + 0.5f * (float) Math.sin(elapsed * Math.PI * 1.3);
        int baseCount = activeSonarType.chargeParticlesPerTick();
        int count = (int) (baseCount * (0.3f + 0.7f * intensity));

        var baseParticle = chargePlayerInShrineZone
                ? ParticleTypes.FLAME
                : chargePlayerInHigh
                        ? ParticleTypes.END_ROD
                        : ParticleTypes.SOUL_FIRE_FLAME;

        boolean goldenAccent = activeSonarType.hasGoldenAccent();

        for (int i = 0; i < count; i++) {
            double offsetY = (Math.random() * 2.0 - 1.0) * CHARGE_VERTICAL_SPREAD;
            double offsetX = (Math.random() * 2.0 - 1.0) * 0.3;
            double offsetZ = (Math.random() * 2.0 - 1.0) * 0.3;
            double vy = (Math.random() * 0.1 + 0.02) * (offsetY > 0 ? 1 : -1);

            if (goldenAccent && i % 3 == 0) {
                var goldParticle = (Math.random() < 0.5) ? GOLD_DUST : GOLD_TRANSITION;
                world.addParticle(goldParticle,
                        cx + offsetX, cy + offsetY, cz + offsetZ,
                        0.0, vy, 0.0);
            } else {
                world.addParticle(baseParticle,
                        cx + offsetX, cy + offsetY, cz + offsetZ,
                        0.0, vy, 0.0);
            }
        }
    }

    private static void tickExpandingRings(ClientLevel world, float ringElapsed) {
        float densityMul = activeSonarType.ringDensityMultiplier();

        float radius1 = RING_MIN_RADIUS + ringElapsed * RING_EXPAND_SPEED;
        if (radius1 <= RING_MAX_RADIUS) {
            int count1 = ringParticleCount(radius1, 80.0 * densityMul, 20);
            spawnRing(world, radius1, count1, 0.02f);
        }

        float wave2Elapsed = ringElapsed - WAVE2_DELAY;
        if (wave2Elapsed > 0) {
            float radius2 = RING_MIN_RADIUS + wave2Elapsed * RING_EXPAND_SPEED;
            if (radius2 <= RING_MAX_RADIUS) {
                int count2 = ringParticleCount(radius2, 50.0 * densityMul, 15);
                spawnRing(world, radius2, count2, 0.015f);
            }
        }
    }

    private static int ringParticleCount(float radius, double baseDensity, int minCount) {
        return Math.max(minCount, (int) (baseDensity / (1.0 + radius * 0.015)));
    }

    private static void spawnRing(ClientLevel world, float radius, int count, float outwardVelocity) {
        double baseAngle = Math.random() * 2.0 * Math.PI;
        boolean goldenAccent = activeSonarType.hasGoldenAccent();

        for (int i = 0; i < count; i++) {
            double angle = baseAngle + (2.0 * Math.PI * i / count);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double px = cx + radius * cos;
            double pz = cz + radius * sin;
            double py = cy + (Math.random() - 0.5) * 0.1;

            double vx = cos * outwardVelocity;
            double vz = sin * outwardVelocity;

            if (isInShrineZone(px, py, pz)) {
                world.addAlwaysVisibleParticle(ParticleTypes.FLAME, px, py, pz, vx, 0.0, vz);
            } else if (isInHighZone(px, pz)) {
                if (goldenAccent && i % 4 == 0) {
                    world.addAlwaysVisibleParticle(GOLD_DUST, px, py, pz, vx, 0.0, vz);
                } else {
                    world.addAlwaysVisibleParticle(ParticleTypes.END_ROD, px, py, pz, vx, 0.0, vz);
                }
            } else {
                world.addAlwaysVisibleParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, vx, 0.0, vz);
            }
        }
    }
}
