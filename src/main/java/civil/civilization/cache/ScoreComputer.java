package civil.civilization.cache;

import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;

/**
 * Score computer: calculates civilization score for a single L1 (VoxelChunk).
 * 
 * <p>Used when L2/L3 cache repairs dirty cells to recalculate scores.
 */
@FunctionalInterface
public interface ScoreComputer {
    /**
     * Calculate civilization score for the specified L1.
     * 
     * @param level Server world
     * @param l1 L1 key (VoxelChunkKey)
     * @return Civilization score
     */
    double compute(ServerWorld level, VoxelChunkKey l1);
}
