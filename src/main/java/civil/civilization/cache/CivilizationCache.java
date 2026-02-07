package civil.civilization.cache;

import civil.civilization.CScore;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Voxel chunk-level civilization cache: reads/writes {@link CScore} (score + head types) for chunks by {@link VoxelChunkKey}.
 */
public interface CivilizationCache {

    /** Query cached CScore for this voxel chunk. */
    Optional<CScore> getChunkCScore(ServerWorld level, VoxelChunkKey key);

    /** Write CScore for this voxel chunk. */
    void putChunkCScore(ServerWorld level, VoxelChunkKey key, CScore cScore);

    /** Invalidate cache for this voxel chunk. */
    void invalidateChunk(ServerWorld level, VoxelChunkKey key);

    /** Invalidate the voxel chunk containing pos. */
    default void invalidateChunkAt(ServerWorld level, BlockPos pos) {
        invalidateChunk(level, VoxelChunkKey.from(pos));
    }

    /** Mark the voxel chunk containing pos as dirty; debounced implementations can defer invalidation. */
    default void markChunkDirtyAt(ServerWorld level, BlockPos pos) {
        invalidateChunkAt(level, pos);
    }
}
