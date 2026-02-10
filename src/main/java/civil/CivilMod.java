package civil;

import civil.civilization.MobHeadRegistry;
import civil.civilization.operator.BlockCivilization;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.util.math.BlockPos;
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

    /** Mob head registry (persistent head position tracking for attraction system). */
    private static MobHeadRegistry mobHeadRegistry;

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
        mobHeadRegistry = new MobHeadRegistry();
        CivilServices.initCivilizationService(civilizationService);
        CivilServices.initCivilizationCache(cacheService);
        CivilServices.initMobHeadRegistry(mobHeadRegistry);
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
        // Initialize H2 database, ServerClock, and MobHeadRegistry when overworld loads
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (DEBUG) {
                    LOGGER.info("[civil] Cache service initializing...");
                }
                cacheService.initialize(world);

                // Initialize MobHeadRegistry after H2 is ready
                if (mobHeadRegistry != null) {
                    mobHeadRegistry.initialize(cacheService.getStorage());
                }
            }
        });

        // Per-tick maintenance (ServerClock tick, prefetch, TTL cleanup)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (cacheService != null && cacheService.isInitialized()) {
                cacheService.onServerTick(server);
            }
        });

        // Shutdown cache service and MobHeadRegistry when overworld unloads
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (DEBUG) {
                    LOGGER.info("[civil] Cache service shutting down...");
                }
                if (mobHeadRegistry != null) {
                    mobHeadRegistry.shutdown();
                }
                cacheService.shutdown();
            }
        });

        // Chunk load event: discover pre-existing heads for world upgrade path.
        // Scans BlockEntities (typically 0-10 per chunk) for skull blocks — negligible cost.
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (mobHeadRegistry == null || !mobHeadRegistry.isInitialized()) return;

            String dim = world.getRegistryKey().toString();
            for (BlockPos bePos : chunk.getBlockEntityPositions()) {
                if (chunk.getBlockEntity(bePos) instanceof SkullBlockEntity) {
                    BlockState state = chunk.getBlockState(bePos);
                    if (BlockCivilization.isMonsterHead(state)) {
                        AbstractSkullBlock skull = (AbstractSkullBlock) state.getBlock();
                        String skullType = skull.getSkullType().toString();
                        mobHeadRegistry.onHeadAdded(dim, bePos.getX(), bePos.getY(), bePos.getZ(), skullType);
                    }
                }
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
