package civil.civilization.cache;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.civilization.ServerClock;
import civil.config.CivilConfig;
import civil.civilization.storage.H2Storage;
import civil.civilization.structure.L2Entry;
import civil.civilization.structure.L2Key;
import civil.civilization.structure.L3Entry;
import civil.civilization.structure.L3Key;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Civilization cache service: unified entry point integrating TTL cache, H2 storage,
 * gradual decay, and player-aware prefetching.
 * 
 * <p>Core features:
 * <ul>
 *   <li>TTL hot cache for memory management (30-minute expiry)</li>
 *   <li>Gradual decay via presenceTime + ServerClock (replaces hard 24h TTL cutoff)</li>
 *   <li>Async persistence without blocking the main thread</li>
 *   <li>Conservative estimate (max civilization value) when data is not yet loaded</li>
 *   <li>Player position-aware prefetching</li>
 * </ul>
 * 
 * <p>Usage:
 * <ol>
 *   <li>Call {@link #initialize(ServerWorld)} to initialize</li>
 *   <li>Call {@link #onServerTick(MinecraftServer)} per tick for maintenance</li>
 *   <li>Call {@link #shutdown()} to shut down</li>
 * </ol>
 */
public final class TtlCacheService implements CivilizationCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-cache-service");

    /**
     * Default score for unloaded L2/L3 regions.
     * With H2 persistence and gradual decay, never-scanned areas have no civilization (0.0).
     * During async loading, this value is returned; once loaded, the real score takes effect.
     */
    public static final double UNLOADED_DEFAULT_SCORE = 0.0;

    private final TtlPyramidCache cache;
    private final H2Storage storage;
    private final PlayerAwarePrefetcher prefetcher;
    private final LoadingStateTracker loadingTracker;

    private volatile boolean initialized = false;
    private int tickCounter = 0;

    public TtlCacheService() {
        this(CivilConfig.hotCacheTtlMs);
    }

    public TtlCacheService(long ttlMillis) {
        this.cache = new TtlPyramidCache(ttlMillis);
        this.storage = new H2Storage();
        this.loadingTracker = cache.getLoadingTracker();
        this.prefetcher = new PlayerAwarePrefetcher(cache, storage);

        cache.setStorage(storage);
    }

    /**
     * Initialize the service (called on world load).
     * Also restores the {@link ServerClock} from persistent storage.
     */
    public void initialize(ServerWorld world) {
        if (initialized) {
            return;
        }

        try {
            storage.initialize(world);

            // Restore ServerClock from civil_meta (0 for fresh worlds / upgrades)
            long savedClock = storage.loadServerClockMillis();
            ServerClock.load(savedClock);

            initialized = true;

            if (CivilMod.DEBUG) {
                LOGGER.info("[civil-cache-service] Service initialized, ServerClock restored to {} ms", savedClock);
            }
        } catch (Exception e) {
            LOGGER.error("[civil-cache-service] Service initialization failed", e);
        }
    }

    // Clock persist interval is in CivilConfig.clockPersistTicks

    /**
     * Per-tick maintenance method.
     */
    public void onServerTick(MinecraftServer server) {
        if (!initialized) return;

        tickCounter++;

        // Advance ServerClock every tick (+50ms)
        ServerClock.tick();

        // Consume prefetcher's pending queue every tick (main-thread safe)
        prefetcher.consumePendingRestores(server);

        // Execute prefetch every second (20 ticks = 1 second)
        if (tickCounter % 20 == 0) {
            prefetcher.prefetchTick(server);
            
            // Output cache status stats (for visualization)
            if (CivilMod.DEBUG) {
                LOGGER.info("[civil-ttl-stats] L1={} L2={} L3={} pending={} serverClock={}",
                        cache.l1Size(), cache.l2Size(), cache.l3Size(),
                        prefetcher.getPendingQueueSize(), ServerClock.now());
            }
        }

        // Execute TTL cleanup every 5 seconds (100 ticks)
        if (tickCounter % 100 == 0) {
            cache.cleanupExpired();
        }

        // Persist ServerClock every 5 minutes (crash recovery)
        if (tickCounter % CivilConfig.clockPersistTicks == 0) {
            storage.saveServerClockAsync(ServerClock.now());
        }
    }

    /**
     * Shut down the service (called on world unload).
     * Persists all L2/L3 entries and ServerClock to cold storage before closing the database connection.
     */
    public void shutdown() {
        if (!initialized) return;

        try {
            // Full persistence of L2/L3 to cold storage
            LOGGER.info("[civil-cache-service] Persisting cache...");
            cache.persistAllL2();
            cache.persistAllL3();

            // Persist ServerClock so next session resumes from the same point
            storage.saveServerClockAsync(ServerClock.now());
            
            // Wait for async writes to complete, then close
            storage.close();
            loadingTracker.clear();
            initialized = false;

            if (CivilMod.DEBUG) {
                LOGGER.info("[civil-cache-service] Service shut down, ServerClock={}", ServerClock.now());
            }
        } catch (Exception e) {
            LOGGER.error("[civil-cache-service] Service shutdown failed", e);
        }
    }

    // ========== CivilizationCache interface implementation ==========

    @Override
    public Optional<CScore> getChunkCScore(ServerWorld level, VoxelChunkKey key) {
        return cache.getChunkCScore(level, key);
    }

    @Override
    public void putChunkCScore(ServerWorld level, VoxelChunkKey key, CScore cScore) {
        cache.putChunkCScore(level, key, cScore);
    }

    @Override
    public void invalidateChunk(ServerWorld level, VoxelChunkKey key) {
        cache.invalidateChunk(level, key);
    }

    @Override
    public void markChunkDirtyAt(ServerWorld level, BlockPos pos) {
        cache.markChunkDirtyAt(level, pos);
    }

    // ========== Query service (with conservative estimates) ==========

    // L1 queries use CivilizationCache.getChunkCScore() directly
    // L1: no cold storage; hot cache misses are computed synchronously by the caller

    /**
     * Query L2 score (with status).
     * 
     * <p>L2 uses cold storage. Return value status:
     * <ul>
     *   <li>PRECISE - hot cache hit, uses exact value</li>
     *   <li>LOADING - async loading in progress, returns 0 (unscanned = no civilization)</li>
     * </ul>
     */
    public QueryResult queryL2Score(ServerWorld level, L2Key key, ScoreComputer scoreComputer) {
        Optional<L2Entry> cached = cache.getL2Entry(level, key, scoreComputer);
        if (cached.isPresent()) {
            L2Entry entry = cached.get();
            return QueryResult.precise(entry.getCoarseScore(), entry.getPresenceTime());
        }

        String loadKey = "l2:" + level.getRegistryKey().toString() + ":" 
                + key.getC2x() + ":" + key.getC2z() + ":" + key.getS2y();

        // Check if already loading
        if (loadingTracker.isLoading(loadKey)) {
            return QueryResult.loading(UNLOADED_DEFAULT_SCORE);
        }

        // Trigger async load
        if (loadingTracker.startLoading(loadKey)) {
            String dim = level.getRegistryKey().toString();
            storage.loadL2Async(dim, key)
                    .thenAccept(optEntry -> {
                        if (optEntry.isPresent()) {
                            // Cold storage has data, restore to hot cache
                            cache.restoreL2(level, optEntry.get().key(), 
                                    optEntry.get().l2Entry(), optEntry.get().createTime());
                        } else {
                            // Cold storage empty, create empty entry (new region)
                            cache.getOrCreateL2(level, key);
                        }
                        loadingTracker.finishLoading(loadKey, true);  // Always complete
                    });
        }

        return QueryResult.loading(UNLOADED_DEFAULT_SCORE);
    }

    /**
     * Query L3 score (with status).
     * 
     * <p>L3 uses cold storage. Return value status:
     * <ul>
     *   <li>PRECISE - hot cache hit, uses exact value</li>
     *   <li>LOADING - async loading in progress, returns 0 (unscanned = no civilization)</li>
     * </ul>
     */
    public QueryResult queryL3Score(ServerWorld level, L3Key key, ScoreComputer scoreComputer) {
        Optional<L3Entry> cached = cache.getL3Entry(level, key, scoreComputer);
        if (cached.isPresent()) {
            L3Entry entry = cached.get();
            return QueryResult.precise(entry.getCoarseScore(), entry.getPresenceTime());
        }

        String loadKey = "l3:" + level.getRegistryKey().toString() + ":" 
                + key.getC3x() + ":" + key.getC3z() + ":" + key.getS3y();

        // Check if already loading
        if (loadingTracker.isLoading(loadKey)) {
            return QueryResult.loading(UNLOADED_DEFAULT_SCORE);
        }

        // Trigger async load
        if (loadingTracker.startLoading(loadKey)) {
            String dim = level.getRegistryKey().toString();
            storage.loadL3Async(dim, key)
                    .thenAccept(optEntry -> {
                        if (optEntry.isPresent()) {
                            // Cold storage has data, restore to hot cache
                            cache.restoreL3(level, optEntry.get().key(), 
                                    optEntry.get().l3Entry(), optEntry.get().createTime());
                        } else {
                            // Cold storage empty, create empty entry (new region)
                            cache.getOrCreateL3(level, key);
                        }
                        loadingTracker.finishLoading(loadKey, true);  // Always complete
                    });
        }

        return QueryResult.loading(UNLOADED_DEFAULT_SCORE);
    }

    // ========== L2/L3 direct access ==========

    /**
     * Get L2 entry (for computation service).
     */
    public Optional<L2Entry> getL2Entry(ServerWorld level, L2Key key, ScoreComputer scoreComputer) {
        return cache.getL2Entry(level, key, scoreComputer);
    }

    /**
     * Get L3 entry (for computation service).
     */
    public Optional<L3Entry> getL3Entry(ServerWorld level, L3Key key, ScoreComputer scoreComputer) {
        return cache.getL3Entry(level, key, scoreComputer);
    }

    // ========== Statistics ==========

    public int l1Size() {
        return cache.l1Size();
    }

    public int l2Size() {
        return cache.l2Size();
    }

    public int l3Size() {
        return cache.l3Size();
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the underlying TTL cache (for debugging).
     */
    public TtlPyramidCache getCache() {
        return cache;
    }

    /**
     * Get the underlying H2 storage (for MobHeadRegistry initialization).
     */
    public H2Storage getStorage() {
        return storage;
    }

    // ========== Query result (L2/L3 only) ==========

    /**
     * L2/L3 query result containing the score, precision status, and presence timestamp.
     * 
     * <p>L1 does not use this structure; use {@link CivilizationCache#getChunkCScore} directly.
     * 
     * <p>Status descriptions:
     * <ul>
     *   <li>PRECISE - hot cache hit, uses exact value; {@code presenceTime} is valid</li>
     *   <li>LOADING - async loading from cold storage, returns 0 (unscanned = no civilization); decay not applied</li>
     * </ul>
     */
    public record QueryResult(double score, boolean isPrecise, long presenceTime) {
        public static QueryResult precise(double score, long presenceTime) {
            return new QueryResult(score, true, presenceTime);
        }

        public static QueryResult loading(double conservativeScore) {
            return new QueryResult(conservativeScore, false, 0);
        }

        public boolean isLoading() {
            return !isPrecise;
        }
    }
}
