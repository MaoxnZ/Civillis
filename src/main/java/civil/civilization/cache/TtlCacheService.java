package civil.civilization.cache;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.civilization.ServerClock;
import civil.config.CivilConfig;
import civil.civilization.storage.H2Storage;
import civil.civilization.VoxelChunkKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

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
    private final H2Storage storage;
    private final PlayerAwarePrefetcher prefetcher;
    private final LoadingStateTracker loadingTracker;

    private volatile boolean initialized = false;
    private int tickCounter = 0;

    public TtlCacheService() {
        this(CivilConfig.l1TtlMs);
    }

    public TtlCacheService(long l1TtlMillis) {
        this.cache = new TtlVoxelCache(l1TtlMillis);
        this.storage = new H2Storage();
        this.loadingTracker = cache.getLoadingTracker();
        this.prefetcher = new PlayerAwarePrefetcher(cache, storage);

        cache.setStorage(storage);
    }

    /**
     * Initialize the service (called on world load).
     */
    public void initialize(ServerWorld world) {
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

        // Flush dirty presenceTime to H2 every 30 seconds (600 ticks)
        if (tickCounter % 600 == 0) {
            var resultCache = civil.CivilServices.getResultCache();
            if (resultCache != null) {
                resultCache.flushPresence(storage);
            }
        }

        // Persist ServerClock periodically
        if (tickCounter % CivilConfig.clockPersistTicks == 0) {
            storage.saveServerClockAsync(ServerClock.now());
        }
    }

    /**
     * Shut down the service.
     */
    public void shutdown() {
        if (!initialized) return;

        try {
            LOGGER.info("[civil-cache-service] Shutting down...");

            // Flush dirty presenceTime before closing (synchronous save)
            var resultCache = civil.CivilServices.getResultCache();
            if (resultCache != null) {
                int flushed = resultCache.flushPresence(storage);
                LOGGER.info("[civil-cache-service] Shutdown: flushed {} presenceTime entries", flushed);
            }

            // Persist ServerClock
            storage.saveServerClockAsync(ServerClock.now());

            // Close H2
            storage.close();
            loadingTracker.clear();
            initialized = false;

            if (CivilMod.DEBUG) {
                LOGGER.info("[civil-cache-service] Shut down, ServerClock={}", ServerClock.now());
            }
        } catch (Exception e) {
            LOGGER.error("[civil-cache-service] Shutdown failed", e);
        }
    }

    // ========== CivilizationCache interface ==========

    @Override
    public Optional<CScore> getChunkCScore(ServerWorld level, VoxelChunkKey key) {
        return cache.getChunkCScore(level, key);
    }

    @Override
    public void putChunkCScore(ServerWorld level, VoxelChunkKey key, CScore cScore) {
        cache.putChunkCScore(level, key, cScore);
    }

    // invalidateChunk / markChunkDirtyAt removed — Fusion Architecture uses
    // immediate L1 recompute + delta propagation via onCivilBlockChanged().

    // ========== Accessors ==========

    public int l1Size() { return cache.l1Size(); }
    public boolean isInitialized() { return initialized; }
    public TtlVoxelCache getCache() { return cache; }
    public H2Storage getStorage() { return storage; }
}
