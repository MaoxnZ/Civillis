package civil;

import civil.civilization.CScore;
import civil.civilization.BlockScanner;
import civil.civilization.HeadTracker;
import civil.civilization.VoxelChunkKey;
import civil.civilization.cache.TtlCacheService;
import civil.civilization.scoring.ScalableCivilizationService;
import civil.config.CivilConfig;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common mod entry point — platform-agnostic initialization and event handlers.
 * Platform-specific entry points (Fabric/NeoForge) call {@link #init()} and
 * register events that delegate to the handler methods defined here.
 */
public class CivilMod {
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

    /**
     * Common initialization — creates services and registers items/sounds.
     * Called by the platform-specific entry point (Fabric or NeoForge).
     * Platform-specific registrations (events, networking, resource loaders,
     * item groups) are handled by the caller after this returns.
     */
    public static void init() {
        CivilConfig.load();

        cacheService = new TtlCacheService();

        ScalableCivilizationService civilizationService =
                new ScalableCivilizationService(cacheService);
        headTracker = new HeadTracker();
        CivilServices.initCivilizationService(civilizationService);
        CivilServices.initCivilizationCache(cacheService);
        CivilServices.initHeadTracker(headTracker);

        // Registry calls (ModComponents, ModSounds, ModItems) are handled by
        // platform-specific entry points: Fabric calls registerDirect(),
        // NeoForge uses DeferredRegister.

        if (DEBUG) {
            LOGGER.info("Civil mod loaded. (service=ScalableCivilizationService, TTL={}min, TPS={})",
                    cacheService.getCache().getTtlMillis() / 60000,
                    CivilConfig.isTpsLogEnabled() ? "on/" + CivilConfig.getTpsLogIntervalTicks() + "ticks" : "off");
            LOGGER.info("[civil] config thresholdMid={} thresholdLow={}",
                    String.format("%.4f", CivilConfig.spawnThresholdMid),
                    String.format("%.4f", CivilConfig.spawnThresholdLow));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Event handlers — called by platform-specific event registration
    // ══════════════════════════════════════════════════════════

    /** Called when a server world loads. Initializes H2, ServerClock, and HeadTracker for overworld. */
    public static void onWorldLoad(MinecraftServer server, ServerLevel world) {
        if (world.dimension() == Level.OVERWORLD) {
            if (DEBUG) {
                LOGGER.info("[civil] Cache service initializing...");
            }
            cacheService.initialize(world);

            if (headTracker != null) {
                headTracker.initialize(cacheService.getStorage());
            }
        }
    }

    /** Called when a server world unloads. Shuts down HeadTracker and cache service for overworld. */
    public static void onWorldUnload(MinecraftServer server, ServerLevel world) {
        if (world.dimension() == Level.OVERWORLD) {
            if (DEBUG) {
                LOGGER.info("[civil] Cache service shutting down...");
            }
            if (headTracker != null) {
                headTracker.shutdown();
            }
            cacheService.shutdown();
        }
    }

    /** Called at end of each server tick. Drives cache maintenance (ServerClock, prefetch, TTL cleanup). */
    public static void onServerTick(MinecraftServer server) {
        if (cacheService != null && cacheService.isInitialized()) {
            cacheService.onServerTick(server);
        }
    }

    /** Called when a chunk loads. Discovers pre-existing heads and pre-fills L1 shards. */
    public static void onChunkLoad(ServerLevel world, ChunkAccess chunk) {
        String dim = world.dimension().toString();

        if (headTracker != null && headTracker.isInitialized()) {
            for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                if (chunk.getBlockEntity(bePos) instanceof SkullBlockEntity) {
                    BlockState state = chunk.getBlockState(bePos);
                    if (BlockScanner.isSkullBlock(state)) {
                        AbstractSkullBlock skull = (AbstractSkullBlock) state.getBlock();
                        String skullType = skull.getType().toString();
                        headTracker.onHeadAdded(dim, bePos.getX(), bePos.getY(), bePos.getZ(), skullType);
                    }
                }
            }
        }

        if (cacheService != null && cacheService.isInitialized()) {
            var cache = cacheService.getCache();
            var storage = cacheService.getStorage();
            LevelChunkSection[] sections = chunk.getSections();
            int bottomSy = Math.floorDiv(world.dimensionType().minY(), 16);

            for (int i = 0; i < sections.length; i++) {
                int sy = bottomSy + i;
                VoxelChunkKey key = new VoxelChunkKey(chunk.getPos().x, chunk.getPos().z, sy);

                if (cache.containsL1(world, key)) continue;

                Double coldScore = storage.loadL1Sync(dim, key);
                if (coldScore != null) {
                    cache.restoreL1(world, key, new CScore(coldScore), System.currentTimeMillis());
                    continue;
                }

                LevelChunkSection section = sections[i];
                if (!section.maybeHas(BlockScanner::isTargetBlock)) {
                    cache.restoreL1(world, key, new CScore(0.0), System.currentTimeMillis());
                }
            }
        }
    }

    /** Get the cache service. */
    public static TtlCacheService getCacheService() {
        return cacheService;
    }
}
