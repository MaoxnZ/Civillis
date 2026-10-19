package civil.civilization.cache;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.config.CivilConfig;
import civil.civilization.storage.CivilStorage;
import civil.civilization.VoxelChunkKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fusion Architecture: L1-only TTL cache.
 *
 * <p>L2/L3 layers have been retired. This cache stores only L1 info shards
 * (per-VC raw scores). Result shards are managed by {@link ResultCache}.
 *
 * <p>L1 entries are staged for unified NBT flush on write and restored from storage
 * on chunk load or server startup.
 */
public final class TtlVoxelCache implements CivilizationCache {

    private static final Logger CACHE_LOG = LoggerFactory.getLogger("civil-cache");

    private final long ttlMillis;

    // L1 cache: dim|cx|cz|sy -> CScore
    private final ConcurrentHashMap<String, TimestampedEntry<CScore>> l1Cache = new ConcurrentHashMap<>();

    /** Pending L1 writes for unified flush. Key: dim|cx|cz|sy, value: CScore. */
    private final ConcurrentHashMap<String, CScore> pendingScoreWrites = new ConcurrentHashMap<>();
    /** Keys to clear (score written as 0) on flush when L1 score is ≤ 0. */
    private final ConcurrentHashMap<String, Boolean> pendingScoreDeleteKeys = new ConcurrentHashMap<>();

    // Persistent storage
    private CivilStorage storage;

    // Loading state tracker
    private final LoadingStateTracker loadingTracker = new LoadingStateTracker();

    public TtlVoxelCache() {
        this(CivilConfig.l1TtlMs);
    }

    public TtlVoxelCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public void setStorage(CivilStorage storage) {
        this.storage = storage;
    }

    public LoadingStateTracker getLoadingTracker() {
        return loadingTracker;
    }

    // ========== Cache key generation ==========

    private static String l1Key(ServerLevel level, VoxelChunkKey key) {
        return level.dimension().identifier().toString() + "|" + key.getCx() + "|" + key.getCz() + "|" + key.getSy();
    }

    private static String getDim(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    // ========== L1 operations ==========

    @Override
    public Optional<CScore> getChunkCScore(ServerLevel level, VoxelChunkKey key) {
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
    public void putChunkCScore(ServerLevel level, VoxelChunkKey key, CScore cScore) {
        String k = l1Key(level, key);
        l1Cache.put(k, new TimestampedEntry<>(cScore));
        if (cScore.score() <= 0.0) {
            pendingScoreWrites.remove(k);
            pendingScoreDeleteKeys.put(k, Boolean.TRUE);
        } else {
            pendingScoreDeleteKeys.remove(k);
            pendingScoreWrites.put(k, cScore);
        }

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache] L1 PUT dim={} cx={} cz={} sy={} score={}",
                    getDim(level), key.getCx(), key.getCz(), key.getSy(), cScore.score());
        }
    }

    // invalidateChunk / markChunkDirtyAt removed — Fusion Architecture uses
    // immediate L1 recompute + delta propagation via onCivilBlockChanged().

    /**
     * Check if L1 cache contains the specified key (not expired).
     */
    public boolean containsL1(ServerLevel level, VoxelChunkKey key) {
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
    public void touchL1(ServerLevel level, VoxelChunkKey key) {
        String k = l1Key(level, key);
        TimestampedEntry<CScore> entry = l1Cache.get(k);
        if (entry != null && !entry.isExpired(ttlMillis)) {
            entry.touch();
        }
    }

    /**
     * Restore L1 entry (from disk snapshot or bulk region load).
     *
     * <p>Two guards apply in order:
     * <ol>
     *   <li>TTL: skip if the stored {@code createTime} is older than the TTL window.
     *       Relevant for the {@code initialize} path where the real write timestamp is used.</li>
     *   <li>Hot-cache: skip if a live (non-expired) entry already exists.
     *       Hot-cache entries reflect more recent writes (always mirrored into
     *       {@code pendingScoreWrites}); NBT data is older by definition.</li>
     * </ol>
     */
    public void restoreL1(ServerLevel level, VoxelChunkKey key, CScore cScore, long createTime) {
        String k = l1Key(level, key);
        if (System.currentTimeMillis() - createTime > ttlMillis) {
            return;
        }
        TimestampedEntry<CScore> existing = l1Cache.get(k);
        if (existing != null && !existing.isExpired(ttlMillis)) {
            return;
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

    /**
     * Drain pending L1 writes for unified flush. Returns map (key=dim|cx|cz|sy -> CScore) and clears.
     */
    public Map<String, CScore> drainPendingScoreWrites() {
        Map<String, CScore> snapshot = new HashMap<>(pendingScoreWrites);
        pendingScoreWrites.clear();
        return snapshot;
    }

    public List<String> drainPendingScoreDeleteKeys() {
        List<String> snapshot = new ArrayList<>(pendingScoreDeleteKeys.keySet());
        pendingScoreDeleteKeys.clear();
        return snapshot;
    }

    /**
     * Clear all L1 entries. Called on world shutdown to prevent cross-world cache contamination.
     * Cache keys (dim|cx|cz|sy) do not include world identifier, so switching worlds would
     * return stale scores from the previous world.
     */
    public void clearAll() {
        l1Cache.clear();
        pendingScoreWrites.clear();
        pendingScoreDeleteKeys.clear();
    }
}
