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
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
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
                LOGGER.info("[civil-storage-flush] regions={} scoreUps={} scoreDel={} presUps={} presDel={} heads={} anchors={} elapsed_ms={}",
                        regionKeys.size(), resolvedScoreUpserts.size(), resolvedScoreDeletes.size(),
                        resolvedPresenceUpserts.size(), resolvedPresenceDeletes.size(),
                        mobHeadsDirty ? 1 : 0, anchorsDirty ? 1 : 0, elapsedMs);
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
        try {
            region = storage.bulkLoadRegion(dim, rx, rz).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("[civil-cache-service] Bulk load region {} interrupted", regionKey);
            return Optional.empty();
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn("[civil-cache-service] Bulk load region {} failed: {}", regionKey, e.getMessage());
            return Optional.empty();
        }
        restoreRegionFromBulkLoad(level, dim, rx, rz, region);
        return cache.getChunkCScore(level, key);
    }

    /**
     * Restore a bulk-loaded region into cache and mark it activated.
     * Shared by getChunkCScore (per-key) and onChunkLoadPreFill (per-chunk).
     */
    private void restoreRegionFromBulkLoad(ServerLevel level, String dim, int rx, int rz,
            Map<VoxelChunkKey, L1Entry> region) {
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
    }

    /**
     * Per-chunk prefill on chunk load. Does one bulk load per region, then restores
     * all keys. For section keys not in cold storage, restores 0.0 when section is empty.
     */
    public void onChunkLoadPreFill(ServerLevel world, ChunkAccess chunk) {
        String dim = world.dimension().identifier().toString();
        int rx = Math.floorDiv(chunk.getPos().x, 32);
        int rz = Math.floorDiv(chunk.getPos().z, 32);
        String regionKey = dim + "|" + rx + "|" + rz;

        if (!activatedRegions.contains(regionKey)) {
            Map<VoxelChunkKey, L1Entry> region;
            try {
                region = storage.bulkLoadRegion(dim, rx, rz).get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("[civil-cache-service] Chunk load bulk region {} interrupted", regionKey);
                return;
            } catch (ExecutionException | TimeoutException e) {
                LOGGER.warn("[civil-cache-service] Chunk load bulk region {} failed: {}", regionKey, e.getMessage());
                return;
            }
            restoreRegionFromBulkLoad(world, dim, rx, rz, region);
        }

        LevelChunkSection[] sections = chunk.getSections();
        int bottomSy = Math.floorDiv(world.dimensionType().minY(), 16);
        long now = System.currentTimeMillis();

        for (int i = 0; i < sections.length; i++) {
            int sy = bottomSy + i;
            VoxelChunkKey key = new VoxelChunkKey(chunk.getPos().x, chunk.getPos().z, sy);

            if (cache.containsL1(world, key)) continue;

            LevelChunkSection section = sections[i];
            if (!section.maybeHas(civil.civilization.BlockScanner::isTargetBlock)) {
                cache.restoreL1(world, key, new CScore(0.0), now);
            }
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
     * Get presence for compute. Checks preload first (from bulk load), then storage.
     */
    public long[] getPresenceForCompute(String dim, VoxelChunkKey key) {
        String k = dim + "|" + key.getCx() + "|" + key.getCz() + "|" + key.getSy();
        long[] preload = presencePreload.get(k);
        if (preload != null) return preload;
        return storage.loadPresenceSync(dim, key);
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
