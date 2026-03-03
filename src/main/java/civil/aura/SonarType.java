package civil.aura;

import civil.config.CivilConfig;

/**
 * Distinguishes different sonar visualization types.
 *
 * <p>Each enum value is self-describing: it carries its own scan radius,
 * sound parameters, particle settings, and enable/disable logic via methods.
 * Adding a new sonar type (e.g. BEACON) only requires adding an enum value,
 * one {@code case} per switch, and corresponding CivilConfig fields — all
 * consumer code (SonarScanManager, SonarShockwaveEffect, etc.) adapts
 * automatically through these methods.
 */
public enum SonarType {

    /** Portable detector item sonar — lighter, shorter range, quieter. */
    DETECTOR((byte) 0),

    /** Bell-over-lodestone sonar — prestigious, longer range, louder, golden accents. */
    STATIC((byte) 1);

    private final byte id;

    SonarType(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }

    public static SonarType fromId(byte id) {
        for (SonarType t : values()) {
            if (t.id == id) return t;
        }
        return DETECTOR;
    }

    // ═══════════════════════════════════════════
    //  Scan radius (independent from detectionRadius)
    // ═══════════════════════════════════════════

    /** BFS scan radius in voxel chunk units. */
    public int getRadius() {
        return switch (this) {
            case DETECTOR -> CivilConfig.sonarDetectorRadius;
            case STATIC   -> CivilConfig.sonarStaticRadius;
        };
    }

    // ═══════════════════════════════════════════
    //  Enable / disable
    // ═══════════════════════════════════════════

    /**
     * Whether this sonar type is enabled.
     * DETECTOR has a user-facing toggle; STATIC is always on
     * (controlled only by the hidden master {@code auraEffectEnabled}).
     */
    public boolean isEnabled() {
        return switch (this) {
            case DETECTOR -> CivilConfig.detectorSonarEnabled;
            case STATIC   -> true;
        };
    }

    // ═══════════════════════════════════════════
    //  Sound parameters
    // ═══════════════════════════════════════════

    public float chargeVolume() {
        return switch (this) {
            case DETECTOR -> 0.6f;
            case STATIC   -> 1.0f;
        };
    }

    public float chargePitch() {
        return switch (this) {
            case DETECTOR -> 0.85f;
            case STATIC   -> 0.55f;
        };
    }

    public float boomVolume() {
        return switch (this) {
            case DETECTOR -> 0.8f;
            case STATIC   -> 1.2f;
        };
    }

    public float boomPitch() {
        return switch (this) {
            case DETECTOR -> 1.1f;
            case STATIC   -> 0.8f;
        };
    }

    // ═══════════════════════════════════════════
    //  Particle parameters
    // ═══════════════════════════════════════════

    /** Charge-up particle count per tick. */
    public int chargeParticlesPerTick() {
        return switch (this) {
            case DETECTOR -> 15;
            case STATIC   -> 20;
        };
    }

    /** Ring particle density multiplier. */
    public float ringDensityMultiplier() {
        return switch (this) {
            case DETECTOR -> 0.8f;
            case STATIC   -> 1.2f;
        };
    }

    /** Whether to mix golden accent particles (DustColorTransition / Dust). */
    public boolean hasGoldenAccent() {
        return switch (this) {
            case DETECTOR -> false;
            case STATIC   -> true;
        };
    }

    // ═══════════════════════════════════════════
    //  Wall timing
    // ═══════════════════════════════════════════

    /** How long the aura wall stays at full brightness (seconds). */
    public float wallSteadyDurationSeconds() {
        return switch (this) {
            case DETECTOR -> 1.5f;
            case STATIC   -> 2.5f;
        };
    }
}
