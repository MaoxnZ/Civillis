package civil.civilization;

/**
 * Shared civil region priority for map tint, sonar, HUD, and detector.
 *
 * <p>Priority when classifying: {@link #SHRINE} &gt; {@link #ZONE} &gt; {@link #HIGH} &gt; {@link #NONE}.
 * Stable id for network payloads; keep ids stable when extending.
 */
public enum CivilRegionKind {
    /** Not in a highlighted civil region. */
    NONE(0),
    /**
     * High CScore (≥ {@link civil.config.CivilConfig#spawnThresholdMid}).
     */
    HIGH(1),
    /**
     * Structure zone policy treats VC as non-civilized (caution).
     */
    ZONE(2),
    /**
     * Player inside an activated farm shrine bypass volume.
     */
    SHRINE(3);

    private final byte id;

    CivilRegionKind(int id) {
        this.id = (byte) id;
    }

    public byte id() {
        return id;
    }

    public static CivilRegionKind fromId(int id) {
        return switch (id) {
            case 0 -> NONE;
            case 1 -> HIGH;
            case 2 -> ZONE;
            case 3 -> SHRINE;
            default -> NONE;
        };
    }
}
