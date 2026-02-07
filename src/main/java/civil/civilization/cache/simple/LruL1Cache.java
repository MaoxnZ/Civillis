package civil.civilization.cache.simple;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.civilization.cache.CivilizationCache;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Civilization value cache by voxel chunk (VoxelChunkKey), LRU eviction, fixed capacity.
 * Does not consider player/chunk distance, only evicts least recently used items by access order.
 * Each PUT/INVALIDATE/HIT writes a log line for analysis script parsing (see scripts/plot_civil_cache_log.py).
 */
public final class LruL1Cache implements CivilizationCache {

    private static final Logger CACHE_LOG = LoggerFactory.getLogger("civil-cache");

    private static String cacheKey(ServerWorld level, VoxelChunkKey key) {
        return level.getRegistryKey().toString()
                + "|" + key.getCx() + "|" + key.getCz() + "|" + key.getSy();
    }

    private final int maxSize;
    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();
    private final ReentrantLock lruLock = new ReentrantLock();
    private volatile String lruHead; // Least recently used
    private volatile String lruTail; // Most recently used

    public LruL1Cache(int maxSize) {
        this.maxSize = maxSize <= 0 ? 4096 : maxSize;
    }

    /** Default capacity 4096. */
    public LruL1Cache() {
        this(4096);
    }

    @Override
    public Optional<CScore> getChunkCScore(ServerWorld level, VoxelChunkKey key) {
        String k = cacheKey(level, key);
        Entry e = map.get(k);
        if (e == null) {
            return Optional.empty();
        }
        touch(k);
        if (CivilMod.DEBUG) {
            CACHE_LOG.info("[civil-cache] HIT dim={} cx={} cz={} sy={}", level.getRegistryKey().toString(), key.getCx(), key.getCz(), key.getSy());
        }
        return Optional.of(e.cScore);
    }

    @Override
    public void putChunkCScore(ServerWorld level, VoxelChunkKey key, CScore cScore) {
        String k = cacheKey(level, key);
        CScore toStore = cScore.headTypes() != null && !cScore.headTypes().isEmpty()
                ? cScore
                : new CScore(cScore.score(), java.util.List.of());
        lruLock.lock();
        try {
            if (map.containsKey(k)) {
                map.get(k).cScore = toStore;
                touchUnlocked(k);
                if (CivilMod.DEBUG) {
                    CACHE_LOG.info("[civil-cache] PUT dim={} cx={} cz={} sy={} score={}", level.getRegistryKey().toString(), key.getCx(), key.getCz(), key.getSy(), toStore.score());
                }
                return;
            }
            while (map.size() >= maxSize && lruHead != null) {
                evict(lruHead);
            }
            Entry e = new Entry(toStore, lruTail);
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
                CACHE_LOG.info("[civil-cache] PUT dim={} cx={} cz={} sy={} score={}", level.getRegistryKey().toString(), key.getCx(), key.getCz(), key.getSy(), toStore.score());
            }
        } finally {
            lruLock.unlock();
        }
    }

    @Override
    public void invalidateChunk(ServerWorld level, VoxelChunkKey key) {
        String k = cacheKey(level, key);
        lruLock.lock();
        try {
            remove(k);
            if (CivilMod.DEBUG) {
                CACHE_LOG.info("[civil-cache] INVALIDATE dim={} cx={} cz={} sy={}", level.getRegistryKey().toString(), key.getCx(), key.getCz(), key.getSy());
            }
        } finally {
            lruLock.unlock();
        }
    }

    private void touch(String k) {
        lruLock.lock();
        try {
            touchUnlocked(k);
        } finally {
            lruLock.unlock();
        }
    }

    /** Caller already holds lruLock. */
    private void touchUnlocked(String k) {
        Entry e = map.get(k);
        if (e == null) return;
        unlink(k);
        linkAtTail(k, e.cScore);
    }

    private void linkAtTail(String k, CScore cScore) {
        Entry e = map.get(k);
        if (e == null) return;
        e.cScore = cScore;
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

    private void remove(String k) {
        if (!map.containsKey(k)) return;
        evict(k);
    }

    private static final class Entry {
        CScore cScore;
        String prev;
        String next;

        Entry(CScore cScore, String prev) {
            this.cScore = cScore;
            this.prev = prev;
            this.next = null;
        }
    }

    /** Current number of cached voxel chunks (for debugging). */
    public int size() {
        return map.size();
    }

    // ========== Persistence Support ==========

    /**
     * Iterate over all cache entries (for persistence save).
     */
    public void forEachEntry(L1EntryConsumer consumer) {
        for (java.util.Map.Entry<String, Entry> e : map.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            if (parts.length == 4) {
                String dim = parts[0];
                int cx = Integer.parseInt(parts[1]);
                int cz = Integer.parseInt(parts[2]);
                int sy = Integer.parseInt(parts[3]);
                consumer.accept(dim, new VoxelChunkKey(cx, cz, sy), e.getValue().cScore);
            }
        }
    }

    /**
     * Restore a cache entry (for persistence load).
     */
    public void restoreEntry(String dim, VoxelChunkKey key, CScore cScore) {
        String k = dim + "|" + key.getCx() + "|" + key.getCz() + "|" + key.getSy();
        lruLock.lock();
        try {
            if (map.containsKey(k)) {
                return;  // Already exists, skip
            }
            while (map.size() >= maxSize && lruHead != null) {
                evict(lruHead);
            }
            Entry e = new Entry(cScore, lruTail);
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
                CACHE_LOG.info("[civil-cache] RESTORE dim={} cx={} cz={} sy={} score={}", dim, key.getCx(), key.getCz(), key.getSy(), cScore.score());
            }
        } finally {
            lruLock.unlock();
        }
    }

    @FunctionalInterface
    public interface L1EntryConsumer {
        void accept(String dim, VoxelChunkKey key, CScore cScore);
    }
}
