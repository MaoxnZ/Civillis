package civil.civilization.cache;

import civil.CivilMod;
import civil.civilization.ServerClock;
import civil.config.CivilConfig;
import civil.civilization.VoxelChunkKey;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import civil.civilization.storage.CivilStorage;

/**
 * Fusion Architecture result shard cache: pre-aggregated civilization scores per Voxel Chunk.
 *
 * <p>Provides O(1) spawn-check queries by caching the weighted sum of all L1 shards
 * within the detection range. Entries are lazily computed on first access and incrementally
 * updated via delta propagation when blocks change.
 *
 * <p>Not persisted to disk — result shards are pure derived data from L1 shards and can be
 * recomputed in ~34μs from cached L1 scores.
 *
 * <p>Non-cacheable (partial) result entries use a <b>fixed wall-clock lifetime</b> from
 * {@link ResultEntry#getCreateTime()} — they are evicted after 5000 ms regardless of read
 * frequency, so map/prefetch hot paths cannot pin stale partials forever.
 *
 * @see ResultEntry
 */
public final class ResultCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-result-cache");
    /** Max age (ms) for a partial result shard; after this it must recompute (may promote to main). */
    private static final long PARTIAL_MAX_LIFETIME_MS = 5_000L;

    private static boolean partialExpired(long nowMillis, TimestampedEntry<ResultEntry> wrapped) {
        ResultEntry re = wrapped.getValue();
        return nowMillis - re.getCreateTime() > PARTIAL_MAX_LIFETIME_MS;
    }

    private final ConcurrentHashMap<String, TimestampedEntry<ResultEntry>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TimestampedEntry<ResultEntry>> partialBackoffCache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    /**
     * Write-back buffer for presenceTime persistence.
     *
     * <p>When {@link #visitAround} advances a ResultEntry's presenceTime,
     * the new value is staged here. Every 30 seconds (and on shutdown),
     * {@link #flushPresence} forwards staged values into {@link CivilStorage} on flush ticks.
     *
     * <p><b>This is NOT related to cache invalidation or delta propagation.</b>
     * It is purely an I/O batching mechanism to avoid per-tick disk writes.
     */
    private final ConcurrentHashMap<String, CivilStorage.PresenceSaveRequest> pendingPresenceWrites = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> pendingPresenceDeleteKeys = new ConcurrentHashMap<>();

    public ResultCache() {
        this(CivilConfig.resultTtlMs);
    }

    public ResultCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public record ComputeResult(ResultEntry entry, boolean cacheable) {}

    // ========== Cache key ==========

    private static String key(ServerLevel world, VoxelChunkKey vc) {
        return world.dimension().identifier().toString() + "|" + vc.getCx() + "|" + vc.getCz() + "|" + vc.getSy();
    }

    private static String key(String dim, VoxelChunkKey vc) {
        return dim + "|" + vc.getCx() + "|" + vc.getCz() + "|" + vc.getSy();
    }

    // ========== Core query ==========

    /**
     * Get the result entry for a VC, computing it if necessary.
     *
     * <p>Flow:
     * <ol>
     *   <li>Hit + config valid → return O(1)</li>
     *   <li>Hit + config invalid → recompute from L1 shards, update entry</li>
     *   <li>Miss → compute from L1 shards, cache new entry</li>
     * </ol>
     *
     * @param world    server world
     * @param centerVC the voxel chunk to query
     * @param computer function (world, centerVC) → fresh ResultEntry
     * @return the result entry (never null)
     */
    public ResultEntry getOrCompute(ServerLevel world, VoxelChunkKey centerVC,
                                    BiFunction<ServerLevel, VoxelChunkKey, ComputeResult> computer) {
        String k = key(world, centerVC);
        TimestampedEntry<ResultEntry> cached = cache.get(k);

        if (cached != null && !cached.isExpired(ttlMillis)) {
            ResultEntry entry = cached.getValue();
            if (entry.isConfigValid()) {
                cached.touch();
                return entry; // O(1) hit
            }
        }

        long nowMillis = System.currentTimeMillis();
        TimestampedEntry<ResultEntry> partial = partialBackoffCache.get(k);
        if (partial != null && !partialExpired(nowMillis, partial)) {
            return partial.getValue();
        }

        // Miss or config mismatch → compute
        ComputeResult computed = computer.apply(world, centerVC);
        ResultEntry entry = computed.entry();
        if (!computed.cacheable()) {
            partialBackoffCache.put(k, new TimestampedEntry<>(entry));
            return entry;
        }

        // Config mismatch → preserve presence from previous cached entry.
        if (cached != null && !cached.isExpired(ttlMillis)) {
            ResultEntry prev = cached.getValue();
            if (!prev.isConfigValid()) {
                entry.presenceTime = prev.presenceTime;
                entry.lastRecoveryTime = prev.lastRecoveryTime;
            }
        }

        cache.put(k, new TimestampedEntry<>(entry));
        partialBackoffCache.remove(k);

        if (entry.getRawScore(ServerClock.now()) > CivilConfig.presenceRawEpsilon) {
            pendingPresenceDeleteKeys.remove(k);
            pendingPresenceWrites.put(k, new CivilStorage.PresenceSaveRequest(
                    world.dimension().identifier().toString(), centerVC,
                    entry.presenceTime, entry.lastRecoveryTime));
        } else {
            pendingPresenceWrites.remove(k);
            pendingPresenceDeleteKeys.put(k, Boolean.TRUE);
        }

        return entry;
    }

    /**
     * Get existing result entry without computing (for delta propagation).
     *
     * @return the entry, or null if not cached / expired
     */
    public ResultEntry getIfPresent(ServerLevel world, VoxelChunkKey vc) {
        String k = key(world, vc);
        TimestampedEntry<ResultEntry> cached = cache.get(k);
        if (cached == null || cached.isExpired(ttlMillis)) return null;
        return cached.getValue();
    }

    /**
     * Get existing result entry by dim string (for delta propagation without world ref).
     */
    public ResultEntry getIfPresent(String dim, VoxelChunkKey vc) {
        String k = key(dim, vc);
        TimestampedEntry<ResultEntry> cached = cache.get(k);
        if (cached == null || cached.isExpired(ttlMillis)) return null;
        return cached.getValue();
    }

    // ========== Delta propagation ==========

    /**
     * Propagate an L1 score delta to all affected result shards.
     *
     * <p>When a civilization block is placed/removed:
     * <ol>
     *   <li>L1 shard is recomputed (palette accelerated)</li>
     *   <li>delta = newScore - oldScore</li>
     *   <li>This method applies delta × weight(dist) to every cached result shard
     *       within detection range of the changed L1 shard</li>
     * </ol>
     *
     * <p>By symmetry: the set of result shards affected by L1[X] changing =
     * the set of L1 shards that would be aggregated if we computed a result at X.
     * So we iterate [-rx, rx] × [-rz, rz] × [-ry, ry] around the changed shard.
     *
     * @param dim      dimension string
     * @param shardKey the L1 shard that changed
     * @param delta    newScore - oldScore
     */
    public void propagateDelta(String dim, VoxelChunkKey shardKey, double delta) {
        if (Math.abs(delta) < 1e-10) return;

        int rx = CivilConfig.detectionRadiusX;
        int rz = CivilConfig.detectionRadiusZ;
        int ry = CivilConfig.detectionRadiusY;

        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                for (int dy = -ry; dy <= ry; dy++) {
                    VoxelChunkKey resultVC = shardKey.offset(dx, dz, dy);
                    ResultEntry entry = getIfPresent(dim, resultVC);
                    if (entry == null) continue;        // Not cached → will be computed fresh on access
                    if (!entry.isConfigValid()) continue; // Range mismatch → will be recomputed on access

                    // Euclidean distance squared — no sqrt needed since weight uses d²
                    double distSq = dx * dx + dz * dz + dy * dy;
                    double weight = 1.0 / (1.0 + CivilConfig.distanceAlphaSq * distSq);
                    double weightedDelta = delta * weight;

                    boolean inCore = Math.abs(dx) <= CivilConfig.coreRadiusX
                                  && Math.abs(dz) <= CivilConfig.coreRadiusZ
                                  && Math.abs(dy) <= CivilConfig.coreRadiusY;
                    if (inCore) {
                        entry.coreSum += weightedDelta;
                    } else {
                        entry.outerSum += weightedDelta;
                    }
                }
            }
        }

        if (CivilMod.DEBUG) {
            LOGGER.info("[civil-result-cache] delta propagated: shard={} delta={}", shardKey, String.format("%.4f", delta));
        }
    }

    // ========== Player presence ==========

    /**
     * Visit one result shard: refresh TTL and advance presenceTime if cached.
     * Used by {@link PlayerAwarePrefetcher} round-robin consumption (CFR 1.2.2).
     */
    public void visitAt(ServerLevel world, VoxelChunkKey vc, long serverNow) {
        String dim = world.dimension().identifier().toString();
        String k = key(world, vc);
        TimestampedEntry<ResultEntry> cached = cache.get(k);
        if (cached == null || cached.isExpired(ttlMillis)) {
            return;
        }
        cached.touch();
        ResultEntry re = cached.getValue();
        if (re.getRawScore(serverNow) <= CivilConfig.presenceRawEpsilon) {
            pendingPresenceWrites.remove(k);
            pendingPresenceDeleteKeys.put(k, Boolean.TRUE);
            return;
        }
        long oldPt = re.presenceTime;
        re.onPlayerNearby(serverNow);
        if (re.presenceTime != oldPt) {
            pendingPresenceDeleteKeys.remove(k);
            pendingPresenceWrites.put(k, new CivilStorage.PresenceSaveRequest(
                    dim, vc, re.presenceTime, re.lastRecoveryTime));
        }
    }

    /**
     * Visit result shards near a player: advance presenceTime AND refresh TTL.
     *
     * <p>This is the primary mechanism that prevents TTL eviction near online players.
     * Called from PlayerAwarePrefetcher bulk paths. Without the touch(),
     * entries would silently expire after 60min even with a player standing on them.
     */
    public void visitAround(ServerLevel world, VoxelChunkKey center, int radiusX, int radiusZ, int radiusY) {
        long serverNow = ServerClock.now();
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                for (int dy = -radiusY; dy <= radiusY; dy++) {
                    visitAt(world, center.offset(dx, dz, dy), serverNow);
                }
            }
        }
    }

    // ========== Presence persistence ==========

    /**
     * Drain pending presence writes for unified flush (merged into L1 region files on NBT path).
     */
    public List<CivilStorage.PresenceSaveRequest> drainPendingPresenceWrites() {
        ArrayList<CivilStorage.PresenceSaveRequest> snapshot = new ArrayList<>(pendingPresenceWrites.values());
        pendingPresenceWrites.clear();
        return snapshot;
    }

    public List<String> drainPendingPresenceDeleteKeys() {
        ArrayList<String> snapshot = new ArrayList<>(pendingPresenceDeleteKeys.keySet());
        pendingPresenceDeleteKeys.clear();
        return snapshot;
    }

    /**
     * Forward drained presence writes to storage (async batch).
     */
    public int flushPresence(CivilStorage storage) {
        var snapshot = drainPendingPresenceWrites();
        if (snapshot.isEmpty() || storage == null) return 0;
        storage.batchSavePresenceAsync(snapshot);
        if (CivilMod.DEBUG) {
            LOGGER.info("[civil-result-cache] flushed {} presenceTime entries", snapshot.size());
        }
        return snapshot.size();
    }

    // ========== Maintenance ==========

    /**
     * Evict expired entries. Called periodically from TtlCacheService.
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        var it = cache.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now - entry.getValue().getCreateTime() > ttlMillis) {
                it.remove();
            }
        }
        var partialIt = partialBackoffCache.entrySet().iterator();
        while (partialIt.hasNext()) {
            var entry = partialIt.next();
            if (partialExpired(now, entry.getValue())) {
                partialIt.remove();
            }
        }
    }

    // ========== Statistics ==========

    public int size() {
        return cache.size();
    }

    /**
     * Clear all result entries. Called on world shutdown to prevent cross-world cache contamination.
     */
    public void clearAll() {
        cache.clear();
        partialBackoffCache.clear();
        pendingPresenceWrites.clear();
        pendingPresenceDeleteKeys.clear();
    }
}
