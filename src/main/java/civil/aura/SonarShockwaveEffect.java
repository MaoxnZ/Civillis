package civil.aura;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;

import java.util.Map;
import java.util.Set;

/**
 * Client-side dramatic particle animation for the Civil Detector sonar pulse.
 *
 * <p>Two independently triggered phases, each synced with its server-side sound:
 * <ol>
 *   <li><b>Charge-up</b> (triggered by {@link SonarChargePayload}): Vertical column of
 *       particles at the player position, intensity pulsing. Particle type depends on
 *       the player's zone: {@code FLAME} in head zones, {@code END_ROD} in HIGH zones,
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
@Environment(EnvType.CLIENT)
public final class SonarShockwaveEffect {

    private SonarShockwaveEffect() {}

    // ========== Phase identifiers ==========
    private static final int PHASE_NONE   = 0;
    private static final int PHASE_CHARGE = 1;
    private static final int PHASE_RING   = 2;

    // ========== Charge-up timing (seconds) ==========
    /** How long the charge-up column plays before naturally expiring. */
    private static final float CHARGE_DURATION = 0.40f;

    // ========== Charge-up parameters ==========
    private static final float CHARGE_VERTICAL_SPREAD = 3.0f;
    private static final int   CHARGE_PARTICLES_PER_TICK = 15;

    // ========== Ring timing (seconds) ==========
    /**
     * Pause before the ring starts expanding (seconds).
     * Matches {@code SonarScanManager.BOOM_DELAY_TICKS} (5 ticks = 0.25s) so the
     * visual ring expansion syncs with the server-side boom sound arrival.
     */
    private static final float RING_PAUSE = 0.25f;
    /** Total ring animation duration (after pause). */
    private static final float RING_EXPAND_DURATION = 1.50f;
    private static final float RING_TOTAL = RING_PAUSE + RING_EXPAND_DURATION;

    // ========== Ring parameters ==========
    private static final float RING_MIN_RADIUS = 2.0f;
    private static final float RING_MAX_RADIUS = 120.0f;
    private static final float RING_EXPAND_SPEED = (RING_MAX_RADIUS - RING_MIN_RADIUS) / RING_EXPAND_DURATION;

    /** Time delay between wave 1 and wave 2 (seconds). */
    private static final float WAVE2_DELAY = 0.08f;

    // ========== Shared state ==========
    private static int phase = PHASE_NONE;
    private static long phaseStartNano = 0;
    private static double cx, cy, cz;

    /** Throttle: minimum nanoseconds between particle spawn ticks (~50ms = 20 Hz). */
    private static final long TICK_INTERVAL_NS = 50_000_000L;
    private static long lastTickNano = 0;

    // ========== Charge-up state ==========
    private static boolean chargePlayerInHigh = true;
    private static boolean chargePlayerInHeadZone = false;

    // ========== Ring state ==========
    /** Whether the player is in the HIGH (safe) zone. */
    @SuppressWarnings("unused")
    private static boolean playerInHigh = true;

    /**
     * Head zone footprint with Y ranges: packed XZ key → float[]{minY, maxY}.
     * Particles landing inside these VCs (with matching Y) are rendered as {@code FLAME} (orange).
     * Y range is the exact VC height [sy*16, (sy+1)*16) — strict same-sy, matching detector sound.
     */
    private static Map<Long, float[]> headZoneYMap = Map.of();

    /**
     * 2D (XZ) footprint of HIGH civilization VCs, packed the same way.
     * Particles landing inside these VCs are rendered as {@code END_ROD} (gold).
     */
    private static Set<Long> civHighZone2D = Set.of();

    // ========== Public API ==========

    /**
     * Start the charge-up phase (vertical particle column).
     * Called when the {@link SonarChargePayload} arrives, synced with the charge-up sound.
     *
     * @param centerX      player X
     * @param centerY      player Y (raised +1.0 internally)
     * @param centerZ      player Z
     * @param inHigh       true if the player is in a HIGH (safe) zone
     * @param inHeadZone   true if the player is in a head zone (Force Allow)
     */
    public static void startCharge(double centerX, double centerY, double centerZ,
                                   boolean inHigh, boolean inHeadZone) {
        cx = centerX;
        cy = centerY + 1.0;
        cz = centerZ;
        chargePlayerInHigh = inHigh;
        chargePlayerInHeadZone = inHeadZone;
        phaseStartNano = System.nanoTime();
        lastTickNano = 0;
        phase = PHASE_CHARGE;
    }

    /**
     * Start the ring expansion phase (shockwave).
     * Called when the {@link SonarBoundaryPayload} arrives. If charge-up is still
     * playing, it transitions immediately to the ring phase.
     *
     * @param inHigh          true if the player is in a HIGH zone
     * @param headZonesYMap   head zone footprint with Y ranges (packed XZ → {minY, maxY})
     * @param civHighZones    2D footprint of HIGH civilization VCs (packed VC coords)
     */
    public static void startRing(boolean inHigh, Map<Long, float[]> headZonesYMap, Set<Long> civHighZones) {
        playerInHigh = inHigh;
        headZoneYMap = headZonesYMap;
        civHighZone2D = civHighZones;
        // Keep cx/cy/cz from charge phase for visual continuity
        phaseStartNano = System.nanoTime();
        lastTickNano = 0;
        phase = PHASE_RING;
    }

    /**
     * Tick the animation. Called every render frame; internally throttled to ~20 Hz.
     */
    public static void tick() {
        if (phase == PHASE_NONE) return;

        long now = System.nanoTime();
        if (now - lastTickNano < TICK_INTERVAL_NS) return;
        lastTickNano = now;

        float elapsed = (now - phaseStartNano) / 1_000_000_000f;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) {
            phase = PHASE_NONE;
            return;
        }

        if (phase == PHASE_CHARGE) {
            if (elapsed > CHARGE_DURATION) {
                // Charge-up expired naturally (boundary packet hasn't arrived yet).
                // Stay idle until ring phase is triggered.
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
            // else: brief pause before ring starts
        }
    }

    public static boolean isActive() {
        return phase != PHASE_NONE;
    }

    // ========== Zone checks ==========

    private static boolean isInHeadZone(double worldX, double worldY, double worldZ) {
        if (headZoneYMap.isEmpty()) return false;
        int vcx = ((int) Math.floor(worldX)) >> 4;
        int vcz = ((int) Math.floor(worldZ)) >> 4;
        long key = ((long) vcx << 32) | (vcz & 0xFFFFFFFFL);
        float[] yRange = headZoneYMap.get(key);
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

    // ========== Phase 1: Charge-up column ==========

    private static void tickChargeUp(ClientWorld world, float elapsed) {
        float t = elapsed / CHARGE_DURATION;
        // Intensity curve: ramp up, sustain, ramp down
        float intensity = (float) Math.sin(Math.PI * t);

        int count = (int) (CHARGE_PARTICLES_PER_TICK * (0.3f + 0.7f * intensity));

        // Use zone info from the charge payload (no BFS data yet)
        var particleType = chargePlayerInHeadZone
                ? ParticleTypes.FLAME
                : chargePlayerInHigh
                        ? ParticleTypes.END_ROD
                        : ParticleTypes.SOUL_FIRE_FLAME;

        for (int i = 0; i < count; i++) {
            double offsetY = (Math.random() * 2.0 - 1.0) * CHARGE_VERTICAL_SPREAD;
            double offsetX = (Math.random() * 2.0 - 1.0) * 0.3;
            double offsetZ = (Math.random() * 2.0 - 1.0) * 0.3;
            double vy = (Math.random() * 0.1 + 0.02) * (offsetY > 0 ? 1 : -1);

            world.addParticleClient(particleType,
                    cx + offsetX, cy + offsetY, cz + offsetZ,
                    0.0, vy, 0.0);
        }
    }

    // ========== Phase 2: Expanding double-ring shockwave ==========

    private static void tickExpandingRings(ClientWorld world, float ringElapsed) {
        // Wave 1 — primary ring
        float radius1 = RING_MIN_RADIUS + ringElapsed * RING_EXPAND_SPEED;
        if (radius1 <= RING_MAX_RADIUS) {
            int count1 = ringParticleCount(radius1, 80.0, 20);
            spawnRing(world, radius1, count1, 0.02f);
        }

        // Wave 2 — trailing ring (starts WAVE2_DELAY later)
        float wave2Elapsed = ringElapsed - WAVE2_DELAY;
        if (wave2Elapsed > 0) {
            float radius2 = RING_MIN_RADIUS + wave2Elapsed * RING_EXPAND_SPEED;
            if (radius2 <= RING_MAX_RADIUS) {
                int count2 = ringParticleCount(radius2, 50.0, 15);
                spawnRing(world, radius2, count2, 0.015f);
            }
        }
    }

    private static int ringParticleCount(float radius, double baseDensity, int minCount) {
        return Math.max(minCount, (int) (baseDensity / (1.0 + radius * 0.015)));
    }

    /**
     * Spawn one ring of evenly-spaced particles at the given radius.
     * Particle type is determined by world position: head zone → FLAME,
     * HIGH civ → END_ROD, else → SOUL_FIRE_FLAME.
     */
    private static void spawnRing(ClientWorld world, float radius, int count, float outwardVelocity) {
        double baseAngle = Math.random() * 2.0 * Math.PI;

        for (int i = 0; i < count; i++) {
            double angle = baseAngle + (2.0 * Math.PI * i / count);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double px = cx + radius * cos;
            double pz = cz + radius * sin;
            double py = cy + (Math.random() - 0.5) * 0.1;

            double vx = cos * outwardVelocity;
            double vz = sin * outwardVelocity;

            var particleType = isInHeadZone(px, py, pz)
                    ? ParticleTypes.FLAME
                    : isInHighZone(px, pz)
                            ? ParticleTypes.END_ROD
                            : ParticleTypes.SOUL_FIRE_FLAME;

            world.addImportantParticleClient(particleType,
                    px, py, pz,
                    vx, 0.0, vz);
        }
    }
}
