package civil.respawn;

/**
 * Constants for the civil save cinematic timing.
 * Flow: Phase 0 (0.5s, FOV+tint, beacon at tick 2) → black 1s + teleport → totem reveal.
 */
public final class UndyingAnchorCinematic {

    /** Phase 0: pre-teleport (FOV, tint). 0.5s. Beacon plays at tick 2. Server teleports at end. */
    public static final int PHASE0_TICKS = 10;

    /** Tick within Phase 0 to play beacon deactivate (2nd tick). */
    public static final int PHASE0_BEACON_TICK = 2;

    /** Black screen duration, seconds. Teleport happens when black ends. */
    public static final float BLACK_DURATION_SEC = 2.0f;

    /** Ticks until teleport (end of phase 0 + black). */
    public static final int TELEPORT_AFTER_TICKS = PHASE0_TICKS + (int)(BLACK_DURATION_SEC * 20);

    /** Totem reveal fade duration, seconds. */
    public static final float REVEAL_DURATION_SEC = 1.0f;

    /** Total cinematic ticks (for server immobilization). */
    public static final int TOTAL_CINEMATIC_TICKS =
            PHASE0_TICKS + (int)(BLACK_DURATION_SEC * 20) + (int)(REVEAL_DURATION_SEC * 20);

    private UndyingAnchorCinematic() {}
}
