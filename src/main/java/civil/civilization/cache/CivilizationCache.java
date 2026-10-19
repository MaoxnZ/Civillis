package civil.civilization.cache;

import civil.civilization.CScore;
import civil.civilization.VoxelChunkKey;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Voxel chunk-level civilization cache: reads/writes {@link CScore} for chunks by {@link VoxelChunkKey}.
 */
public interface CivilizationCache {

    /** Query cached CScore for this voxel chunk. */
    Optional<CScore> getChunkCScore(ServerLevel level, VoxelChunkKey key);

    /** Write CScore for this voxel chunk. */
    void putChunkCScore(ServerLevel level, VoxelChunkKey key, CScore cScore);
}
