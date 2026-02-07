package civil.civilization.cache.simple;

import civil.CivilMod;
import civil.civilization.cache.ScoreComputer;
import civil.civilization.structure.L2Entry;
import civil.civilization.structure.L2Key;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * L2 cache: aggregated cache for 3×3×1 voxel chunks.
 * 
 * <p>Differences from L1 cache:
 * <ul>
 *   <li>L2 cache entries store detailed state of 9 cells</li>
 *   <li>L2 eviction does not affect L1 (L1 LRU is independently managed)</li>
 *   <li>L2 has markDirty operation, used to mark dirty cells when world blocks change</li>
 *   <li>L2 get needs to repair dirty cells if dirty (but does not write back to L1)</li>
 * </ul>
 */
public final class LruL2Cache {

    private static final Logger CACHE_LOG = LoggerFactory.getLogger("civil-cache-l2");

    private static String cacheKey(ServerWorld level, L2Key key) {
        return level.getRegistryKey().toString()
                + "|" + key.getC2x() + "|" + key.getC2z() + "|" + key.getS2y();
    }

    private final int maxSize;
    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final ReentrantLock lruLock = new ReentrantLock();
    private volatile String lruHead;
    private volatile String lruTail;

    /** L3Cache reference, for cross-updates. */
    private LruL3Cache l3Cache;

    public LruL2Cache(int maxSize) {
        this.maxSize = maxSize <= 0 ? 2048 : maxSize;
    }

    public LruL2Cache() {
        this(2048);
    }

    /**
     * Set L3Cache reference, for cross-updates.
     * Must be called before use.
     */
    public void setL3Cache(LruL3Cache l3Cache) {
        this.l3Cache = l3Cache;
    }

