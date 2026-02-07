package civil.civilization.cache.scalable;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.civilization.cache.CivilizationCache;
import civil.civilization.cache.ScoreComputer;
import civil.civilization.storage.H2Storage;
import civil.civilization.structure.L2Entry;
import civil.civilization.structure.L2Key;
import civil.civilization.structure.L3Entry;
import civil.civilization.structure.L3Key;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TTL-evicting civilization cache: supports TTL auto-eviction and async persistence.
 * 
 * <p>Core features:
 * <ul>
 *   <li>TTL eviction: entries automatically expire after a specified time (civilization decay)</li>
 *   <li>No capacity limit: grows on demand, size controlled by TTL</li>
 *   <li>Async persistence: reads/writes via H2 database asynchronously</li>
 *   <li>Conservative estimate: returns max civilization value when data is not loaded</li>
 * </ul>
 */
public final class TtlPyramidCache implements CivilizationCache {

    private static final Logger CACHE_LOG = LoggerFactory.getLogger("civil-cache");

    /** Hot cache TTL: 30 minutes (controls memory capacity). */
    public static final long DEFAULT_TTL_MILLIS = 30 * 60 * 1000L;

    /** Cold storage TTL: 24 hours (civilization decay). */
    public static final long DEFAULT_COLD_TTL_MILLIS = 24 * 60 * 60 * 1000L;

    /** Maximum civilization value (conservative estimate). */
    public static final double MAX_CIVILIZATION = 1.0;

    private final long ttlMillis;
    private final long coldTtlMillis;

    // L1 cache: VoxelChunk -> CScore
    private final ConcurrentHashMap<String, TimestampedEntry<CScore>> l1Cache = new ConcurrentHashMap<>();

    // L2 cache: L2Key -> L2Entry
    private final ConcurrentHashMap<String, TimestampedEntry<L2Entry>> l2Cache = new ConcurrentHashMap<>();

    // L3 cache: L3Key -> L3Entry
    private final ConcurrentHashMap<String, TimestampedEntry<L3Entry>> l3Cache = new ConcurrentHashMap<>();

    // Persistent storage
    private H2Storage storage;

    // Loading state tracker
    private final LoadingStateTracker loadingTracker = new LoadingStateTracker();

    public TtlPyramidCache() {
        this(DEFAULT_TTL_MILLIS, DEFAULT_COLD_TTL_MILLIS);
    }

    public TtlPyramidCache(long ttlMillis, long coldTtlMillis) {
        this.ttlMillis = ttlMillis;
        this.coldTtlMillis = coldTtlMillis;
    }

    /**
     * Set the persistent storage backend.
     */
    public void setStorage(H2Storage storage) {
        this.storage = storage;
    }

    /**
     * Get the loading state tracker.
     */
    public LoadingStateTracker getLoadingTracker() {
        return loadingTracker;
    }

    // ========== Cache key generation ==========

    private static String l1Key(ServerWorld level, VoxelChunkKey key) {
        return level.getRegistryKey().toString() + "|" + key.getCx() + "|" + key.getCz() + "|" + key.getSy();
    }

    private static String l2Key(ServerWorld level, L2Key key) {
        return level.getRegistryKey().toString() + "|" + key.getC2x() + "|" + key.getC2z() + "|" + key.getS2y();
    }

    private static String l3Key(ServerWorld level, L3Key key) {
        return level.getRegistryKey().toString() + "|" + key.getC3x() + "|" + key.getC3z() + "|" + key.getS3y();
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
            // Check TTL
            if (entry.isExpired(ttlMillis)) {
                // Expired = civilization decay, evict directly
                l1Cache.remove(k);
                if (CivilMod.DEBUG) {
                    CACHE_LOG.info("[civil-cache] L1 TTL_EXPIRE dim={} cx={} cz={} sy={}",
                            getDim(level), key.getCx(), key.getCz(), key.getSy());
                }
                return Optional.empty();
            }

            entry.touch();
            if (CivilMod.DEBUG) {
                CACHE_LOG.info("[civil-cache] L1 HIT dim={} cx={} cz={} sy={}",
                        getDim(level), key.getCx(), key.getCz(), key.getSy());
            }
            return Optional.of(entry.getValue());
        }

