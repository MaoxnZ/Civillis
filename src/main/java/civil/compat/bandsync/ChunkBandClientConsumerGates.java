package civil.compat.bandsync;

import civil.config.CivilConfig;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Civil client toggles that gate {@link ChunkBandSubscriptionHub} refcount for world-map integrations.
 * When a toggle is off, {@link ChunkBandSubscriptionHub#acquire} is a noop for that consumer id, and
 * {@link CivilConfig} reload will {@link ChunkBandSubscriptionHub#forceReleaseConsumer} it.
 *
 * <p>Add one {@link Gate} row per consumer that has a dedicated Civil GUI/properties switch. Unknown
 * consumer ids are not gated (acquire always allowed).
 */
public final class ChunkBandClientConsumerGates {

    public record Gate(String consumerId, BooleanSupplier civilClientEnabled) {}

    /**
     * Append here when adding a new world-map consumer (e.g. Xaero after consumer id + config key exist).
     */
    public static final List<Gate> GATED = List.of(
            new Gate(ChunkBandConsumers.JOURNEY_MAP, () -> CivilConfig.chunkBandClientEnabledJourneyMap),
            new Gate(ChunkBandConsumers.XAEROS_MINIMAP, () -> CivilConfig.chunkBandClientEnabledXaeros),
            new Gate(ChunkBandConsumers.XAEROS_WORLDMAP, () -> CivilConfig.chunkBandClientEnabledXaeros));

    private ChunkBandClientConsumerGates() {
    }

    /** When {@code true}, {@link ChunkBandSubscriptionHub#acquire} must not take a ref for this consumer. */
    public static boolean isBlockedFromAcquire(String consumerId) {
        for (Gate g : GATED) {
            if (g.consumerId().equals(consumerId)) {
                return !g.civilClientEnabled().getAsBoolean();
            }
        }
        return false;
    }
}