    /**
     * Get L2 entry. If dirty, repair all dirty cells before returning.
     * 
     * @param level World
     * @param key L2 key
     * @param scoreComputer Function to calculate single L1 score (used when repairing dirty cells)
     * @return L2Entry (may have just been repaired)
     */
    public Optional<L2Entry> get(ServerWorld level, L2Key key, ScoreComputer scoreComputer) {
        String k = cacheKey(level, key);
        Entry e = map.get(k);
        if (e == null) {
            return Optional.empty();
        }
        touch(k);

        // If dirty, repair all dirty cells
        if (e.l2Entry.isDirty()) {
            repairDirty(level, e.l2Entry, scoreComputer);
        }

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache-l2] GET dim={} c2x={} c2z={} s2y={} validCount={} score={}", 
                    level.getRegistryKey().toString(), key.getC2x(), key.getC2z(), key.getS2y(), 
                    e.l2Entry.getValidCount(), e.l2Entry.getCoarseScore());
        }
        return Optional.of(e.l2Entry);
    }

    /**
     * Get L2 entry (does not repair dirty, used during cascade updates).
     */
    public Optional<L2Entry> getOrCreate(ServerWorld level, L2Key key) {
        String k = cacheKey(level, key);
        Entry e = map.get(k);
        if (e != null) {
            touch(k);
            return Optional.of(e.l2Entry);
        }
        // If not exists, create
        return Optional.of(createEntry(level, key));
    }

    /**
     * Create new L2 entry (used when L1 put cascades creation).
     */
    public L2Entry createEntry(ServerWorld level, L2Key key) {
        String k = cacheKey(level, key);
        lruLock.lock();
        try {
            Entry existing = map.get(k);
            if (existing != null) {
                touchUnlocked(k);
                return existing.l2Entry;
            }
            while (map.size() >= maxSize && lruHead != null) {
                evict(lruHead);
            }
            L2Entry l2Entry = new L2Entry(key);
            Entry e = new Entry(l2Entry, lruTail);
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
                CACHE_LOG.info("[civil-cache-l2] CREATE dim={} c2x={} c2z={} s2y={}", 
                        level.getRegistryKey().toString(), key.getC2x(), key.getC2z(), key.getS2y());
            }
            return l2Entry;
        } finally {
            lruLock.unlock();
        }
    }

    /**
     * Update the cell corresponding to an L1 (called after L1 put cascades).
     * If L2 entry does not exist, create it.
     */
    public void updateCell(ServerWorld level, VoxelChunkKey l1, double newScore) {
        L2Key l2Key = L2Key.from(l1);
        L2Entry entry = getOrCreate(level, l2Key).orElseThrow();
        entry.updateCell(l1, newScore);

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache-l2] UPDATE_CELL dim={} c2x={} c2z={} s2y={} score={}", 
                    level.getRegistryKey().toString(), l2Key.getC2x(), l2Key.getC2z(), l2Key.getS2y(), newScore);
        }
    }

    /**
     * If the cell corresponding to an L1 is DIRTY or EMPTY, update it (for cross-updates).
     * If L2 entry does not exist, skip.
     */
    public void updateCellIfDirtyOrEmpty(ServerWorld level, VoxelChunkKey l1, double newScore) {
        L2Key l2Key = L2Key.from(l1);
        String k = cacheKey(level, l2Key);
        Entry e = map.get(k);
        if (e == null) {
            return;  // L2 entry does not exist, skip
        }
        int idx = l2Key.l1ToIndex(l1);
        byte state = e.l2Entry.getCellState(idx);
        if (state == L2Entry.STATE_DIRTY || state == L2Entry.STATE_EMPTY) {
            e.l2Entry.updateCell(idx, newScore);
        }
    }

    /**
     * Mark the cell corresponding to an L1 as dirty (called when world blocks change).
     * If L2 entry does not exist, skip (no need to mark non-existent cache).
     */
    public void markCellDirty(ServerWorld level, VoxelChunkKey l1) {
        L2Key l2Key = L2Key.from(l1);
        String k = cacheKey(level, l2Key);
        Entry e = map.get(k);
        if (e == null) {
            return;  // L2 entry does not exist, skip
        }
        e.l2Entry.markCellDirty(l1);

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache-l2] MARK_DIRTY dim={} c2x={} c2z={} s2y={}", 
                    level.getRegistryKey().toString(), l2Key.getC2x(), l2Key.getC2z(), l2Key.getS2y());
        }
    }

    /**
     * Repair all dirty cells in L2 entry.
     * Does not write back to L1, but cross-updates L3.
     */
    private void repairDirty(ServerWorld level, L2Entry entry, ScoreComputer scoreComputer) {
        L2Key l2Key = entry.getKey();
        entry.forEachDirtyCell(idx -> {
            VoxelChunkKey l1 = l2Key.indexToL1(idx);
            double newScore = scoreComputer.compute(level, l1);
            entry.updateCell(idx, newScore);

            // Cross-update L3 (if L3 entry exists and this cell is also dirty)
            if (l3Cache != null) {
                l3Cache.updateCellIfDirtyOrEmpty(level, l1, newScore);
            }
        });

        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache-l2] REPAIR dim={} c2x={} c2z={} s2y={} score={}", 
                    level.getRegistryKey().toString(), l2Key.getC2x(), l2Key.getC2z(), l2Key.getS2y(), entry.getCoarseScore());
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
        L2Entry l2Entry;
        String prev;
        String next;

        Entry(L2Entry l2Entry, String prev) {
            this.l2Entry = l2Entry;
            this.prev = prev;
            this.next = null;
        }
    }

    // ========== Persistence Support ==========

    /**
     * Iterate over all cache entries (for persistence save).
     */
    public void forEachEntry(L2EntryConsumer consumer) {
        for (java.util.Map.Entry<String, Entry> e : map.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            if (parts.length == 4) {
                String dim = parts[0];
                int c2x = Integer.parseInt(parts[1]);
                int c2z = Integer.parseInt(parts[2]);
                int s2y = Integer.parseInt(parts[3]);
                consumer.accept(dim, new L2Key(c2x, c2z, s2y), e.getValue().l2Entry);
            }
        }
    }

    /**
     * Restore a cache entry (for persistence load).
     */
    public void restoreEntry(String dim, L2Key key, L2Entry l2Entry) {
        String k = dim + "|" + key.getC2x() + "|" + key.getC2z() + "|" + key.getS2y();
        lruLock.lock();
        try {
            if (map.containsKey(k)) {
                return;  // Already exists, skip
            }
            while (map.size() >= maxSize && lruHead != null) {
                evict(lruHead);
            }
            Entry e = new Entry(l2Entry, lruTail);
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
                CACHE_LOG.info("[civil-cache-l2] RESTORE dim={} c2x={} c2z={} s2y={} score={}", 
                        dim, key.getC2x(), key.getC2z(), key.getS2y(), l2Entry.getCoarseScore());
            }
        } finally {
            lruLock.unlock();
        }
    }

    @FunctionalInterface
    public interface L2EntryConsumer {
        void accept(String dim, L2Key key, L2Entry entry);
    }
}
