package civil.civilization.cache.simple;

import civil.civilization.CScore;
import civil.civilization.cache.CivilizationCache;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Pyramid-style multi-layer cache: integrates L1, L2, L3 cache layers.
 * 
 * <p>Responsibilities:
 * <ul>
 *   <li>Cascade update L2/L3 when L1 put</li>
 *   <li>Independently mark L1/L2/L3 as dirty when world blocks change</li>
 *   <li>Provide access interface for L2/L3 caches</li>
 * </ul>
 * 
 * <p>Architecture:
 * <pre>
 *   ┌─────────────────────────────────────────────┐
 *   │             LruPyramidCache                 │
 *   │  ┌─────────┐  ┌─────────┐  ┌─────────┐      │
 *   │  │   L1    │  │   L2    │  │   L3    │      │
 *   │  │  1×1×1  │  │  3×3×1  │  │  9×9×3  │      │
 *   │  │  4096   │  │  2048   │  │   512   │      │
 *   │  └─────────┘  └─────────┘  └─────────┘      │
 *   └─────────────────────────────────────────────┘
 * </pre>
 */
public final class LruPyramidCache implements CivilizationCache {

    private final LruL1Cache l1Cache;
    private final LruL2Cache l2Cache;
    private final LruL3Cache l3Cache;

    public LruPyramidCache() {
        this(4096, 2048, 512);
    }

    public LruPyramidCache(int l1Size, int l2Size, int l3Size) {
        this.l1Cache = new LruL1Cache(l1Size);
        this.l2Cache = new LruL2Cache(l2Size);
        this.l3Cache = new LruL3Cache(l3Size);

        // Set cross-references
        this.l2Cache.setL3Cache(l3Cache);
        this.l3Cache.setL2Cache(l2Cache);
    }

    // ========== L1 Operations (implements CivilizationCache interface) ==========

    @Override
    public Optional<CScore> getChunkCScore(ServerWorld level, VoxelChunkKey key) {
        return l1Cache.getChunkCScore(level, key);
    }

    @Override
    public void putChunkCScore(ServerWorld level, VoxelChunkKey key, CScore cScore) {
        // 1. Write to L1
        l1Cache.putChunkCScore(level, key, cScore);

        // 2. Cascade update L2/L3
        double score = cScore.score();
        l2Cache.updateCell(level, key, score);
        l3Cache.updateCell(level, key, score);
    }

    @Override
    public void invalidateChunk(ServerWorld level, VoxelChunkKey key) {
        // L1 directly delete
        l1Cache.invalidateChunk(level, key);

        // L2/L3 mark as dirty (do not delete)
        l2Cache.markCellDirty(level, key);
        l3Cache.markCellDirty(level, key);
    }

    @Override
    public void markChunkDirtyAt(ServerWorld level, BlockPos pos) {
        VoxelChunkKey key = VoxelChunkKey.from(pos);

        // L1 directly delete (L1 has no dirty state, only invalidate)
        l1Cache.invalidateChunk(level, key);

        // L2/L3 mark as dirty
        l2Cache.markCellDirty(level, key);
        l3Cache.markCellDirty(level, key);
    }

    // ========== L2/L3 Access Interface ==========

    /**
     * Get L2 cache.
     */
    public LruL2Cache getL2Cache() {
        return l2Cache;
    }

    /**
     * Get L3 cache.
     */
    public LruL3Cache getL3Cache() {
        return l3Cache;
    }

    /**
     * Get L1 cache (original LRU cache).
     */
    public LruL1Cache getL1Cache() {
        return l1Cache;
    }

    // ========== Debug Information ==========

    public int l1Size() {
        return l1Cache.size();
    }

    public int l2Size() {
        return l2Cache.size();
    }

    public int l3Size() {
        return l3Cache.size();
    }
}
