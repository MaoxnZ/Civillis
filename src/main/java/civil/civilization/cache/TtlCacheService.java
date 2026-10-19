package civil.civilization.cache;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.civilization.ServerClock;
import civil.config.CivilConfig;
import civil.civilization.storage.CivilStorage;
import civil.civilization.storage.CivilStorage.L1Entry;
import civil.civilization.storage.CivilStorage.PresenceSaveRequest;
import civil.civilization.storage.NbtStorage;
import civil.civilization.VoxelChunkKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

/**
 * Fusion Architecture: civilization cache service.
 *
 * <p>Manages L1 info shard cache lifecycle (TTL, NBT persistence, ServerClock).
 * L2/L3 layers have been retired; result shards are managed by {@link ResultCache}
 * via {@link civil.civilization.scoring.ScalableCivilizationService}.
 */
public final class TtlCacheService implements CivilizationCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-cache-service");

    private final TtlVoxelCache cache;
    private final CivilStorage storage;
    private final PlayerAwarePrefetcher prefetcher;
    private final PresenceKeepAliveSweep presenceKeepAliveSweep;
    private final LoadingStateTracker loadingTracker;

    private volatile boolean initialized = false;
    private int tickCounter = 0;

    /** Regions that were bulk-loaded; skip Cold read until flush invalidates. */
    private final Set<String> activatedRegions = ConcurrentHashMap.newKeySet();

    /** Presence preload from bulk load; key=dim|cx|cz|sy, value=[presenceTime, lastRecoveryTime]. */
    private final Map<String, long[]> presencePreload = new ConcurrentHashMap<>();

    public TtlCacheService() {
        this(CivilConfig.l1TtlMs);
    }

    public TtlCacheService(long l1TtlMillis) {
        this.cache = new TtlVoxelCache(l1TtlMillis);
        this.storage = new NbtStorage();
        this.loadingTracker = cache.getLoadingTracker();
        this.prefetcher = new PlayerAwarePrefetcher();
        this.presenceKeepAliveSweep = new PresenceKeepAliveSweep();

        cache.setStorage(storage);
    }

    /**
     * Initialize the service (called on world load).
     */
    public void initialize(ServerLevel world) {
        if (initialized) return;

        try {
            storage.initialize(world);

            // Restore ServerClock from civil_meta
            long savedClock = storage.loadServerClockMillis();
            ServerClock.load(savedClock);

            // Fusion Architecture: bulk restore L1 from storage (NBT may return empty)
            var allL1 = storage.loadAllL1();
            for (var entry : allL1) {
                cache.restoreL1(world, entry.key(), entry.cScore(), entry.createTime());
            }

            initialized = true;

            if (CivilMod.DEBUG) {
                LOGGER.info("[civil-cache-service] Initialized, ServerClock={} ms", savedClock);
            }
        } catch (Exception e) {
            LOGGER.error("[civil-cache-service] Initialization failed", e);
        }
    }

    /**
     * Per-tick maintenance.
     */
    public void onServerTick(MinecraftServer server) {
        if (!initialized) return;

        tickCounter++;

        // Advance ServerClock every tick (+50ms)
        ServerClock.tick();

        // CFR 1.2.2: prefetch queue + round-robin budget + epoch receipts (each logical tick)
        prefetcher.onServerTick(server);
        presenceKeepAliveSweep.onServerTick(server);

        if (CivilMod.DEBUG && tickCounter % 20 == 0) {
            var resultCache = civil.CivilServices.getResultCache();
            int resultSize = resultCache != null ? resultCache.size() : 0;
            LOGGER.info("[civil-ttl-stats] L1={} results={} prefetchQ={} serverClock={}",
                    cache.l1Size(), resultSize, prefetcher.getPendingQueueSize(), ServerClock.now());
        }

        // TTL cleanup every 5 seconds
        if (tickCounter % 100 == 0) {
            cache.cleanupExpired();

            var resultCache = civil.CivilServices.getResultCache();
            if (resultCache != null) {
                resultCache.cleanupExpired();
            }
        }

        // Unified flush every 30 seconds (600 ticks)
        if (tickCounter % 600 == 0) {
            runUnifiedFlush(false);
        }
    }

    /**
     * Shut down the service.
     */
    public void shutdown() {
        if (!initialized) return;

        try {
            LOGGER.info("[civil-cache-service] Shutting down...");

            CompletableFuture<Void> flush = runUnifiedFlush(true);
            try {
                flush.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOGGER.warn("[civil-cache-service] Flush did not complete in time: {}", e.getMessage());
            }

            storage.close();
            cache.clearAll();
            var resultCache = civil.CivilServices.getResultCache();
            if (resultCache != null) {
                resultCache.clearAll();
            }
            loadingTracker.clear();
            activatedRegions.clear();
            presencePreload.clear();
            prefetcher.clear();
            presenceKeepAliveSweep.clear();
            initialized = false;

            if (CivilMod.DEBUG) {
                LOGGER.info("[civil-cache-service] Shut down, ServerClock={}", ServerClock.now());
            }
        } catch (Exception e) {
            LOGGER.error("[civil-cache-service] Shutdown failed", e);
        }
    }

    /**
     * Run unified flush: meta + structure + L1 regions (CFR: score/presence upserts + deletes, prune empty rows).
     */
    private CompletableFuture<Void> runUnifiedFlush(boolean shutdown) {
        final long serverClockMillis = civil.civilization.ServerClock.now();
        final Map<String, CScore> pendingScores = cache.drainPendingScoreWrites();
        final List<String> pendingScoreDeletes = cache.drainPendingScoreDeleteKeys();
        var resultCache = civil.CivilServices.getResultCache();
        final List<PresenceSaveRequest> pendingPresence =
                resultCache != null ? resultCache.drainPendingPresenceWrites() : List.of();
        final List<String> pendingPresenceDeletes =
                resultCache != null ? resultCache.drainPendingPresenceDeleteKeys() : List.of();
        final boolean mobHeadsDirty;
        final List<CivilStorage.StoredMobHead> mobHeadsSnapshot;
        final var headTracker = civil.CivilServices.getHeadTracker();
        if (headTracker != null && headTracker.isMobHeadsDirty()) {
            mobHeadsDirty = true;
            mobHeadsSnapshot = headTracker.snapshotAllHeads();
        } else {
            mobHeadsDirty = false;
            mobHeadsSnapshot = List.of();
        }
        final boolean anchorsDirty;
        final List<CivilStorage.StoredUndyingAnchor> anchorsSnapshot;
        final var anchorTracker = civil.CivilServices.getUndyingAnchorTracker();
        if (anchorTracker != null && anchorTracker.isAnchorsDirty()) {
            anchorsDirty = true;
            anchorsSnapshot = anchorTracker.snapshotAllAnchors();
        } else {
            anchorsDirty = false;
            anchorsSnapshot = List.of();
        }
        final boolean shrinesDirty;
        final List<CivilStorage.StoredFarmShrine> shrinesSnapshot;
        final var farmShrineTracker = civil.CivilServices.getFarmShrineTracker();
        if (farmShrineTracker != null && farmShrineTracker.isShrinesDirty()) {
            shrinesDirty = true;
            shrinesSnapshot = farmShrineTracker.snapshotAllShrines();
        } else {
            shrinesDirty = false;
            shrinesSnapshot = List.of();
        }

        return storage.submitOnIO(() -> {
            long flushStartMs = System.currentTimeMillis();
            storage.writeMeta(serverClockMillis);
            if (mobHeadsDirty) {
                storage.writeMobHeads(mobHeadsSnapshot);
                if (headTracker != null) headTracker.clearMobHeadsDirty();
            }
            if (anchorsDirty) {
                storage.writeUndyingAnchors(anchorsSnapshot);
                if (anchorTracker != null) anchorTracker.clearAnchorsDirty();
            }
            if (shrinesDirty) {
                storage.writeFarmShrines(shrinesSnapshot);
                if (farmShrineTracker != null) farmShrineTracker.clearShrinesDirty();
            }

            Map<String, CScore> resolvedScoreUpserts = new HashMap<>(pendingScores);
            Set<String> resolvedScoreDeletes = new HashSet<>(pendingScoreDeletes);
            for (String key : resolvedScoreDeletes) {
                resolvedScoreUpserts.remove(key);
            }

            Map<String, PresenceSaveRequest> resolvedPresenceUpserts = new HashMap<>();
            for (PresenceSaveRequest req : pendingPresence) {
                resolvedPresenceUpserts.put(shardKey(req.dim(), req.key()), req);
            }
            Set<String> resolvedPresenceDeletes = new HashSet<>(pendingPresenceDeletes);
            for (String key : resolvedPresenceDeletes) {
                resolvedPresenceUpserts.remove(key);
            }

            Set<String> regionKeys = new HashSet<>();
            Map<String, Map<VoxelChunkKey, CScore>> scoreUpsertsByRegion = new HashMap<>();
            Map<String, Set<VoxelChunkKey>> scoreDeletesByRegion = new HashMap<>();
            Map<String, Map<VoxelChunkKey, PresenceSaveRequest>> presenceUpsertsByRegion = new HashMap<>();
            Map<String, Set<VoxelChunkKey>> presenceDeletesByRegion = new HashMap<>();

            for (Map.Entry<String, CScore> e : resolvedScoreUpserts.entrySet()) {
                ParsedShardKey p = parseShardKey(e.getKey());
                if (p == null) continue;
                int rrx = Math.floorDiv(p.cx(), 32);
                int rrz = Math.floorDiv(p.cz(), 32);
                String rk = regionKey(p.dim(), rrx, rrz);
                regionKeys.add(rk);
                scoreUpsertsByRegion.computeIfAbsent(rk, x -> new HashMap<>())
                        .put(new VoxelChunkKey(p.cx(), p.cz(), p.sy()), e.getValue());
            }
            for (String key : resolvedScoreDeletes) {
                ParsedShardKey p = parseShardKey(key);
                if (p == null) continue;
                int rrx = Math.floorDiv(p.cx(), 32);
                int rrz = Math.floorDiv(p.cz(), 32);
                String rk = regionKey(p.dim(), rrx, rrz);
                regionKeys.add(rk);
                scoreDeletesByRegion.computeIfAbsent(rk, x -> new HashSet<>())
                        .add(new VoxelChunkKey(p.cx(), p.cz(), p.sy()));
            }
            for (PresenceSaveRequest req : resolvedPresenceUpserts.values()) {
                int rrx = Math.floorDiv(req.key().getCx(), 32);
                int rrz = Math.floorDiv(req.key().getCz(), 32);
                String rk = regionKey(req.dim(), rrx, rrz);
                regionKeys.add(rk);
                presenceUpsertsByRegion.computeIfAbsent(rk, x -> new HashMap<>()).put(req.key(), req);
            }
            for (String key : resolvedPresenceDeletes) {
                ParsedShardKey p2 = parseShardKey(key);
                if (p2 == null) continue;
                int rrx = Math.floorDiv(p2.cx(), 32);
                int rrz = Math.floorDiv(p2.cz(), 32);
                String rk = regionKey(p2.dim(), rrx, rrz);
                regionKeys.add(rk);
                presenceDeletesByRegion.computeIfAbsent(rk, x -> new HashSet<>())
                        .add(new VoxelChunkKey(p2.cx(), p2.cz(), p2.sy()));
            }

            for (String rk3 : regionKeys) {
                String[] parts = rk3.split("\\|", 3);
                if (parts.length != 3) continue;
                String dim = parts[0];
                int rx;
                int rz;
                try {
                    rx = Integer.parseInt(parts[1]);
                    rz = Integer.parseInt(parts[2]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                Map<VoxelChunkKey, L1Entry> data = new HashMap<>(storage.loadL1RegionSync(dim, rx, rz));

                Map<VoxelChunkKey, CScore> scoreUpserts = scoreUpsertsByRegion.getOrDefault(rk3, Map.of());
                for (Map.Entry<VoxelChunkKey, CScore> e : scoreUpserts.entrySet()) {
                    VoxelChunkKey vk = e.getKey();
                    L1Entry l1Entry = data.getOrDefault(vk, new L1Entry(0.0, 0L, 0L));
                    data.put(vk, new L1Entry(e.getValue().score(), l1Entry.presenceTime(), l1Entry.lastRecoveryTime()));
                }
                Set<VoxelChunkKey> scoreDeletes = scoreDeletesByRegion.getOrDefault(rk3, Set.of());
                for (VoxelChunkKey vk : scoreDeletes) {
                    L1Entry l1Entry = data.getOrDefault(vk, new L1Entry(0.0, 0L, 0L));
                    data.put(vk, new L1Entry(0.0, l1Entry.presenceTime(), l1Entry.lastRecoveryTime()));
                }
                Map<VoxelChunkKey, PresenceSaveRequest> presUps = presenceUpsertsByRegion.getOrDefault(rk3, Map.of());
                for (Map.Entry<VoxelChunkKey, PresenceSaveRequest> e : presUps.entrySet()) {
                    VoxelChunkKey vk = e.getKey();
                    PresenceSaveRequest req = e.getValue();
                    L1Entry prev = data.getOrDefault(vk, new L1Entry(0.0, 0L, 0L));
                    data.put(vk, new L1Entry(prev.score(), req.presenceTime(), req.lastRecoveryTime()));
                }
                Set<VoxelChunkKey> presDeletes = presenceDeletesByRegion.getOrDefault(rk3, Set.of());
                for (VoxelChunkKey vk : presDeletes) {
                    L1Entry prev = data.getOrDefault(vk, new L1Entry(0.0, 0L, 0L));
                    data.put(vk, new L1Entry(prev.score(), 0L, 0L));
                }
                data.entrySet().removeIf(e -> {
                    L1Entry v = e.getValue();
                    return v.score() == 0.0 && v.presenceTime() == 0L && v.lastRecoveryTime() == 0L;
                });
                storage.writeL1Region(dim, rx, rz, data);
                deactivateRegion(dim, rx, rz);
            }

            if (CivilMod.DEBUG) {
                long elapsedMs = System.currentTimeMillis() - flushStartMs;
                LOGGER.info("[civil-storage-flush] regions={} scoreUps={} scoreDel={} presUps={} presDel={} heads={} anchors={} shrines={} elapsed_ms={}",
                        regionKeys.size(), resolvedScoreUpserts.size(), resolvedScoreDeletes.size(),
                        resolvedPresenceUpserts.size(), resolvedPresenceDeletes.size(),
                        mobHeadsDirty ? 1 : 0, anchorsDirty ? 1 : 0, shrinesDirty ? 1 : 0, elapsedMs);
            }
        });
    }

    private record ParsedShardKey(String dim, int cx, int cz, int sy) {}

    private static ParsedShardKey parseShardKey(String key) {
        String[] parts = key.split("\\|", 4);
        if (parts.length != 4) return null;
        try {
            return new ParsedShardKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String shardKey(String dim, VoxelChunkKey key) {
        return dim + "|" + key.getCx() + "|" + key.getCz() + "|" + key.getSy();
    }

    private static String regionKey(String dim, int rx, int rz) {
        return dim + "|" + rx + "|" + rz;
    }

    // ========== CivilizationCache interface ==========

    @Override
    public Optional<CScore> getChunkCScore(ServerLevel level, VoxelChunkKey key) {
        Optional<CScore> hit = cache.getChunkCScore(level, key);
        if (hit.isPresent()) return hit;

        // Hot miss: try bulk load (if not activated)
        String dim = level.dimension().identifier().toString();
        int rx = Math.floorDiv(key.getCx(), 32);
        int rz = Math.floorDiv(key.getCz(), 32);
        String regionKey = dim + "|" + rx + "|" + rz;
        if (activatedRegions.contains(regionKey)) return Optional.empty();

        Map<VoxelChunkKey, L1Entry> region;
        long bulkLoadWaitNanos;
        try {
            long t0 = System.nanoTime();
            region = storage.bulkLoadRegion(dim, rx, rz).get(5, TimeUnit.SECONDS);
            bulkLoadWaitNanos = System.nanoTime() - t0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("[civil-cache-service] Bulk load region {} interrupted", regionKey);
            return Optional.empty();
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn("[civil-cache-service] Bulk load region {} failed: {}", regionKey, e.getMessage());
            return Optional.empty();
        }
        restoreRegionFromBulkLoad(level, dim, rx, rz, region, "l1_miss", bulkLoadWaitNanos);
        return cache.getChunkCScore(level, key);
    }

    /**
     * Restore a bulk-loaded region into cache and mark it activated.
     * Invoked from {@link #getChunkCScore} (L1 hot miss) or {@link #getPresenceForCompute}
     * (presence cold path) when the region was not yet activated.
     *
     * @param trigger       {@code l1_miss} or {@code presence_miss}; used only for DEBUG logging
     * @param bulkLoadWaitNs wall time spent in {@code bulkLoadRegion(...).get(...)} (IO queue + disk)
     */
    private void restoreRegionFromBulkLoad(ServerLevel level, String dim, int rx, int rz,
            Map<VoxelChunkKey, L1Entry> region, String trigger, long bulkLoadWaitNs) {
        long now = System.currentTimeMillis();
        for (Map.Entry<VoxelChunkKey, L1Entry> e : region.entrySet()) {
            VoxelChunkKey k = e.getKey();
            L1Entry v = e.getValue();
            cache.restoreL1(level, k, new CScore(v.score()), now);
            if (v.presenceTime() != 0 || v.lastRecoveryTime() != 0) {
                presencePreload.put(dim + "|" + k.getCx() + "|" + k.getCz() + "|" + k.getSy(),
                        new long[] { v.presenceTime(), v.lastRecoveryTime() });
            }
        }
        String regionKey = dim + "|" + rx + "|" + rz;
        activatedRegions.add(regionKey);

        if (CivilMod.DEBUG) {
            int entries = region.size();
            int withScore = 0;
            int withPresence = 0;
            for (L1Entry v : region.values()) {
                if (v.score() != 0.0) withScore++;
                if (v.presenceTime() != 0L || v.lastRecoveryTime() != 0L) withPresence++;
            }
            long loadWaitMs = TimeUnit.NANOSECONDS.toMillis(bulkLoadWaitNs);
            LOGGER.info("[civil-region-bulk-load] trigger={} dim={} rx={} rz={} entries={} withScore={} withPresence={} load_wait_ms={}",
                    trigger, dim, rx, rz, entries, withScore, withPresence, loadWaitMs);
        }
    }

    @Override
    public void putChunkCScore(ServerLevel level, VoxelChunkKey key, CScore cScore) {
        cache.putChunkCScore(level, key, cScore);
    }

    // invalidateChunk / markChunkDirtyAt removed — Fusion Architecture uses
    // immediate L1 recompute + delta propagation via onCivilBlockChanged().

    // ========== Accessors ==========

    public int l1Size() { return cache.l1Size(); }
    public boolean isInitialized() { return initialized; }
    public TtlVoxelCache getCache() { return cache; }
    public CivilStorage getStorage() { return storage; }

    /** CFR: clear prefetcher state when a player disconnects. */
    public void onPlayerLeave(UUID playerId) {
        prefetcher.removePlayer(playerId);
    }

    /**
     * Get presence for compute, using a three-level lookup to avoid spurious NBT reads.
     *
     * <ol>
     *   <li><b>pendingPresenceWrites</b> (ResultCache): in-flight updates from {@code visitAt}
     *       that have not yet been flushed to NBT. These are more recent than anything on disk
     *       and must take priority to avoid regressing presenceTime on ResultEntry rebuild.</li>
     *   <li><b>presencePreload</b>: populated when this region was last bulk-loaded. Valid until
     *       the region is deactivated on the next flush.</li>
     *   <li><b>activation-gated bulk restore</b>: if the region is not yet activated, trigger a
     *       full {@code restoreRegionFromBulkLoad} (same path as {@link #getChunkCScore}).
     *       This atomically restores both L1 scores and the entire region's presence data,
     *       marks the region activated, and then returns from presencePreload.</li>
     * </ol>
     *
     * <p>If the region is already activated but a key is absent from presencePreload, that
     * means the VC had no persisted presence (e.g. never visited or tombstoned on last flush).
     * In that case {@code null} is returned and a fresh presenceTime will be assigned.
     *
     * @param level the server level (needed to restore L1 cache on cold bulk load)
     * @param dim   dimension string
     * @param key   voxel chunk key
     */
    public long[] getPresenceForCompute(ServerLevel level, String dim, VoxelChunkKey key) {
        String shardKey = dim + "|" + key.getCx() + "|" + key.getCz() + "|" + key.getSy();

        // 1. Check in-flight pending presence (ResultCache) — most recent, pre-flush
        ResultCache resultCache = civil.CivilServices.getResultCache();
        if (resultCache != null) {
            long[] pending = resultCache.getPendingPresenceTime(shardKey);
            if (pending != null) return pending;
        }

        // 2. Check presencePreload — populated by last bulk restore for this region
        long[] preload = presencePreload.get(shardKey);
        if (preload != null) return preload;

        // 3. Region not activated: trigger full bulk restore (same gate as getChunkCScore)
        int rx = Math.floorDiv(key.getCx(), 32);
        int rz = Math.floorDiv(key.getCz(), 32);
        String regionKey = dim + "|" + rx + "|" + rz;
        if (activatedRegions.contains(regionKey)) {
            // Activated but key absent from preload → this VC had no persisted presence
            return null;
        }

        Map<VoxelChunkKey, L1Entry> region;
        long bulkLoadWaitNanos;
        try {
            long t0 = System.nanoTime();
            region = storage.bulkLoadRegion(dim, rx, rz).get(5, TimeUnit.SECONDS);
            bulkLoadWaitNanos = System.nanoTime() - t0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("[civil-cache-service] Bulk load region {} for presence interrupted", regionKey);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn("[civil-cache-service] Bulk load region {} for presence failed: {}", regionKey, e.getMessage());
            return null;
        }
        restoreRegionFromBulkLoad(level, dim, rx, rz, region, "presence_miss", bulkLoadWaitNanos);
        return presencePreload.get(shardKey);
    }

    /** Remove region from activated set (call when flush writes that region). Phase 4. */
    public void deactivateRegion(String dim, int rx, int rz) {
        String regionKey = dim + "|" + rx + "|" + rz;
        activatedRegions.remove(regionKey);
        // Clear presence preload for keys in this region
        int minCx = rx * 32, maxCx = rx * 32 + 31;
        int minCz = rz * 32, maxCz = rz * 32 + 31;
        presencePreload.keySet().removeIf(s -> {
            String[] parts = s.split("\\|");
            if (parts.length != 4) return false;
            try {
                int cx = Integer.parseInt(parts[1]);
                int cz = Integer.parseInt(parts[2]);
                return cx >= minCx && cx <= maxCx && cz >= minCz && cz <= maxCz;
            } catch (NumberFormatException e) { return false; }
        });
    }
}
