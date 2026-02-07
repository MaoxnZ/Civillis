package civil;

import civil.component.ModComponents;
import civil.civilization.cache.simple.LruPyramidCache;
import civil.civilization.cache.scalable.TtlCacheService;
import civil.civilization.storage.NbtStorage;
import civil.civilization.structure.NeighborhoodSampler;
import civil.civilization.structure.SimpleNeighborhoodSampler;
import civil.item.CivilDetectorAnimationReset;
import civil.civilization.core.PyramidCivilizationService;
import civil.civilization.core.ScalableCivilizationService;
import civil.civilization.operator.CivilizationOperator;
import civil.civilization.operator.SimpleCivilizationOperator;
import civil.config.CivilConfig;
import civil.perf.TpsLogger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CivilMod implements ModInitializer {
    public static final String MOD_ID = "civil";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Global debug switch: when true, outputs spawn decision, cache HIT/PUT/INVALIDATE,
     * TPS logging, and other debug logs. Set to false before release builds;
     * set to true locally to enable all debug output at once.
     */
    public static final boolean DEBUG = false;

    /**
     * Whether to use the TTL cache (H2 database).
     * true  = Use the scalable TTL cache + H2 async persistence (supports large-scale servers)
     * false = Use the simple LRU cache + NBT persistence (simple and stable)
     */
    public static final boolean USE_SCALABLE_CACHE = true;

    /** Legacy persistence manager (used when USE_SCALABLE_CACHE=false). */
    private static NbtStorage persistence;

    /** Scalable cache service (used when USE_SCALABLE_CACHE=true). */
    private static TtlCacheService cacheService;

    @Override
    public void onInitialize() {
        CivilConfig.load();
        NeighborhoodSampler sampler = new SimpleNeighborhoodSampler(
                PyramidCivilizationService.DETECTION_RADIUS_X,
                PyramidCivilizationService.DETECTION_RADIUS_Z,
                PyramidCivilizationService.DETECTION_RADIUS_Y);
        SimpleCivilizationOperator operator = new SimpleCivilizationOperator();

        if (USE_SCALABLE_CACHE) {
            // Scalable TTL cache + H2 database
            initWithTtlCache(sampler, operator);
        } else {
            // Simple LRU cache + NBT persistence
            initWithLruCache(sampler, operator);
        }

        ModComponents.initialize();
        ModSounds.initialize();
        ModItems.register();
        ModItems.registerItemGroups();
        CivilDetectorAnimationReset.register();
    }

    /**
     * Initialize with legacy LRU cache (backward compatible).
     */
    private void initWithLruCache(NeighborhoodSampler sampler, SimpleCivilizationOperator operator) {
        // Pyramid multi-layer cache: L1(4096) + L2(2048) + L3(512), no debounce needed (lazy load + idempotent markDirty)
        LruPyramidCache cache = new LruPyramidCache(4096, 2048, 512);
        PyramidCivilizationService civilizationService =
                new PyramidCivilizationService(sampler, List.of((CivilizationOperator) operator), cache);
        CivilServices.initCivilizationService(civilizationService);
        CivilServices.initCivilizationCache(cache);
        TpsLogger.register();

        // Register cache persistence events
        registerLruCachePersistence(cache);

        if (DEBUG) {
            LOGGER.info("Civil mod loaded. (mode=LRU, service=pyramid+SimpleCivilizationOperator, cache=L1:{}/L2:{}/L3:{}, TPS={})",
                    cache.l1Size(), cache.l2Size(), cache.l3Size(),
                    CivilConfig.isTpsLogEnabled() ? "on/" + CivilConfig.getTpsLogIntervalTicks() + "ticks" : "off");
        }
    }

    /**
     * Initialize with scalable TTL cache (supports large-scale servers).
     */
    private void initWithTtlCache(NeighborhoodSampler sampler, SimpleCivilizationOperator operator) {
        // TTL cache service (30-minute expiry, H2 database persistence)
        cacheService = new TtlCacheService();

        // Scalable civilization service: adapts TTL cache + H2 persistence
        ScalableCivilizationService civilizationService =
                new ScalableCivilizationService(sampler, List.of((CivilizationOperator) operator), cacheService);
        CivilServices.initCivilizationService(civilizationService);
        
        // Use TTL cache as CivilizationCache
        CivilServices.initCivilizationCache(cacheService);
        TpsLogger.register();

        // Register TTL cache events
        registerTtlCacheEvents();

        if (DEBUG) {
            LOGGER.info("Civil mod loaded. (mode=scalable, service=ScalableCivilizationService, TTL={}min, TPS={})",
                    cacheService.getCache().getTtlMillis() / 60000,
                    CivilConfig.isTpsLogEnabled() ? "on/" + CivilConfig.getTpsLogIntervalTicks() + "ticks" : "off");
        }
    }

    /**
     * Register legacy LRU cache persistence: load from save on overworld load, save on overworld unload.
     */
    private void registerLruCachePersistence(LruPyramidCache cache) {
        persistence = new NbtStorage(cache);

        // Load cache from save when overworld loads
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (CivilMod.DEBUG) {
                LOGGER.info("[civil] ServerWorldEvents.LOAD fired, world: {}", world.getRegistryKey());
            }
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (CivilMod.DEBUG) {
                    LOGGER.info("[civil] Overworld detected, loading cache...");
                }
                persistence.load(world);
            } else {
                if (CivilMod.DEBUG) {
                    LOGGER.info("[civil] Non-overworld, skipping cache load: {}", world.getRegistryKey());
                }
            }
        });

        // Save cache when overworld unloads
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                persistence.save();
            }
        });
    }

    /**
     * Register TTL cache events: initialization, per-tick maintenance, and shutdown.
     */
    private void registerTtlCacheEvents() {
        // Initialize H2 database when overworld loads
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (DEBUG) {
                    LOGGER.info("[civil] TTL cache service initializing...");
                }
                cacheService.initialize(world);
            }
        });

        // Per-tick maintenance (prefetch, TTL cleanup)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (cacheService != null && cacheService.isInitialized()) {
                cacheService.onServerTick(server);
            }
        });

        // Shutdown cache service when overworld unloads
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (DEBUG) {
                    LOGGER.info("[civil] TTL cache service shutting down...");
                }
                cacheService.shutdown();
            }
        });
    }

    /**
     * Get the legacy persistence manager (for manual save triggers).
     */
    public static NbtStorage getPersistence() {
        return persistence;
    }

    /**
     * Get the scalable cache service.
     */
    public static TtlCacheService getCacheService() {
        return cacheService;
    }
}
