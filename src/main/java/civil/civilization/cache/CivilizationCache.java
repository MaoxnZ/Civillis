package civil.civilization.cache;

import civil.civilization.CScore;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;

import java.util.Optional;

/**
 * Voxel chunk-level civilization cache: reads/writes {@link CScore} for chunks by {@link VoxelChunkKey}.
 */
public interface CivilizationCache {

    /** Query cached CScore for this voxel chunk. */
    Optional<CScore> getChunkCScore(ServerWorld level, VoxelChunkKey key);

    /** Write CScore for this voxel chunk. */
    void putChunkCScore(ServerWorld level, VoxelChunkKey key, CScore cScore);
}
