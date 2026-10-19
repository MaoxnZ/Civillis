package civil.compat.xaeros;

import civil.CivilPlatform;
import civil.compat.bandsync.ChunkBandClientCache;
import civil.compat.bandsync.ChunkBandConsumers;
import civil.compat.bandsync.ChunkBandSubscriptionHub;
import civil.config.CivilConfig;
import net.minecraft.client.Minecraft;

/**
 * Client glue for Xaero WM/MM chunk-band bandsync: separate hub refcounts for minimap vs world map,
 * cache listener registration, and Highlighter cache invalidation.
 *
 * <p>Chunk-band overlays are rendered via Xaero's {@code ChunkHighlighter} pipeline
 * ({@link civil.compat.xaeros.highlight.CivilMinimapChunkBandHighlighter} /
 * {@link civil.compat.xaeros.highlight.CivilWorldMapChunkBandHighlighter}), registered through
 * {@link civil.compat.xaeros.minimap.XaeroMinimapHighlighterRegisterMixin} /
 * {@link civil.compat.xaeros.worldmap.XaeroWorldMapHighlighterRegisterMixin}.
 * Cache invalidation is driven by {@link ChunkBandClientCache} change listeners (XF-HUD-026).
 *
 * <p>Civil exposes one client toggle ({@link civil.config.CivilConfig#chunkBandClientEnabledXaeros}) for both Xaero
 * hub consumers; chunk-band is only active when {@code xaerominimap} and {@code xaeroworldmap} are both loaded.
 */
public final class XaerosChunkBandHooks {

    private static final Runnable CACHE_LISTENER = XaerosChunkBandHooks::onChunkBandCacheChanged;

    private static boolean minimapModLoaded;
    private static boolean worldMapModLoaded;

    private XaerosChunkBandHooks() {
    }

    /** Fabric / Forge client init: register cache listener once and snapshot mod-presence flags. */
    public static void register() {
        if (!CivilPlatform.isPhysicalClient()) {
            return;
        }
        minimapModLoaded = CivilPlatform.isModLoaded("xaerominimap");
        worldMapModLoaded = CivilPlatform.isModLoaded("xaeroworldmap");
        ChunkBandClientCache.addChangeListener(CACHE_LISTENER);
    }

    private static void onChunkBandCacheChanged() {
        refreshMinimapHighlights();
        refreshWorldMapHighlights();
    }

    /**
     * Bumps the minimap {@code DimensionHighlighterHandler.version} so the writer re-queries
     * {@code getColors()} on the next tile pass.
     * Chain: {@code XaeroMinimapSession.getCurrentSession().getMinimapProcessor().getMinimapWriter()
     *          .getDimensionHighlightHandler().requestRefresh()}.
     */
    private static void refreshMinimapHighlights() {
        try {
            var session = xaero.common.XaeroMinimapSession.getCurrentSession();
            if (session == null) return;
            var writer = session.getMinimapProcessor().getMinimapWriter();
            if (writer == null) return;
            var handler = writer.getDimensionHighlightHandler();
            if (handler != null) {
                handler.requestRefresh();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Clears the world-map {@code DimensionHighlighterHandler} hash cache so region hashes
     * (which include {@code overlayStamp()}) are recalculated on the next render.
     * Chain: {@code WorldMapSession.getCurrentSession().getMapProcessor().getMapWorld()
     *          .getCurrentDimension().getHighlightHandler().clearCachedHashes()}.
     */
    private static void refreshWorldMapHighlights() {
        try {
            var wmSession = xaero.map.WorldMapSession.getCurrentSession();
            if (wmSession == null) return;
            var mapWorld = wmSession.getMapProcessor().getMapWorld();
            if (mapWorld == null) return;
            var dim = mapWorld.getCurrentDimension();
            if (dim == null) return;
            var handler = dim.getHighlightHandler();
            if (handler != null) {
                handler.clearCachedHashes();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Called from {@link civil.compat.bandsync.ChunkBandClientSession#tick} on the client thread: acquires/releases
     * {@link ChunkBandConsumers#XAEROS_MINIMAP} / {@link ChunkBandConsumers#XAEROS_WORLDMAP} around in-world sessions
     * (JM uses JourneyMap events instead).
     * Both Xaero consumers require {@code xaerominimap} and {@code xaeroworldmap}.
     */
    public static void tickSubscription(Minecraft mc) {
        if (!CivilPlatform.isPhysicalClient()) {
            return;
        }
        boolean minimapMod = minimapModLoaded;
        boolean worldMapMod = worldMapModLoaded;
        if (!minimapMod && !worldMapMod) {
            releaseXaeroChunkBandConsumerIfHeld(ChunkBandConsumers.XAEROS_MINIMAP);
            releaseXaeroChunkBandConsumerIfHeld(ChunkBandConsumers.XAEROS_WORLDMAP);
            return;
        }
        if (mc.level == null) {
            releaseXaeroChunkBandConsumerIfHeld(ChunkBandConsumers.XAEROS_MINIMAP);
            releaseXaeroChunkBandConsumerIfHeld(ChunkBandConsumers.XAEROS_WORLDMAP);
            return;
        }
        boolean wantMm =
                CivilConfig.chunkBandClientEnabledXaeros && minimapMod && worldMapMod;
        boolean heldMm = ChunkBandSubscriptionHub.isHeld(ChunkBandConsumers.XAEROS_MINIMAP);
        if (wantMm && !heldMm) {
            ChunkBandSubscriptionHub.acquire(ChunkBandConsumers.XAEROS_MINIMAP);
        } else if (!wantMm && heldMm) {
            ChunkBandSubscriptionHub.release(ChunkBandConsumers.XAEROS_MINIMAP);
        }
        boolean wantWm =
                CivilConfig.chunkBandClientEnabledXaeros && minimapMod && worldMapMod;
        boolean heldWm = ChunkBandSubscriptionHub.isHeld(ChunkBandConsumers.XAEROS_WORLDMAP);
        if (wantWm && !heldWm) {
            ChunkBandSubscriptionHub.acquire(ChunkBandConsumers.XAEROS_WORLDMAP);
        } else if (!wantWm && heldWm) {
            ChunkBandSubscriptionHub.release(ChunkBandConsumers.XAEROS_WORLDMAP);
        }
    }

    private static void releaseXaeroChunkBandConsumerIfHeld(String consumerId) {
        if (ChunkBandSubscriptionHub.isHeld(consumerId)) {
            ChunkBandSubscriptionHub.release(consumerId);
        }
    }

    public static boolean shouldDrawXaerosMinimapChunkBandOverlay() {
        if (!CivilPlatform.isPhysicalClient()) {
            return false;
        }
        if (!CivilConfig.chunkBandClientEnabledXaeros) {
            return false;
        }
        if (!ChunkBandSubscriptionHub.isHeld(ChunkBandConsumers.XAEROS_MINIMAP)) {
            return false;
        }
        return minimapModLoaded && worldMapModLoaded;
    }

    public static boolean shouldDrawXaerosWorldMapChunkBandOverlay() {
        if (!CivilPlatform.isPhysicalClient()) {
            return false;
        }
        if (!CivilConfig.chunkBandClientEnabledXaeros) {
            return false;
        }
        if (!ChunkBandSubscriptionHub.isHeld(ChunkBandConsumers.XAEROS_WORLDMAP)) {
            return false;
        }
        return minimapModLoaded && worldMapModLoaded;
    }
}
