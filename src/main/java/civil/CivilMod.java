package civil;

import civil.civilization.CScore;
import civil.civilization.BlockScanner;
import civil.civilization.HeadTracker;
import civil.registry.BlockWeightLoader;
import civil.registry.HeadTypeLoader;
import civil.civilization.VoxelChunkKey;
import civil.component.ModComponents;
import civil.civilization.cache.TtlCacheService;
import civil.aura.SonarBoundaryPayload;
import civil.aura.SonarChargePayload;
import civil.aura.SonarScanManager;
import civil.item.CivilDetectorAnimationReset;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import civil.civilization.scoring.ScalableCivilizationService;
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
import net.minecraft.world.chunk.ChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



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
     * Thread-local flag indicating the current spawn is from the natural mob
     * spawning pipeline ({@code SpawnHelper.spawnEntitiesInChunk}).
     * <p>Set to {@code true} by {@code CivilSpawnHelperMixin} on entry and cleared on
     * return.  The spawn gate mixin checks this flag so that only natural spawns are
     * subject to civilization scoring — spawn eggs, spawners, and commands bypass it.
     */
    public static final ThreadLocal<Boolean> NATURAL_SPAWN_CONTEXT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Cache service (TTL cache + H2 async persistence + gradual decay). */
    private static TtlCacheService cacheService;

    /** Head tracker (persistent head position tracking for attraction system). */
    private static HeadTracker headTracker;

    @Override
    public void onInitialize() {
        CivilConfig.load();

        // TTL cache service (60-minute hot cache, H2 database persistence, gradual decay)
        cacheService = new TtlCacheService();

        ScalableCivilizationService civilizationService =
                new ScalableCivilizationService(cacheService);
        headTracker = new HeadTracker();
        CivilServices.initCivilizationService(civilizationService);
        CivilServices.initCivilizationCache(cacheService);
        CivilServices.initHeadTracker(headTracker);
        TpsLogger.register();

        // Register datapack reload listeners (block weights + head types)
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.SERVER_DATA)
                .registerReloadListener(new BlockWeightLoader());
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.resource.ResourceType.SERVER_DATA)
                .registerReloadListener(new HeadTypeLoader());

        // Register cache lifecycle events
        registerCacheEvents();

        // Register S2C payload types (must be before client receiver registration)
        PayloadTypeRegistry.playS2C().register(SonarChargePayload.ID, SonarChargePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SonarBoundaryPayload.ID, SonarBoundaryPayload.CODEC);

        ModComponents.initialize();
        ModSounds.initialize();
        ModItems.register();
        ModItems.registerItemGroups();
        CivilDetectorAnimationReset.register();
        SonarScanManager.register();

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
        // Initialize H2 database, ServerClock, and HeadTracker when overworld loads
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (DEBUG) {
                    LOGGER.info("[civil] Cache service initializing...");
                }
                cacheService.initialize(world);

                // Initialize HeadTracker after H2 is ready
                if (headTracker != null) {
                    headTracker.initialize(cacheService.getStorage());
                }
            }
        });

        // Per-tick maintenance (ServerClock tick, prefetch, TTL cleanup)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (cacheService != null && cacheService.isInitialized()) {
                cacheService.onServerTick(server);
            }
        });

        // Shutdown cache service and HeadTracker when overworld unloads
        ServerWorldEvents.UNLOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (DEBUG) {
                    LOGGER.info("[civil] Cache service shutting down...");
                }
                if (headTracker != null) {
                    headTracker.shutdown();
                }
                cacheService.shutdown();
            }
        });

        // Chunk load event: discover pre-existing heads + Fusion Architecture L1 palette pre-fill.
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            String dim = world.getRegistryKey().toString();

            // 1. Discover pre-existing heads for world upgrade path (existing logic)
            if (headTracker != null && headTracker.isInitialized()) {
                for (BlockPos bePos : chunk.getBlockEntityPositions()) {
                    if (chunk.getBlockEntity(bePos) instanceof SkullBlockEntity) {
                        BlockState state = chunk.getBlockState(bePos);
                        if (BlockScanner.isSkullBlock(state)) {
                            AbstractSkullBlock skull = (AbstractSkullBlock) state.getBlock();
                            String skullType = skull.getSkullType().toString();
                            headTracker.onHeadAdded(dim, bePos.getX(), bePos.getY(), bePos.getZ(), skullType);
                        }
                    }
                }
            }

            // 2. Fusion Architecture: pre-fill L1 shards from H2 cold storage or palette scan
            if (cacheService != null && cacheService.isInitialized()) {
                var cache = cacheService.getCache();
                var storage = cacheService.getStorage();
                ChunkSection[] sections = chunk.getSectionArray();
                int bottomSy = Math.floorDiv(world.getDimension().minY(), 16);

                for (int i = 0; i < sections.length; i++) {
                    int sy = bottomSy + i;
                    VoxelChunkKey key = new VoxelChunkKey(chunk.getPos().x, chunk.getPos().z, sy);

                    // Already in hot cache? Skip.
                    if (cache.containsL1(world, key)) continue;

                    // Check H2 cold storage (sync — very fast, single row lookup)
                    Double coldScore = storage.loadL1Sync(dim, key);
                    if (coldScore != null) {
                        cache.restoreL1(world, key, new CScore(coldScore), System.currentTimeMillis());
                        continue;
                    }

                    // H2 miss: palette scan to determine if full computation is needed
                    ChunkSection section = sections[i];
                    if (!section.hasAny(BlockScanner::isTargetBlock)) {
                        // No target blocks in palette → guaranteed score 0.0.
                        // Only store in hot cache — NOT persisted to H2.
                        // Empty sections make up ~75-80% of all L1 entries (sky, deep stone);
                        // skipping H2 saves massive storage.  On next chunk load after TTL
                        // eviction, the palette check (~1μs) re-derives score=0 for free.
                        cache.restoreL1(world, key, new CScore(0.0), System.currentTimeMillis());
                        continue;
                    } else {
                        // Has target blocks — defer to first spawn check (computeAndCacheL1).
                        // NOT storing 0.0 here: a zero-score L1 entry would be
                        // indistinguishable from a truly empty section and would hide
                        // real civilization value until the next block change delta.
                        continue;
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
