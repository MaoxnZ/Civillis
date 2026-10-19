package civil.compat.bandsync;

/**
 * Stable ids for {@link ChunkBandSubscriptionHub} refcount consumers.
 * Client-side GUI toggles that gate acquire / reload are listed in {@link ChunkBandClientConsumerGates}.
 */
public final class ChunkBandConsumers {

    public static final String JOURNEY_MAP = "civil:journey_map";

    /** Xaero HUD minimap chunk-band consumer (C2S subscription refcount; separate from world map). */
    public static final String XAEROS_MINIMAP = "civil:xaeros_minimap";

    /** Xaero fullscreen world map chunk-band consumer. */
    public static final String XAEROS_WORLDMAP = "civil:xaeros_worldmap";

    private ChunkBandConsumers() {
    }
}
