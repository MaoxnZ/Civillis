package civil.registry;

import net.minecraft.server.level.ServerLevel;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Per-dimension toggles for civilization scoring / spawn logic vs mob-head mechanics.
 *
 * <p>Loaded from datapack JSON by {@link DimensionPolicyLoader}.
 * Dimensions not listed use {@link #DEFAULT}.
 */
public final class DimensionPolicyRegistry {

    /** Default: full mod behavior (civilization + heads). */
    public static final DimensionPolicy DEFAULT = new DimensionPolicy(true, true);

    private static Map<String, DimensionPolicy> overrides = Map.of();

    private DimensionPolicyRegistry() {
    }

    /**
     * @param dimensionId {@link ServerLevel#dimension()} as {@link net.minecraft.resources.Identifier#toString()}
     */
    public static DimensionPolicy policyFor(String dimensionId) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        return overrides.getOrDefault(dimensionId, DEFAULT);
    }

    public static DimensionPolicy policyFor(ServerLevel world) {
        return policyFor(world.dimension().identifier().toString());
    }

    public static void reload(Map<String, DimensionPolicy> newOverrides) {
        overrides = Collections.unmodifiableMap(Map.copyOf(newOverrides));
    }

    /**
     * @param civilization when false, civilization score is treated as 0 and zone/score spawn stages are skipped.
     * @param headMechanics when false, SHRINE_NEARBY / SHRINE_SUPPRESS spawn stages are skipped for the dimension.
     */
    public record DimensionPolicy(boolean civilization, boolean headMechanics) {
    }
}