        return Optional.empty();
    }

    @Override
    public void putChunkCScore(ServerWorld level, VoxelChunkKey key, CScore cScore) {
        String k = l1Key(level, key);
        CScore toStore = cScore.headTypes() != null && !cScore.headTypes().isEmpty()
                ? cScore
                : new CScore(cScore.score(), List.of());

        l1Cache.put(k, new TimestampedEntry<>(toStore));

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache] L1 PUT dim={} cx={} cz={} sy={} score={}",
                    getDim(level), key.getCx(), key.getCz(), key.getSy(), toStore.score());
        }

        // Cascade update L2/L3 (hot cache only; cold storage written on eviction)
        double score = cScore.score();
        updateL2Cell(level, key, score);
        updateL3Cell(level, key, score);
        
        // L1 is not written to cold storage: brush core zone is always computed synchronously
    }

    @Override
    public void invalidateChunk(ServerWorld level, VoxelChunkKey key) {
        String k = l1Key(level, key);
        l1Cache.remove(k);

        // Mark L2/L3 as dirty
        markL2CellDirty(level, key);
        markL3CellDirty(level, key);

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache] L1 INVALIDATE dim={} cx={} cz={} sy={}",
                    getDim(level), key.getCx(), key.getCz(), key.getSy());
        }
    }

    @Override
    public void markChunkDirtyAt(ServerWorld level, BlockPos pos) {
        VoxelChunkKey key = VoxelChunkKey.from(pos);
        invalidateChunk(level, key);
    }

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
     * Restore L1 entry (for loading from database).
     */
    public void restoreL1(ServerWorld level, VoxelChunkKey key, CScore cScore, long createTime) {
        String k = l1Key(level, key);
        // Check if already expired
        if (System.currentTimeMillis() - createTime > ttlMillis) {
            return; // Expired, do not restore
        }
        l1Cache.put(k, new TimestampedEntry<>(cScore, createTime));
    }

    // ========== L2 operations ==========

    /**
     * Check if L2 entry exists (does not trigger repair; used for prefetch checks).
     */
    public boolean hasL2Entry(ServerWorld level, L2Key key) {
        String k = l2Key(level, key);
        TimestampedEntry<L2Entry> entry = l2Cache.get(k);
        if (entry == null) return false;
        if (entry.isExpired(ttlMillis)) {
            l2Cache.remove(k);
            return false;
        }
        return true;
    }

    /**
     * Get L2 entry.
     */
    public Optional<L2Entry> getL2Entry(ServerWorld level, L2Key key, ScoreComputer scoreComputer) {
        String k = l2Key(level, key);
        TimestampedEntry<L2Entry> entry = l2Cache.get(k);

        if (entry != null) {
            if (entry.isExpired(ttlMillis)) {
                l2Cache.remove(k);
                return Optional.empty();
            }

            // Repair dirty cells
            L2Entry l2Entry = entry.getValue();
            if (l2Entry.isDirty() && scoreComputer != null) {
                repairL2Dirty(level, l2Entry, scoreComputer);
            }

            entry.touch();
            return Optional.of(l2Entry);
        }

        return Optional.empty();
    }

    /**
     * Get or create L2 entry.
     */
    public L2Entry getOrCreateL2(ServerWorld level, L2Key key) {
        String k = l2Key(level, key);
        TimestampedEntry<L2Entry> entry = l2Cache.get(k);

        if (entry != null) {
            if (entry.isExpired(ttlMillis)) {
                l2Cache.remove(k);
            } else {
                entry.touch();
                return entry.getValue();
            }
        }

        // Create new entry
        L2Entry l2Entry = new L2Entry(key);
        l2Cache.put(k, new TimestampedEntry<>(l2Entry));
        return l2Entry;
    }

    /**
     * Update L2 cell (cascade call after L1 put).
     * Only updates hot cache; cold storage is written on TTL eviction.
     */
    private void updateL2Cell(ServerWorld level, VoxelChunkKey l1, double newScore) {
        L2Key l2Key = L2Key.from(l1);
        L2Entry entry = getOrCreateL2(level, l2Key);
        entry.updateCell(l1, newScore);
        // Do not write to cold storage immediately; written on TTL eviction
    }

    /**
     * Mark L2 cell as dirty.
     */
    private void markL2CellDirty(ServerWorld level, VoxelChunkKey l1) {
        L2Key l2Key = L2Key.from(l1);
        String k = l2Key(level, l2Key);
        TimestampedEntry<L2Entry> entry = l2Cache.get(k);
        if (entry != null && !entry.isExpired(ttlMillis)) {
            entry.getValue().markCellDirty(l1);
        }
    }

    /**
     * Repair L2 dirty cells.
     */
    private void repairL2Dirty(ServerWorld level, L2Entry entry, ScoreComputer scoreComputer) {
        L2Key l2Key = entry.getKey();
        entry.forEachDirtyCell(idx -> {
            VoxelChunkKey l1 = l2Key.indexToL1(idx);
            double newScore = scoreComputer.compute(level, l1);
            entry.updateCell(idx, newScore);
        });
    }

    /**
     * Restore L2 entry from cold storage.
     * 
     * @param createTime timestamp from cold storage (last access time)
     * @return true if restored successfully, false if cold storage expired (civilization decay)
     */
    public boolean restoreL2(ServerWorld level, L2Key key, L2Entry l2Entry, long createTime) {
        String k = l2Key(level, key);
        // Check cold storage TTL (24 hours, civilization decay)
        if (System.currentTimeMillis() - createTime > coldTtlMillis) {
            if (CivilMod.DEBUG) {
                CACHE_LOG.info("[civil-cache] L2 COLD_EXPIRED dim={} key={},{},{}", 
                        level.getRegistryKey(), key.getC2x(), key.getC2z(), key.getS2y());
            }
            return false; // Cold storage expired = civilization decay
        }
        // Restore with current time (start new 30-minute hot cache countdown)
        l2Cache.put(k, new TimestampedEntry<>(l2Entry, System.currentTimeMillis()));
        return true;
    }

    // ========== L3 operations ==========

    /**
     * Check if L3 entry exists (does not trigger repair; used for prefetch checks).
     */
    public boolean hasL3Entry(ServerWorld level, L3Key key) {
        String k = l3Key(level, key);
        TimestampedEntry<L3Entry> entry = l3Cache.get(k);
        if (entry == null) return false;
        if (entry.isExpired(ttlMillis)) {
            l3Cache.remove(k);
            return false;
        }
        return true;
    }

    /**
     * Get L3 entry.
     */
    public Optional<L3Entry> getL3Entry(ServerWorld level, L3Key key, ScoreComputer scoreComputer) {
        String k = l3Key(level, key);
        TimestampedEntry<L3Entry> entry = l3Cache.get(k);

        if (entry != null) {
            if (entry.isExpired(ttlMillis)) {
                l3Cache.remove(k);
                return Optional.empty();
            }

            // Repair dirty cells
            L3Entry l3Entry = entry.getValue();
            if (l3Entry.isDirty() && scoreComputer != null) {
                repairL3Dirty(level, l3Entry, scoreComputer);
            }

            entry.touch();
            return Optional.of(l3Entry);
        }

        return Optional.empty();
    }

    /**
     * Get or create L3 entry.
     */
    public L3Entry getOrCreateL3(ServerWorld level, L3Key key) {
        String k = l3Key(level, key);
        TimestampedEntry<L3Entry> entry = l3Cache.get(k);

        if (entry != null) {
            if (entry.isExpired(ttlMillis)) {
                l3Cache.remove(k);
            } else {
                entry.touch();
                return entry.getValue();
            }
        }

        // Create new entry
        L3Entry l3Entry = new L3Entry(key);
        l3Cache.put(k, new TimestampedEntry<>(l3Entry));
        return l3Entry;
    }

    /**
     * Update L3 cell (cascade call after L1 put).
     * Only updates hot cache; cold storage is written on TTL eviction.
     */
    private void updateL3Cell(ServerWorld level, VoxelChunkKey l1, double newScore) {
        L3Key l3Key = L3Key.from(l1);
        L3Entry entry = getOrCreateL3(level, l3Key);
        entry.updateCell(l1, newScore);
        // Do not write to cold storage immediately; written on TTL eviction
    }

    /**
     * Mark L3 cell as dirty.
     */
    private void markL3CellDirty(ServerWorld level, VoxelChunkKey l1) {
        L3Key l3Key = L3Key.from(l1);
        String k = l3Key(level, l3Key);
        TimestampedEntry<L3Entry> entry = l3Cache.get(k);
        if (entry != null && !entry.isExpired(ttlMillis)) {
            entry.getValue().markCellDirty(l1);
        }
    }

    /**
     * Repair L3 dirty cells.
     */
    private void repairL3Dirty(ServerWorld level, L3Entry entry, ScoreComputer scoreComputer) {
        L3Key l3Key = entry.getKey();
        entry.forEachDirtyCell(idx -> {
            VoxelChunkKey l1 = l3Key.indexToL1(idx);
            double newScore = scoreComputer.compute(level, l1);
            entry.updateCell(idx, newScore);
        });
    }

    /**
     * Restore L3 entry from cold storage.
     * 
     * @param createTime timestamp from cold storage (last access time)
     * @return true if restored successfully, false if cold storage expired (civilization decay)
     */
    public boolean restoreL3(ServerWorld level, L3Key key, L3Entry l3Entry, long createTime) {
        String k = l3Key(level, key);
        // Check cold storage TTL (24 hours, civilization decay)
        if (System.currentTimeMillis() - createTime > coldTtlMillis) {
            if (CivilMod.DEBUG) {
                CACHE_LOG.info("[civil-cache] L3 COLD_EXPIRED dim={} key={},{},{}", 
                        level.getRegistryKey(), key.getC3x(), key.getC3z(), key.getS3y());
            }
            return false; // Cold storage expired = civilization decay
        }
        // Restore with current time (start new 30-minute hot cache countdown)
        l3Cache.put(k, new TimestampedEntry<>(l3Entry, System.currentTimeMillis()));
        return true;
    }

    // ========== Maintenance operations ==========

    /**
     * Clean up all expired entries (called periodically).
     * L1 entries are evicted directly; L2/L3 entries are persisted to cold storage before eviction.
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        int l1Removed = 0, l2Removed = 0, l3Removed = 0;

        // Clean up L1 (no cold storage write; brush core zone is always computed synchronously)
        var l1Iterator = l1Cache.entrySet().iterator();
        while (l1Iterator.hasNext()) {
            var entry = l1Iterator.next();
            if (now - entry.getValue().getCreateTime() > ttlMillis) {
                l1Iterator.remove();
                l1Removed++;
            }
        }

        // Clean up L2 (persist to cold storage before eviction, preserving original timestamp)
        var l2Iterator = l2Cache.entrySet().iterator();
        while (l2Iterator.hasNext()) {
            var mapEntry = l2Iterator.next();
            long lastAccessTime = mapEntry.getValue().getCreateTime();
            if (now - lastAccessTime > ttlMillis) {
                // Parse key and write to cold storage
                if (storage != null) {
                    String[] parts = mapEntry.getKey().split("\\|");
                    if (parts.length == 4) {
                        String dim = parts[0];
                        L2Key l2Key = new L2Key(
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3])
                        );
                        // Preserve original timestamp (last access time) for cold storage TTL evaluation
                        storage.saveL2Async(dim, l2Key, mapEntry.getValue().getValue(), lastAccessTime);
                    }
                }
                l2Iterator.remove();
                l2Removed++;
            }
        }

        // Clean up L3 (persist to cold storage before eviction, preserving original timestamp)
        var l3Iterator = l3Cache.entrySet().iterator();
        while (l3Iterator.hasNext()) {
            var mapEntry = l3Iterator.next();
            long lastAccessTime = mapEntry.getValue().getCreateTime();
            if (now - lastAccessTime > ttlMillis) {
                // Parse key and write to cold storage
                if (storage != null) {
                    String[] parts = mapEntry.getKey().split("\\|");
                    if (parts.length == 4) {
                        String dim = parts[0];
                        L3Key l3Key = new L3Key(
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3])
                        );
                        // Preserve original timestamp (last access time) for cold storage TTL evaluation
                        storage.saveL3Async(dim, l3Key, mapEntry.getValue().getValue(), lastAccessTime);
                    }
                }
                l3Iterator.remove();
                l3Removed++;
            }
        }

        if (CivilMod.DEBUG && (l1Removed > 0 || l2Removed > 0 || l3Removed > 0)) {
            int coldWrites = l2Removed + l3Removed;  // L2/L3 written to cold storage on eviction
            CACHE_LOG.info("[civil-ttl-evict] L1={} L2={} L3={} coldWrites={}", 
                    l1Removed, l2Removed, l3Removed, coldWrites);
        }
    }

    // ========== Statistics ==========

    public int l1Size() {
        return l1Cache.size();
    }

    public int l2Size() {
        return l2Cache.size();
    }

    public int l3Size() {
        return l3Cache.size();
    }

    public long getTtlMillis() {
        return ttlMillis;
    }

    // ========== Full persistence (called on shutdown) ==========

    /**
     * Persist all L2 entries to cold storage (called on shutdown).
     */
    public void persistAllL2() {
        if (storage == null) return;
        
        for (var mapEntry : l2Cache.entrySet()) {
            String[] parts = mapEntry.getKey().split("\\|");
            if (parts.length == 4) {
                String dim = parts[0];
                L2Key l2Key = new L2Key(
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                );
                // Preserve original timestamp
                long lastAccessTime = mapEntry.getValue().getCreateTime();
                storage.saveL2Async(dim, l2Key, mapEntry.getValue().getValue(), lastAccessTime);
            }
        }
        
        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache] Persisted {} L2 entries", l2Cache.size());
        }
    }

    /**
     * Persist all L3 entries to cold storage (called on shutdown).
     */
    public void persistAllL3() {
        if (storage == null) return;
        
        for (var mapEntry : l3Cache.entrySet()) {
            String[] parts = mapEntry.getKey().split("\\|");
            if (parts.length == 4) {
                String dim = parts[0];
                L3Key l3Key = new L3Key(
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])
                );
                // Preserve original timestamp
                long lastAccessTime = mapEntry.getValue().getCreateTime();
                storage.saveL3Async(dim, l3Key, mapEntry.getValue().getValue(), lastAccessTime);
            }
        }
        
        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache] Persisted {} L3 entries", l3Cache.size());
        }
    }

    /**
     * Get cold storage TTL (civilization decay time).
     */
    public long getColdTtlMillis() {
        return coldTtlMillis;
    }
}
