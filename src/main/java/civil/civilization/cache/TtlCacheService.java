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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fusion Architecture: civilization cache service.
 *
 * <p>Manages L1 info shard cache lifecycle (TTL, H2 persistence, ServerClock).
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
        this.prefetcher = new PlayerAwarePrefetcher(cache, storage);

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

            // Fusion Architecture: bulk restore L1 from H2
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

        // Player-aware result shard visits (for decay recovery)
        if (tickCounter % 20 == 0) {
            prefetcher.prefetchTick(server);

            if (CivilMod.DEBUG) {
                var resultCache = civil.CivilServices.getResultCache();
                int resultSize = resultCache != null ? resultCache.size() : 0;
                LOGGER.info("[civil-ttl-stats] L1={} results={} serverClock={}",
                        cache.l1Size(), resultSize, ServerClock.now());
            }
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
     * Run unified flush: meta + structure + L1 regions. Non-blocking unless shutdown.
     */
    private CompletableFuture<Void> runUnifiedFlush(boolean shutdown) {
        final long serverClockMillis = civil.civilization.ServerClock.now();
        final Map<String, CScore> pendingScores = cache.drainPendingScoreWrites();
        final List<PresenceSaveRequest> pendingPresence;
        var resultCache = civil.CivilServices.getResultCache();
        pendingPresence = resultCache != null ? resultCache.drainPendingPresenceWrites() : List.of();
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
            // Collect region keys from both pendingScores and pendingPresence
            Map<String, int[]> regionKeys = new HashMap<>();
            for (String k : pendingScores.keySet()) {
                String[] p = k.split("\\|", 4);
                if (p.length != 4) continue;
                try {
                    int cx = Integer.parseInt(p[1]), cz = Integer.parseInt(p[2]);
                    String rk = p[0] + "|" + Math.floorDiv(cx, 32) + "|" + Math.floorDiv(cz, 32);
                    regionKeys.put(rk, new int[] { Math.floorDiv(cx, 32), Math.floorDiv(cz, 32) });
                } catch (NumberFormatException ignored) {}
            }
            for (PresenceSaveRequest r : pendingPresence) {
                int rx = Math.floorDiv(r.key().getCx(), 32);
                int rz = Math.floorDiv(r.key().getCz(), 32);
                String rk = r.dim() + "|" + rx + "|" + rz;
                regionKeys.put(rk, new int[] { rx, rz });
            }
            for (Map.Entry<String, int[]> re : regionKeys.entrySet()) {
                String[] p = re.getKey().split("\\|", -1);
                if (p.length < 3) continue;
                String dim = p[0];
                int rx = re.getValue()[0], rz = re.getValue()[1];
                Map<VoxelChunkKey, L1Entry> data = new HashMap<>(storage.loadL1RegionSync(dim, rx, rz));
                for (Map.Entry<String, CScore> e : pendingScores.entrySet()) {
                    String k = e.getKey();
                    String[] parts = k.split("\\|", 4);
                    if (parts.length != 4) continue;
                    try {
                        if (!parts[0].equals(dim)) continue;
                        int cx = Integer.parseInt(parts[1]);
                        int cz = Integer.parseInt(parts[2]);
                        int sy = Integer.parseInt(parts[3]);
                        if (Math.floorDiv(cx, 32) != rx || Math.floorDiv(cz, 32) != rz) continue;
                        VoxelChunkKey vk = new VoxelChunkKey(cx, cz, sy);
                        L1Entry prev = data.getOrDefault(vk, new L1Entry(0, 0, 0));
                        data.put(vk, new L1Entry(e.getValue().score(), prev.presenceTime(), prev.lastRecoveryTime()));
                    } catch (NumberFormatException ignored) {}
                }
                for (PresenceSaveRequest r : pendingPresence) {
                    if (!r.dim().equals(dim)) continue;
                    if (Math.floorDiv(r.key().getCx(), 32) != rx || Math.floorDiv(r.key().getCz(), 32) != rz) continue;
                    VoxelChunkKey vk = r.key();
                    L1Entry prev = data.getOrDefault(vk, new L1Entry(0, 0, 0));
                    data.put(vk, new L1Entry(prev.score(), r.presenceTime(), r.lastRecoveryTime()));
                }
                storage.writeL1Region(dim, rx, rz, data);
                deactivateRegion(dim, rx, rz);
            }
            if (CivilMod.DEBUG) {
                long elapsedMs = System.currentTimeMillis() - flushStartMs;
                LOGGER.info("[civil-storage-flush] regions={} scores={} presence={} heads={} anchors={} elapsed_ms={}",
                        regionKeys.size(), pendingScores.size(), pendingPresence.size(),
                        mobHeadsDirty ? 1 : 0, anchorsDirty ? 1 : 0, elapsedMs);
            }
        });
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
