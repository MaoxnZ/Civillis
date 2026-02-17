package civil.civilization;

import civil.registry.BlockWeightRegistry;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SkullBlock;

/**
 * Stateless utility for block-level civilization queries.
 *
 * <p>Delegates weight lookups to {@link BlockWeightRegistry} (data-driven).
 * Provides skull detection helpers used by Mixin hooks and palette pre-filters.
 *
 * <p>Replaces the former {@code BlockCivilization} class (which held hardcoded
 * weights in a static initializer). All weight data now comes from datapack
 * JSON via BlockWeightRegistry.
 */
public final class BlockScanner {

    private BlockScanner() {}

    /**
     * Non-air counts as 1, air counts as 0.
     * Used for corner suspicion and priority curve.
     */
    public static int suspect(BlockState state) {
        return state.isAir() ? 0 : 1;
    }

    /**
     * Single block civilization weight [0, 5], not normalized.
     * Normalization is handled by operators using NORMALIZATION_FACTOR etc.
     *
     * <p>Delegates to {@link BlockWeightRegistry#getWeight(Block)}.
     */
    public static double getBlockWeight(Block block) {
        return BlockWeightRegistry.getWeight(block);
    }

    /**
     * Whether this block state is a non-player monster head.
     * Used by block change mixin to track head placement/removal.
     */
    public static boolean isSkullBlock(BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof AbstractSkullBlock skull)) return false;
        return skull.getSkullType() != SkullBlock.Type.PLAYER;
    }

    /**
     * Whether this block state is a "target" for civilization scanning:
     * positive civilization weight OR a monster head.
     * Used as the predicate for {@code ChunkSection.hasAny()} palette pre-filter.
     */
    public static boolean isTargetBlock(BlockState state) {
        return BlockWeightRegistry.hasWeight(state.getBlock()) || isSkullBlock(state);
    }
}
