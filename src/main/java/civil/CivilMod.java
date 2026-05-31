package civil;

import civil.map.CivilMapPerfTrace;
import civil.civilization.ServerClock;
import civil.civilization.BlockScanner;
import civil.civilization.BaseScoreSourceRegistry;
import civil.civilization.FarmShrineTracker;
import civil.civilization.HeadTracker;
import civil.civilization.UndyingAnchorTracker;
import civil.civilization.TownCenterTracker;
import civil.civilization.ZonePolicyService;
import civil.aura.SonarScanManager;
import civil.civilization.cache.TtlCacheService;
import civil.civilization.scoring.ScalableCivilizationService;
import civil.respawn.UndyingAnchorParticleManager;
import civil.respawn.UndyingAnchorSaveHandler;
import civil.shrine.FarmShrineParticleManager;
import civil.towncenter.TownCenterParticleManager;
import civil.config.CivilConfig;
import civil.registry.HeadTypeLoader;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

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

    /** Cache service (TTL cache + NBT disk persistence + gradual decay). */
    private static TtlCacheService cacheService;

    /** Head tracker (persistent head position tracking for attraction system). */
    private static HeadTracker headTracker;

    /** Undying anchor tracker (civil save system). */
    private static UndyingAnchorTracker undyingAnchorTracker;

    /** Farm shrine tracker (mob farm / spawn bypass). */
    private static FarmShrineTracker farmShrineTracker;

    private static TownCenterTracker townCenterTracker;
    private static BaseScoreSourceRegistry baseScoreSourceRegistry;

    /** Structure-based zone policy (spawn bypass, caution, HUD). */
    private static ZonePolicyService zonePolicyService;

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
        zonePolicyService = new ZonePolicyService();
        undyingAnchorTracker = new UndyingAnchorTracker();
        farmShrineTracker = new FarmShrineTracker();
        townCenterTracker = new TownCenterTracker();
        baseScoreSourceRegistry = new BaseScoreSourceRegistry();
        CivilServices.initCivilizationService(civilizationService);
        CivilServices.initCivilizationCache(cacheService);
        CivilServices.initHeadTracker(headTracker);
        CivilServices.initZonePolicyService(zonePolicyService);
        CivilServices.initUndyingAnchorTracker(undyingAnchorTracker);
        CivilServices.initFarmShrineTracker(farmShrineTracker);
        CivilServices.initTownCenterTracker(townCenterTracker);
        CivilServices.initBaseScoreSourceRegistry(baseScoreSourceRegistry);

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

    /**
     * Call immediately after {@link HeadTypeLoader#reload} on the server thread.
     * Rebuilds farm-shrine head attribution so {@link FarmShrineTracker} matches the new head-type table
     * (Fabric: world LOAD can run before the first reload; datapack /reload on both loaders).
     */
    public static void onHeadTypesReloaded() {
        FarmShrineTracker shrines = CivilServices.getFarmShrineTracker();
        if (shrines != null) {
            shrines.rebuildAllShrineHeadPosSetsFromHeadTracker();
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Event handlers — called by platform-specific event registration
    // ══════════════════════════════════════════════════════════

    /** Called when a server world loads. Initializes storage, ServerClock, and trackers for overworld. */
    public static void onWorldLoad(MinecraftServer server, ServerLevel world) {
        if (world.dimension() == Level.OVERWORLD) {
            UndyingAnchorSaveHandler.clearAllTransientStates(server);
            if (DEBUG) {
                LOGGER.info("[civil] Cache service initializing...");
            }
            cacheService.initialize(world);

            if (headTracker != null) {
                headTracker.initialize(cacheService.getStorage());
            }
            // Farm shrines rebuild headPosSet from HeadTracker — must run after headTracker.initialize.
            if (farmShrineTracker != null) {
                farmShrineTracker.initialize(cacheService.getStorage(), headTracker);
            }
            if (undyingAnchorTracker != null) {
                undyingAnchorTracker.initialize(cacheService.getStorage());
            }
            if (baseScoreSourceRegistry != null) {
                baseScoreSourceRegistry.initialize(cacheService.getStorage());
            }
            if (townCenterTracker != null) {
                townCenterTracker.initialize(cacheService.getStorage());
            }
            if (zonePolicyService != null) {
                zonePolicyService.clear();
            }
        }
    }

    /** Called when a server world unloads. Shuts down HeadTracker and cache service for overworld. */
    public static void onWorldUnload(MinecraftServer server, ServerLevel world) {
        if (world.dimension() == Level.OVERWORLD) {
            if (DEBUG) {
                LOGGER.info("[civil] Cache service shutting down...");
            }
            UndyingAnchorSaveHandler.clearForWorld(world);
            SonarScanManager.shutdown();
            UndyingAnchorParticleManager.reset();
            FarmShrineParticleManager.reset();
            TownCenterParticleManager.reset();
            // Shutdown cache first: unified flush snapshots from headTracker/anchorTracker, then awaits
            if (cacheService != null) {
                cacheService.shutdown();
            }
            if (headTracker != null) {
                headTracker.shutdown();
            }
            if (farmShrineTracker != null) {
                farmShrineTracker.shutdown();
            }
            if (undyingAnchorTracker != null) {
                undyingAnchorTracker.shutdown();
            }
            if (zonePolicyService != null) {
                zonePolicyService.clear();
            }
        }
    }

    /** Called at end of each server tick. Drives cache maintenance (ServerClock, prefetch, TTL cleanup). */
    public static void onServerTick(MinecraftServer server) {
        if (zonePolicyService != null) {
            zonePolicyService.onServerTick(server.getTickCount());
        }
        if (cacheService != null && cacheService.isInitialized()) {
            cacheService.onServerTick(server);
        }
        civil.towncenter.TownCenterShutdownService.onServerTick(server);
        civil.towncenter.TownCenterZoneEffectService.onServerTick(server);
        CivilMapPerfTrace.flushServerWindow();
    }

    /** CFR: player disconnect — undying-anchor transient state + prefetcher eviction. */
    public static void onPlayerLogout(ServerPlayer player) {
        UndyingAnchorSaveHandler.clearForPlayer(player);
        civil.towncenter.TownCenterZoneEffectService.clearPlayer(player.getUUID());
        if (cacheService != null && cacheService.isInitialized()) {
            cacheService.onPlayerLeave(player.getUUID());
        }
    }

    /** Called when a chunk loads. Discovers pre-existing mob heads (L1 is lazy via {@link TtlCacheService#getChunkCScore}). */
    public static void onChunkLoad(ServerLevel world, ChunkAccess chunk) {
        String dim = world.dimension().identifier().toString();

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
    }

    /** Get the cache service. */
    public static TtlCacheService getCacheService() {
        return cacheService;
    }

    /**
     * Rebuild Civil runtime data by deleting data/civil and reinitializing services.
     * Intended for admin command use (/civil rebuild).
     */
    public static boolean rebuildCivilData(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            LOGGER.warn("[civil] rebuild failed: overworld is null");
            return false;
        }
        try {
            onWorldUnload(server, overworld);
            Path civilDir = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("civil");
            if (Files.exists(civilDir)) {
                try (Stream<Path> walk = Files.walk(civilDir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
            onWorldLoad(server, overworld);
            LOGGER.info("[civil] rebuild completed");
            return true;
        } catch (Exception e) {
            LOGGER.error("[civil] rebuild failed", e);
            return false;
        }
    }
}
