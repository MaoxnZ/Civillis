package civil.civilization.cache.simple;

import civil.CivilMod;
import civil.civilization.cache.ScoreComputer;
import civil.civilization.structure.L3Entry;
import civil.civilization.structure.L3Key;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * L3 cache: aggregated cache for 9×9×3 voxel chunks.
 * 
 * <p>Differences from L1/L2 cache:
 * <ul>
 *   <li>L3 cache entries store detailed state of 243 cells</li>
 *   <li>L3 eviction does not affect L1/L2</li>
 *   <li>L3 has markDirty operation, used to mark dirty cells when world blocks change</li>
 *   <li>L3 get needs to repair dirty cells if dirty (but does not write back to L1)</li>
 * </ul>
 */
public final class LruL3Cache {

    private static final Logger CACHE_LOG = LoggerFactory.getLogger("civil-cache-l3");

    private static String cacheKey(ServerWorld level, L3Key key) {
        return level.getRegistryKey().toString()
                + "|" + key.getC3x() + "|" + key.getC3z() + "|" + key.getS3y();
    }

    private final int maxSize;
    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final ReentrantLock lruLock = new ReentrantLock();
    private volatile String lruHead;
    private volatile String lruTail;

    /** L2Cache reference, for cross-updates. */
    private LruL2Cache l2Cache;

    public LruL3Cache(int maxSize) {
        this.maxSize = maxSize <= 0 ? 512 : maxSize;
    }

    public LruL3Cache() {
        this(512);
    }

    /**
     * Set L2Cache reference, for cross-updates.
     * Must be called before use.
     */
    public void setL2Cache(LruL2Cache l2Cache) {
        this.l2Cache = l2Cache;
    }

