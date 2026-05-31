package civil.civilization;

import civil.CivilServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Public API for modpack authors to register extra baseScore regions.
 */
public final class BaseScoreApi {

    private BaseScoreApi() {}

    public static void add(ServerLevel level, BlockPos min, BlockPos max, double value, String sourceKey) {
        BaseScoreSourceRegistry registry = requireRegistry();
        if (registry == null) return;
        String dim = level.dimension().identifier().toString();
        VoxelChunkKey[] bounds = orderedVcBounds(min, max);
        registry.add(new BaseScoreSourceRegistry.SourceEntry(
                sourceKey, dim, bounds[0], bounds[1], value, BaseScoreSourceRegistry.TYPE_API));
    }

    public static void remove(String sourceKey) {
        BaseScoreSourceRegistry registry = requireRegistry();
        if (registry != null) {
            registry.remove(sourceKey);
        }
    }

    public static void update(String sourceKey, ServerLevel level, BlockPos newMin, BlockPos newMax, Double newValue) {
        BaseScoreSourceRegistry registry = requireRegistry();
        if (registry == null) return;
        String dim = level != null ? level.dimension().identifier().toString() : null;
        VoxelChunkKey minVc = null;
        VoxelChunkKey maxVc = null;
        if (newMin != null && newMax != null) {
            VoxelChunkKey[] bounds = orderedVcBounds(newMin, newMax);
            minVc = bounds[0];
            maxVc = bounds[1];
        } else if (newMin != null) {
            minVc = VoxelChunkKey.from(newMin);
        } else if (newMax != null) {
            maxVc = VoxelChunkKey.from(newMax);
        }
        registry.update(sourceKey, dim, minVc, maxVc, newValue);
    }

    public static double query(ServerLevel level, BlockPos worldPos) {
        BaseScoreSourceRegistry registry = requireRegistry();
        if (registry == null) return 0.0;
        String dim = level.dimension().identifier().toString();
        return registry.queryBaseScore(dim, VoxelChunkKey.from(worldPos));
    }

    private static BaseScoreSourceRegistry requireRegistry() {
        return CivilServices.getBaseScoreSourceRegistry();
    }

    private static VoxelChunkKey[] orderedVcBounds(BlockPos min, BlockPos max) {
        VoxelChunkKey a = VoxelChunkKey.from(min);
        VoxelChunkKey b = VoxelChunkKey.from(max);
        int minCx = Math.min(a.getCx(), b.getCx());
        int maxCx = Math.max(a.getCx(), b.getCx());
        int minCz = Math.min(a.getCz(), b.getCz());
        int maxCz = Math.max(a.getCz(), b.getCz());
        int minSy = Math.min(a.getSy(), b.getSy());
        int maxSy = Math.max(a.getSy(), b.getSy());
        return new VoxelChunkKey[] {
                new VoxelChunkKey(minCx, minCz, minSy),
                new VoxelChunkKey(maxCx, maxCz, maxSy)
        };
    }
}
