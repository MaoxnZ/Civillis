package civil.compat.journeymap;

import civil.CivilMod;
import civil.compat.bandsync.ChunkBandClientCache;
import civil.compat.bandsync.ChunkBandConsumers;
import civil.compat.bandsync.ChunkBandSubscriptionHub;
import civil.compat.bandsync.ChunkBandTintPayload;
import civil.config.CivilConfig;
import civil.map.CivilMapTintPalette;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.Context;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds {@link PolygonOverlay}s from {@link ChunkBandClientCache} for JourneyMap.
 * <p>
 * Tinted {@linkplain ShapeProperties#setFillOpacity fill} (~30%) for the chunk face; stroke is disabled (no outline).
 * {@link #applyDelta} applies one {@link ChunkBandTintPayload}; {@link #fullRebuildFromCache} rescans L1 for the current
 * dimension.
 */
public final class JourneyMapChunkBandOverlayRenderer {

    private static final EnumSet<Context.MapType> MAP_TYPES = EnumSet.of(
            Context.MapType.Day,
            Context.MapType.Night,
            Context.MapType.Topo,
            Context.MapType.Biome,
            Context.MapType.Underground);
    private static final EnumSet<Context.UI> UIS = EnumSet.of(Context.UI.Fullscreen, Context.UI.Minimap);

    private static final ConcurrentHashMap<String, PolygonOverlay> TRACKED = new ConcurrentHashMap<>();

    private JourneyMapChunkBandOverlayRenderer() {
    }

    public static void clearPolygonTracking() {
        TRACKED.clear();
    }

    private static String trackKey(String dimensionId, int cx, int cz) {
        return dimensionId + ":" + cx + ":" + cz;
    }

    private static boolean mayDrawChunkBandPolygons(IClientAPI api) {
        if (api == null) {
            return false;
        }
        if (!CivilConfig.chunkBandClientEnabledJourneyMap) {
            return false;
        }
        if (!api.playerAccepts(CivilMod.MOD_ID, DisplayType.Polygon)) {
            return false;
        }
        if (!ChunkBandSubscriptionHub.isHeld(ChunkBandConsumers.JOURNEY_MAP)) {
            return false;
        }
        return Minecraft.getInstance().level != null;
    }

    public static void applyDelta(IClientAPI api, ChunkBandTintPayload payload) {
        if (api == null || payload == null) {
            return;
        }
        if (!mayDrawChunkBandPolygons(api)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        String currentDim = level.dimension().location().toString();
        if (!payload.dimensionId().equals(currentDim)) {
            return;
        }
        ResourceKey<Level> dimKey = level.dimension();
        for (ChunkBandTintPayload.Entry e : payload.entries()) {
            String tk = trackKey(payload.dimensionId(), e.cx(), e.cz());
            if (CivilMapTintPalette.isChunkBandOverlayStored(e.band())) {
                PolygonOverlay poly = buildChunkOverlay(dimKey, level, e.cx(), e.cz(), e.band());
                PolygonOverlay prev = TRACKED.put(tk, poly);
                if (prev != null) {
                    try {
                        api.remove(prev);
                    } catch (Exception ignored) {
                    }
                }
                try {
                    api.show(poly);
                } catch (Exception ignored) {
                }
            } else {
                PolygonOverlay prev = TRACKED.remove(tk);
                if (prev != null) {
                    try {
                        api.remove(prev);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info(
                    "[journeymap] applyDelta dim={} entries={} tracked={}",
                    payload.dimensionId(),
                    payload.entries().size(),
                    TRACKED.size());
        }
    }

    public static void fullRebuildFromCache(IClientAPI api) {
        if (api == null) {
            return;
        }
        if (!CivilConfig.chunkBandClientEnabledJourneyMap) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[journeymap] fullRebuild skip: chunkBand.clientEnabled.journeymap=false");
            }
            api.removeAll(CivilMod.MOD_ID, DisplayType.Polygon);
            clearPolygonTracking();
            return;
        }
        if (!api.playerAccepts(CivilMod.MOD_ID, DisplayType.Polygon)) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[journeymap] fullRebuild skip: player disabled Polygon overlays for {}", CivilMod.MOD_ID);
            }
            api.removeAll(CivilMod.MOD_ID, DisplayType.Polygon);
            clearPolygonTracking();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[journeymap] fullRebuild skip: client level not ready");
            }
            return;
        }

        api.removeAll(CivilMod.MOD_ID, DisplayType.Polygon);
        clearPolygonTracking();

        Level level = mc.level;
        ResourceKey<Level> dimKey = level.dimension();
        String dimStr = dimKey.location().toString();
        Long2ByteOpenHashMap bandMap = ChunkBandClientCache.copyDimensionMap(dimStr);

        int shown = 0;
        var keyIter = bandMap.keySet().iterator();
        while (keyIter.hasNext()) {
            long packed = keyIter.nextLong();
            byte band = bandMap.get(packed);
            if (!CivilMapTintPalette.isChunkBandOverlayStored(band)) {
                continue;
            }
            int cx = (int) (packed >> 32);
            int cz = (int) packed;
            try {
                PolygonOverlay poly = buildChunkOverlay(dimKey, level, cx, cz, band);
                api.show(poly);
                TRACKED.put(trackKey(dimStr, cx, cz), poly);
                shown++;
            } catch (Exception ignored) {
            }
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info(
                    "[journeymap] fullRebuild dim={} cache_rows={} polygons={}",
                    dimStr,
                    bandMap.size(),
                    shown);
        }
    }

    private static PolygonOverlay buildChunkOverlay(
            ResourceKey<Level> dimKey,
            Level level,
            int cx,
            int cz,
            byte band) {
        int y = surfaceY(level, cx, cz);
        BlockPos p00 = new BlockPos(cx * 16, y, cz * 16);
        BlockPos p10 = new BlockPos(cx * 16 + 16, y, cz * 16);
        BlockPos p11 = new BlockPos(cx * 16 + 16, y, cz * 16 + 16);
        BlockPos p01 = new BlockPos(cx * 16, y, cz * 16 + 16);
        MapPolygon poly = new MapPolygon(p00, p10, p11, p01);

        int argb;
        if (band == CivilMapTintPalette.HIGH) {
            argb = CivilConfig.chunkBandColorHigh;
        } else if (band == CivilMapTintPalette.ZONE) {
            argb = CivilConfig.chunkBandColorZone;
        } else {
            argb = CivilConfig.chunkBandColorMonster;
        }
        int rgb = argb & 0xFFFFFF;
        float opacity = ((argb >>> 24) & 0xFF) / 255.0f;
        ShapeProperties shape = new ShapeProperties();
        shape.setFillColor(rgb).setFillOpacity(opacity).setStrokeOpacity(0f).setStrokeWidth(0f);

        String id = "civil_jm_" + dimKey.location().getNamespace() + "_" + dimKey.location().getPath() + "_" + cx + "_" + cz;
        PolygonOverlay overlay = new PolygonOverlay(CivilMod.MOD_ID, id, dimKey, shape, poly);
        overlay.setActiveMapTypes(MAP_TYPES);
        overlay.setActiveUIs(UIS);
        return overlay;
    }

    private static int surfaceY(Level level, int cx, int cz) {
        if (!level.hasChunk(cx, cz)) {
            return level.getSeaLevel();
        }
        int x = cx * 16 + 8;
        int z = cz * 16 + 8;
        int y = level.getChunk(cx, cz).getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) - 1;
        DimensionType dt = level.dimensionType();
        int minY = dt.minY();
        int maxY = minY + dt.height() - 1;
        return Math.max(minY, Math.min(maxY, y));
    }
}