    /**
     * Get L3 entry. If dirty, repair all dirty cells before returning.
     * 
     * @param level World
     * @param key L3 key
     * @param scoreComputer Function to calculate single L1 score (used when repairing dirty cells)
     * @return L3Entry (may have just been repaired)
     */
    public Optional<L3Entry> get(ServerWorld level, L3Key key, ScoreComputer scoreComputer) {
        String k = cacheKey(level, key);
        Entry e = map.get(k);
        if (e == null) {
            return Optional.empty();
        }
        touch(k);

        // If dirty, repair all dirty cells
        if (e.l3Entry.isDirty()) {
            repairDirty(level, e.l3Entry, scoreComputer);
        }

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache-l3] GET dim={} c3x={} c3z={} s3y={} validCount={} score={}", 
                    level.getRegistryKey().toString(), key.getC3x(), key.getC3z(), key.getS3y(), 
                    e.l3Entry.getValidCount(), e.l3Entry.getCoarseScore());
        }
        return Optional.of(e.l3Entry);
    }

    /**
     * Get L3 entry (does not repair dirty, used during cascade updates).
     */
    public Optional<L3Entry> getOrCreate(ServerWorld level, L3Key key) {
        String k = cacheKey(level, key);
        Entry e = map.get(k);
        if (e != null) {
            touch(k);
            return Optional.of(e.l3Entry);
        }
        // If not exists, create
        return Optional.of(createEntry(level, key));
    }

    /**
     * Create new L3 entry (used when L1 put cascades creation).
     */
    public L3Entry createEntry(ServerWorld level, L3Key key) {
        String k = cacheKey(level, key);
        lruLock.lock();
        try {
            Entry existing = map.get(k);
            if (existing != null) {
                touchUnlocked(k);
                return existing.l3Entry;
            }
            while (map.size() >= maxSize && lruHead != null) {
                evict(lruHead);
            }
            L3Entry l3Entry = new L3Entry(key);
            Entry e = new Entry(l3Entry, lruTail);
            if (lruTail != null) {
                map.get(lruTail).next = k;
            }
            e.next = null;
            map.put(k, e);
            lruTail = k;
            if (lruHead == null) {
                lruHead = k;
            }
            if (CivilMod.DEBUG) {
                CACHE_LOG.info("[civil-cache-l3] CREATE dim={} c3x={} c3z={} s3y={}", 
                        level.getRegistryKey().toString(), key.getC3x(), key.getC3z(), key.getS3y());
            }
            return l3Entry;
        } finally {
            lruLock.unlock();
        }
    }

    /**
     * Update the cell corresponding to an L1 (called after L1 put cascades).
     * If L3 entry does not exist, create it.
     */
    public void updateCell(ServerWorld level, VoxelChunkKey l1, double newScore) {
        L3Key l3Key = L3Key.from(l1);
        L3Entry entry = getOrCreate(level, l3Key).orElseThrow();
        entry.updateCell(l1, newScore);

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache-l3] UPDATE_CELL dim={} c3x={} c3z={} s3y={} score={}", 
                    level.getRegistryKey().toString(), l3Key.getC3x(), l3Key.getC3z(), l3Key.getS3y(), newScore);
        }
    }

    /**
     * If the cell corresponding to an L1 is DIRTY or EMPTY, update it (for cross-updates).
     * If L3 entry does not exist, skip.
     */
    public void updateCellIfDirtyOrEmpty(ServerWorld level, VoxelChunkKey l1, double newScore) {
        L3Key l3Key = L3Key.from(l1);
        String k = cacheKey(level, l3Key);
        Entry e = map.get(k);
        if (e == null) {
            return;  // L3 entry does not exist, skip
        }
        int idx = l3Key.l1ToIndex(l1);
        byte state = e.l3Entry.getCellState(idx);
        if (state == L3Entry.STATE_DIRTY || state == L3Entry.STATE_EMPTY) {
            e.l3Entry.updateCell(idx, newScore);
        }
    }

    /**
     * If the cell corresponding to an L1 is DIRTY, update it (for L2 cross-updates).
     * If L3 entry does not exist or cell is not DIRTY, skip.
     */
    public void updateCellIfDirty(ServerWorld level, VoxelChunkKey l1, double newScore) {
        L3Key l3Key = L3Key.from(l1);
        String k = cacheKey(level, l3Key);
        Entry e = map.get(k);
        if (e == null) {
            return;  // L3 entry does not exist, skip
        }
        int idx = l3Key.l1ToIndex(l1);
        if (e.l3Entry.getCellState(idx) == L3Entry.STATE_DIRTY) {
            e.l3Entry.updateCell(idx, newScore);
        }
    }

    /**
     * Mark the cell corresponding to an L1 as dirty (called when world blocks change).
     * If L3 entry does not exist, skip (no need to mark non-existent cache).
     */
    public void markCellDirty(ServerWorld level, VoxelChunkKey l1) {
        L3Key l3Key = L3Key.from(l1);
        String k = cacheKey(level, l3Key);
        Entry e = map.get(k);
        if (e == null) {
            return;  // L3 entry does not exist, skip
        }
        e.l3Entry.markCellDirty(l1);

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache-l3] MARK_DIRTY dim={} c3x={} c3z={} s3y={}", 
                    level.getRegistryKey().toString(), l3Key.getC3x(), l3Key.getC3z(), l3Key.getS3y());
        }
    }

    /**
     * Repair all dirty cells in L3 entry.
     * Does not write back to L1, but cross-updates L2.
     */
    private void repairDirty(ServerWorld level, L3Entry entry, ScoreComputer scoreComputer) {
        L3Key l3Key = entry.getKey();
        entry.forEachDirtyCell(idx -> {
            VoxelChunkKey l1 = l3Key.indexToL1(idx);
            double newScore = scoreComputer.compute(level, l1);
            entry.updateCell(idx, newScore);

            // Cross-update L2 (if L2 entry exists and this cell is also dirty)
            if (l2Cache != null) {
                l2Cache.updateCellIfDirtyOrEmpty(level, l1, newScore);
            }
        });

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache-l3] REPAIR dim={} c3x={} c3z={} s3y={} score={}", 
                    level.getRegistryKey().toString(), l3Key.getC3x(), l3Key.getC3z(), l3Key.getS3y(), entry.getCoarseScore());
        }
    }

    // ========== LRU Management ==========

    private void touch(String k) {
        lruLock.lock();
        try {
            touchUnlocked(k);
        } finally {
            lruLock.unlock();
        }
    }

    private void touchUnlocked(String k) {
        Entry e = map.get(k);
        if (e == null) return;
        unlink(k);
        linkAtTail(k, e);
    }

    private void linkAtTail(String k, Entry e) {
        e.prev = lruTail;
        e.next = null;
        if (lruTail != null) {
            map.get(lruTail).next = k;
        }
        lruTail = k;
        if (lruHead == null) {
            lruHead = k;
        }
    }

    private void unlink(String k) {
        Entry e = map.get(k);
        if (e == null) return;
        if (e.prev != null) {
            map.get(e.prev).next = e.next;
        } else {
            lruHead = e.next;
        }
        if (e.next != null) {
            map.get(e.next).prev = e.prev;
        } else {
            lruTail = e.prev;
        }
    }

    private void evict(String k) {
        unlink(k);
        map.remove(k);
    }

    public int size() {
        return map.size();
    }

    private static final class Entry {
        L3Entry l3Entry;
        String prev;
        String next;

        Entry(L3Entry l3Entry, String prev) {
            this.l3Entry = l3Entry;
            this.prev = prev;
            this.next = null;
        }
    }

    // ========== Persistence Support ==========

    /**
     * Iterate over all cache entries (for persistence save).
     */
    public void forEachEntry(L3EntryConsumer consumer) {
        for (java.util.Map.Entry<String, Entry> e : map.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            if (parts.length == 4) {
                String dim = parts[0];
                int c3x = Integer.parseInt(parts[1]);
                int c3z = Integer.parseInt(parts[2]);
                int s3y = Integer.parseInt(parts[3]);
                consumer.accept(dim, new L3Key(c3x, c3z, s3y), e.getValue().l3Entry);
            }
        }
    }

    /**
     * Restore a cache entry (for persistence load).
     */
    public void restoreEntry(String dim, L3Key key, L3Entry l3Entry) {
        String k = dim + "|" + key.getC3x() + "|" + key.getC3z() + "|" + key.getS3y();
        lruLock.lock();
        try {
            if (map.containsKey(k)) {
                return;  // Already exists, skip
            }
            while (map.size() >= maxSize && lruHead != null) {
                evict(lruHead);
            }
            Entry e = new Entry(l3Entry, lruTail);
            if (lruTail != null) {
                map.get(lruTail).next = k;
            }
            e.next = null;
            map.put(k, e);
            lruTail = k;
            if (lruHead == null) {
                lruHead = k;
            }
            if (CivilMod.DEBUG) {
                CACHE_LOG.info("[civil-cache-l3] RESTORE dim={} c3x={} c3z={} s3y={} score={}", 
                        dim, key.getC3x(), key.getC3z(), key.getS3y(), l3Entry.getCoarseScore());
            }
        } finally {
            lruLock.unlock();
        }
    }

    @FunctionalInterface
    public interface L3EntryConsumer {
        void accept(String dim, L3Key key, L3Entry entry);
    }
}
