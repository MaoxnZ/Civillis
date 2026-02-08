package civil;

import civil.component.ModComponents;
import civil.civilization.cache.TtlCacheService;
import civil.civilization.structure.SimpleNeighborhoodSampler;
import civil.item.CivilDetectorAnimationReset;
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

    /** Cache service (TTL cache + H2 async persistence + gradual decay). */
    private static TtlCacheService cacheService;

    @Override
    public void onInitialize() {
        CivilConfig.load();

        SimpleCivilizationOperator operator = new SimpleCivilizationOperator();
        SimpleNeighborhoodSampler sampler = new SimpleNeighborhoodSampler(
                CivilConfig.detectionRadiusX,
                CivilConfig.detectionRadiusZ,
                CivilConfig.detectionRadiusY);

        // TTL cache service (30-minute hot cache, H2 database persistence, gradual decay)
        cacheService = new TtlCacheService();

        ScalableCivilizationService civilizationService =
                new ScalableCivilizationService(sampler, List.of((CivilizationOperator) operator), cacheService);
        CivilServices.initCivilizationService(civilizationService);
        CivilServices.initCivilizationCache(cacheService);
        TpsLogger.register();

        // Register cache lifecycle events
        registerCacheEvents();

        ModComponents.initialize();
        ModSounds.initialize();
        ModItems.register();
        ModItems.registerItemGroups();
        CivilDetectorAnimationReset.register();

        if (DEBUG) {
            LOGGER.info("Civil mod loaded. (service=ScalableCivilizationService, TTL={}min, TPS={})",
                    cacheService.getCache().getTtlMillis() / 60000,
                    CivilConfig.isTpsLogEnabled() ? "on/" + CivilConfig.getTpsLogIntervalTicks() + "ticks" : "off");
            LOGGER.info("[civil] config thresholdMid={} thresholdLow={}",
                    String.format("%.4f", CivilConfig.spawnThresholdMid),
                    String.format("%.4f", CivilConfig.spawnThresholdLow));
        }
    }

    /**
     * Register cache lifecycle events: initialization, per-tick maintenance, and shutdown.
     */
    private void registerCacheEvents() {
        // Initialize H2 database and ServerClock when overworld loads
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (DEBUG) {
                    LOGGER.info("[civil] Cache service initializing...");
                }
                cacheService.initialize(world);
            }
        });

        // Per-tick maintenance (ServerClock tick, prefetch, TTL cleanup)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (cacheService != null && cacheService.isInitialized()) {
                cacheService.onServerTick(server);
            }
        });

        // Shutdown cache service when overworld unloads
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (DEBUG) {
                    LOGGER.info("[civil] Cache service shutting down...");
                }
                cacheService.shutdown();
            }
        });
    }

    /**
     * Get the cache service.
     */
    public static TtlCacheService getCacheService() {
        return cacheService;
    }
}
