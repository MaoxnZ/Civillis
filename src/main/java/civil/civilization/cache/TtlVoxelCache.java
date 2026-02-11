package civil.civilization.cache;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.config.CivilConfig;
import civil.civilization.storage.H2Storage;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fusion Architecture: L1-only TTL cache.
 *
 * <p>L2/L3 layers have been retired. This cache stores only L1 info shards
 * (per-VC raw scores). Result shards are managed by {@link ResultCache}.
 *
 * <p>L1 entries are persisted to H2 on write and restored from H2 on chunk load
 * or server startup.
 */
public final class TtlVoxelCache implements CivilizationCache {

    private static final Logger CACHE_LOG = LoggerFactory.getLogger("civil-cache");

    private final long ttlMillis;

    // L1 cache: VoxelChunk -> CScore
    private final ConcurrentHashMap<String, TimestampedEntry<CScore>> l1Cache = new ConcurrentHashMap<>();

    // Persistent storage
    private H2Storage storage;

    // Loading state tracker
    private final LoadingStateTracker loadingTracker = new LoadingStateTracker();

    public TtlVoxelCache() {
        this(CivilConfig.l1TtlMs);
    }

    public TtlVoxelCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public void setStorage(H2Storage storage) {
        this.storage = storage;
    }

    public LoadingStateTracker getLoadingTracker() {
        return loadingTracker;
    }

    // ========== Cache key generation ==========

    private static String l1Key(ServerWorld level, VoxelChunkKey key) {
        return level.getRegistryKey().toString() + "|" + key.getCx() + "|" + key.getCz() + "|" + key.getSy();
    }

    private static String getDim(ServerWorld level) {
        return level.getRegistryKey().toString();
    }

    // ========== L1 operations ==========

    @Override
    public Optional<CScore> getChunkCScore(ServerWorld level, VoxelChunkKey key) {
        String k = l1Key(level, key);
        TimestampedEntry<CScore> entry = l1Cache.get(k);

        if (entry != null) {
            if (entry.isExpired(ttlMillis)) {
                l1Cache.remove(k);
                return Optional.empty();
            }
            entry.touch();
            return Optional.of(entry.getValue());
        }

        return Optional.empty();
    }

    @Override
    public void putChunkCScore(ServerWorld level, VoxelChunkKey key, CScore cScore) {
        String k = l1Key(level, key);
        l1Cache.put(k, new TimestampedEntry<>(cScore));

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache] L1 PUT dim={} cx={} cz={} sy={} score={}",
                    getDim(level), key.getCx(), key.getCz(), key.getSy(), cScore.score());
        }

        // Fusion Architecture: persist L1 to H2 for cold recovery
        if (storage != null) {
            String dim = getDim(level);
            storage.saveL1Async(dim, key, cScore);
        }
    }

    // invalidateChunk / markChunkDirtyAt removed — Fusion Architecture uses
    // immediate L1 recompute + delta propagation via onCivilBlockChanged().

    /**
     * Check if L1 cache contains the specified key (not expired).
     */
    public boolean containsL1(ServerWorld level, VoxelChunkKey key) {
        String k = l1Key(level, key);
        TimestampedEntry<CScore> entry = l1Cache.get(k);
        if (entry == null) return false;
        if (entry.isExpired(ttlMillis)) {
            l1Cache.remove(k);
            return false;
        }
        return true;
    }

    /**
     * Touch an L1 entry to refresh its TTL timer (prevent eviction near players).
     * Called by PlayerAwarePrefetcher once per second for entries near online players.
     * No-op if the entry does not exist or is already expired.
     */
    public void touchL1(ServerWorld level, VoxelChunkKey key) {
        String k = l1Key(level, key);
        TimestampedEntry<CScore> entry = l1Cache.get(k);
        if (entry != null && !entry.isExpired(ttlMillis)) {
            entry.touch();
        }
    }

    /**
     * Restore L1 entry (from H2 cold storage or chunk load palette scan).
     */
    public void restoreL1(ServerWorld level, VoxelChunkKey key, CScore cScore, long createTime) {
        String k = l1Key(level, key);
        if (System.currentTimeMillis() - createTime > ttlMillis) {
            return; // Expired, do not restore
        }
        l1Cache.put(k, new TimestampedEntry<>(cScore, createTime));
    }

    // ========== Maintenance ==========

    /**
     * Clean up expired L1 entries.
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();

        var l1Iterator = l1Cache.entrySet().iterator();
        while (l1Iterator.hasNext()) {
            var entry = l1Iterator.next();
            if (now - entry.getValue().getCreateTime() > ttlMillis) {
                l1Iterator.remove();
            }
        }
    }

    // ========== Statistics ==========

    public int l1Size() {
        return l1Cache.size();
    }

    public long getTtlMillis() {
        return ttlMillis;
    }
}
