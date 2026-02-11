package civil.civilization.cache;

import civil.CivilMod;
import civil.civilization.ServerClock;
import civil.config.CivilConfig;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import civil.civilization.storage.H2Storage;

/**
 * Fusion Architecture result shard cache: pre-aggregated civilization scores per Voxel Chunk.
 *
 * <p>Provides O(1) spawn-check queries by caching the weighted sum of all L1 shards
 * within the detection range. Entries are lazily computed on first access and incrementally
 * updated via delta propagation when blocks change.
 *
 * <p>Not persisted to H2 — result shards are pure derived data from L1 shards and can be
 * recomputed in ~34μs from cached L1 scores.
 *
 * @see ResultEntry
 */
public final class ResultCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-result-cache");

    private final ConcurrentHashMap<String, TimestampedEntry<ResultEntry>> cache = new ConcurrentHashMap<>();
    private final long ttlMillis;

    /**
     * Write-back buffer for presenceTime persistence.
     *
     * <p>When {@link #visitAround} advances a ResultEntry's presenceTime,
     * the new value is staged here. Every 30 seconds (and on shutdown),
     * {@link #flushPresence} drains this buffer into H2 via batch UPDATE.
     *
     * <p><b>This is NOT related to cache invalidation or delta propagation.</b>
     * It is purely an I/O batching mechanism to avoid per-second H2 writes.
     */
    private final ConcurrentHashMap<String, H2Storage.PresenceSaveRequest> pendingPresenceWrites = new ConcurrentHashMap<>();

    public ResultCache() {
        this(CivilConfig.resultTtlMs);
    }

    public ResultCache(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    // ========== Cache key ==========

    private static String key(ServerWorld world, VoxelChunkKey vc) {
        return world.getRegistryKey().toString() + "|" + vc.getCx() + "|" + vc.getCz() + "|" + vc.getSy();
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
    public ResultEntry getOrCompute(ServerWorld world, VoxelChunkKey centerVC,
                                    BiFunction<ServerWorld, VoxelChunkKey, ResultEntry> computer) {
        String k = key(world, centerVC);
        TimestampedEntry<ResultEntry> cached = cache.get(k);

        if (cached != null && !cached.isExpired(ttlMillis)) {
            ResultEntry entry = cached.getValue();
            if (entry.isConfigValid()) {
                cached.touch();
                return entry; // O(1) hit
            }
            // Config mismatch → recompute (preserve presenceTime)
            ResultEntry fresh = computer.apply(world, centerVC);
            fresh.presenceTime = entry.presenceTime;
            fresh.lastRecoveryTime = entry.lastRecoveryTime;
            cache.put(k, new TimestampedEntry<>(fresh));
            return fresh;
        }

        // Miss → compute
        ResultEntry entry = computer.apply(world, centerVC);
        cache.put(k, new TimestampedEntry<>(entry));

        // Stage initial presenceTime for H2 persistence.
        // Without this, entries outside the patrol radius would never have their
        // presenceTime saved to H2.  On TTL eviction (60 min) + cache miss,
        // computeResultEntry would find no H2 data and reset presenceTime to now(),
        // perpetually restarting the grace period → decay never takes effect.
        pendingPresenceWrites.putIfAbsent(k, new H2Storage.PresenceSaveRequest(
                world.getRegistryKey().toString(), centerVC,
                entry.presenceTime, entry.lastRecoveryTime));

        return entry;
    }

    /**
     * Get existing result entry without computing (for delta propagation).
     *
     * @return the entry, or null if not cached / expired
     */
    public ResultEntry getIfPresent(ServerWorld world, VoxelChunkKey vc) {
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
     * Visit result shards near a player: advance presenceTime AND refresh TTL.
     *
     * <p>This is the primary mechanism that prevents TTL eviction near online players.
     * Called once per second from PlayerAwarePrefetcher. Without the touch(),
     * entries would silently expire after 60min even with a player standing on them.
     */
    public void visitAround(ServerWorld world, VoxelChunkKey center, int radiusX, int radiusZ, int radiusY) {
        long serverNow = ServerClock.now();
        String dim = world.getRegistryKey().toString();
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                for (int dy = -radiusY; dy <= radiusY; dy++) {
                    VoxelChunkKey vc = center.offset(dx, dz, dy);
                    String k = key(world, vc);
                    TimestampedEntry<ResultEntry> cached = cache.get(k);
                    if (cached != null && !cached.isExpired(ttlMillis)) {
                        cached.touch();                       // refresh TTL timer
                        ResultEntry re = cached.getValue();
                        long oldPt = re.presenceTime;
                        re.onPlayerNearby(serverNow);         // advance presenceTime
                        if (re.presenceTime != oldPt) {
                            // presenceTime changed → stage for next H2 flush
                            pendingPresenceWrites.put(k, new H2Storage.PresenceSaveRequest(
                                    dim, vc, re.presenceTime, re.lastRecoveryTime));
                        }
                    }
                }
            }
        }
    }

    // ========== Presence persistence ==========

    /**
     * Flush dirty presenceTime entries to H2.
     * Called every 30 seconds from TtlCacheService + on shutdown.
     *
     * @return number of entries flushed
     */
    /**
     * Drain the write-back buffer and persist pending presenceTime values to H2.
     * Called every 30 seconds from TtlCacheService + on shutdown.
     *
     * @return number of entries written
     */
    public int flushPresence(H2Storage storage) {
        if (pendingPresenceWrites.isEmpty() || storage == null) return 0;

        // Snapshot and clear atomically (ConcurrentHashMap — values() is weakly consistent,
        // but clear() after snapshot is fine: worst case a write appears in the next batch)
        var snapshot = new ArrayList<>(pendingPresenceWrites.values());
        pendingPresenceWrites.clear();

        storage.batchSavePresenceAsync(snapshot);

        if (CivilMod.DEBUG) {
            LOGGER.info("[civil-result-cache] flushed {} presenceTime entries to H2", snapshot.size());
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
    }

    // ========== Statistics ==========

    public int size() {
        return cache.size();
    }
}
