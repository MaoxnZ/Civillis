package civil.civilization.structure;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Multi-voxel chunk region from a single sampling: uses (cx, cz, sy) or {@link VoxelChunkKey} as key,
 * each key corresponds to one {@link VoxelRegion} (single 16³ block or slightly smaller after dimension clamp).
 *
 * <p>Returned by {@link NeighborhoodSampler#sample}, for CivilizationService
 * to call operators on each chunk and aggregate, achieving decoupling between sampler and service.
 */
public final class VoxelChunkRegion {

    private final Map<VoxelChunkKey, VoxelRegion> map = new HashMap<>();

    /** Only for internal filling by sampler. */
    void put(VoxelChunkKey key, VoxelRegion region) {
        map.put(key, region);
    }

    public VoxelRegion get(VoxelChunkKey key) {
        return map.get(key);
    }

    public VoxelRegion get(int cx, int cz, int sy) {
        return map.get(new VoxelChunkKey(cx, cz, sy));
    }

    public int size() {
        return map.size();
    }

    public void forEach(BiConsumer<VoxelChunkKey, VoxelRegion> action) {
        map.forEach(action);
    }

    /** Read-only view for iterating all key-region pairs. */
    public Iterable<Map.Entry<VoxelChunkKey, VoxelRegion>> entries() {
        return Collections.unmodifiableMap(map).entrySet();
    }
}
