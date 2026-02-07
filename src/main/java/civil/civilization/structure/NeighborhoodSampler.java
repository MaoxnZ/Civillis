package civil.civilization.structure;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Neighborhood sampler: given a center point, samples by voxel chunk radius, returns multi-region
 * {@link VoxelChunkRegion}, accessible by (cx, cz, sy) for each {@link VoxelRegion}.
 */
public interface NeighborhoodSampler {

    /**
     * Centered on the voxel chunk containing centerPos, expands by sampler's radiusX/Y/Z, calls
     * {@link #sampleOneVoxelChunk} for each voxel chunk, returns structure composed of multiple VoxelRegions.
     */
    VoxelChunkRegion sample(ServerWorld world, BlockPos centerPos);

    /**
     * Sample a single voxel chunk (16×16×16, or slightly smaller after dimension clamp).
     * If key is invalid in current dimension (Y has no intersection), returns null.
     */
    VoxelRegion sampleOneVoxelChunk(ServerWorld world, VoxelChunkKey key);
}
