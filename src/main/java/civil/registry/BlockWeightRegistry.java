package civil.registry;

import net.minecraft.world.level.block.Block;

import java.util.IdentityHashMap;

/**
 * Data-driven block weight registry.
 *
 * <p>Holds the mapping from {@link Block} to civilization weight.
 * Populated at server startup / {@code /reload} by {@link BlockWeightLoader}
 * from datapack JSON files ({@code data/<namespace>/civil_blocks/*.json}).
 *
 * <p>This class is a <b>pure data holder</b> with no side effects.
 * Thread safety: the internal map reference is {@code volatile} and replaced
 * atomically on reload; reads are lock-free.
 *
 * <p>Runtime cost: identical to the previous hardcoded {@code IdentityHashMap}
 * — O(1) identity-based lookup per call.
 */
public final class BlockWeightRegistry {

    private static volatile IdentityHashMap<Block, Double> weights = new IdentityHashMap<>();

    private BlockWeightRegistry() {}

    /**
     * Get the civilization weight for a block.
     *
     * @return weight in [0, 5], or 0.0 if the block is not registered
     */
    public static double getWeight(Block block) {
        return weights.getOrDefault(block, 0.0);
    }

    /**
     * Whether this block has a positive civilization weight.
     * Used as part of the palette pre-filter predicate.
     */
    public static boolean hasWeight(Block block) {
        return weights.containsKey(block);
    }

    /**
     * Atomically replace the entire weight map.
     * Called by {@link BlockWeightLoader} during datapack reload.
     *
     * @param newWeights fully resolved block→weight map
     */
    public static void reload(IdentityHashMap<Block, Double> newWeights) {
        weights = newWeights;
    }
}
