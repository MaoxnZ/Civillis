package civil.map;

import civil.CivilMod;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Per-thread session while {@link net.minecraft.world.item.MapItem#update} runs on a civil map stack.
 * Computes civilization tint and bakes it into {@link MapItemSavedData#colors} via {@link #updateColorWithBake};
 * padding is session-local only (no disk / no S2C tint channel).
 */
public final class CivilMapTintUpdateSession {

    private static final ThreadLocal<Context> CTX = new ThreadLocal<>();

    private CivilMapTintUpdateSession() {}

    public static void begin(Level level, Entity entity, MapItemSavedData data) {
        CTX.remove();
        if (!(level instanceof ServerLevel sl) || !(entity instanceof Player player)) {
            return;
        }
        if (!data.dimension.equals(level.dimension())) {
            return;
        }
        MapId mapId = resolveCivilMapId(sl, player, data);
        if (mapId == null) {
            return;
        }
        long startNs = CivilMod.DEBUG ? System.nanoTime() : 0L;
        CTX.set(new Context(sl, mapId, data, startNs));
    }

    public static void end() {
        Context c = CTX.get();
        CTX.remove();
        if (c == null) {
            return;
        }
        if (CivilMod.DEBUG && c.pixelsProcessed > 0) {
            long us = (System.nanoTime() - c.traceStartNs) / 1000L;
            CivilMapPerfTrace.onServerTintPass(0, c.pixelsProcessed, c.pixelsBaked, us);
        }
    }

    /**
     * Invoked from mixin instead of vanilla {@link MapItemSavedData#updateColor}; bakes civilization overlay when
     * session is active.
     */
    public static boolean updateColorWithBake(MapItemSavedData data, int mapX, int mapY, byte packedColor) {
        Context c = CTX.get();
        if (c == null || c.mapData != data) {
            return data.updateColor(mapX, mapY, packedColor);
        }
        return c.applyBakedUpdate(mapX, mapY, packedColor);
    }

    private static final EquipmentSlot[] HAND_SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private static MapId resolveCivilMapId(ServerLevel level, Player player, MapItemSavedData data) {
        for (EquipmentSlot slot : HAND_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (MapItem.getSavedData(stack, level) != data) {
                continue;
            }
            MapId id = CivilMapUtil.getCivilMapId(stack);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static final class Context {
        final ServerLevel level;
        @SuppressWarnings("unused")
        final MapId mapId;

        final MapItemSavedData mapData;
        final long traceStartNs;
        final Long2ByteOpenHashMap chunkTintThisUpdate = new Long2ByteOpenHashMap(256);
        final CivilMapTintPaddingState padding = new CivilMapTintPaddingState();
        int pixelsProcessed;
        int pixelsBaked;

        Context(ServerLevel level, MapId mapId, MapItemSavedData mapData, long traceStartNs) {
            this.level = level;
            this.mapId = mapId;
            this.mapData = mapData;
            this.traceStartNs = traceStartNs;
        }

        byte tintForChunkCached(int cx, int cz) {
            long key = CivilMapTintLogic.packChunk(cx, cz);
            if (chunkTintThisUpdate.containsKey(key)) {
                return chunkTintThisUpdate.get(key);
            }
            byte t = CivilMapTintLogic.computeTintForChunk(level, cx, cz, null);
            chunkTintThisUpdate.put(key, t);
            return t;
        }

        boolean applyBakedUpdate(int mapX, int mapY, byte packedColor) {
            pixelsProcessed++;
            int idx = mapX + mapY * 128;
            boolean isEdge = mapX == 0 || mapX == 127 || mapY == 0 || mapY == 127;

            if ((packedColor & 0xFF) == 0) {
                if (isEdge) {
                    clearPaddingForEdgePixel(padding, mapX, mapY);
                }
                return mapData.updateColor(mapX, mapY, packedColor);
            }

            if (isEdge) {
                fillPaddingForEdgePixel(this, padding, mapX, mapY);
            }

            int cx = CivilMapTintPixelGeometry.chunkX(mapData, mapX, mapY);
            int cz = CivilMapTintPixelGeometry.chunkZ(mapData, mapX, mapY);
            byte band = tintForChunkCached(cx, cz);

            CivilMapBakeEdgePixels.MapTintProbe probe =
                    (nx, ny) -> {
                        int ncx = CivilMapTintPixelGeometry.chunkX(mapData, nx, ny);
                        int ncz = CivilMapTintPixelGeometry.chunkZ(mapData, nx, ny);
                        return tintForChunkCached(ncx, ncz);
                    };
            boolean edge =
                    CivilMapBakeEdgePixels.isEdgePixel(
                            mapX, mapY, band, mapData, padding, probe);
            byte baked = CivilMapColorBake.blendPackedMapByte(packedColor, band, edge);
            if (baked != packedColor) {
                pixelsBaked++;
            }
            return mapData.updateColor(mapX, mapY, baked);
        }
    }

    private static void clearPaddingForEdgePixel(CivilMapTintPaddingState pad, int mapX, int mapY) {
        if (mapX == 0) {
            clearLeft(pad, mapY);
        }
        if (mapX == 127) {
            clearRight(pad, mapY);
        }
        if (mapY == 0) {
            clearTop(pad, mapX);
        }
        if (mapY == 127) {
            clearBottom(pad, mapX);
        }
        if (mapX == 0 && mapY == 0) {
            clearCorner(pad, 0);
        }
        if (mapX == 127 && mapY == 0) {
            clearCorner(pad, 1);
        }
        if (mapX == 0 && mapY == 127) {
            clearCorner(pad, 2);
        }
        if (mapX == 127 && mapY == 127) {
            clearCorner(pad, 3);
        }
    }

    private static void fillPaddingForEdgePixel(
            Context ctx, CivilMapTintPaddingState pad, int mapX, int mapY) {
        MapItemSavedData data = ctx.mapData;
        if (mapX == 0) {
            fillLeft(ctx, pad, mapY);
        }
        if (mapX == 127) {
            fillRight(ctx, pad, mapY);
        }
        if (mapY == 0) {
            fillTop(ctx, pad, mapX);
        }
        if (mapY == 127) {
            fillBottom(ctx, pad, mapX);
        }
        if (mapX == 0 && mapY == 0) {
            fillCorner(ctx, pad, 0, -1, -1);
        }
        if (mapX == 127 && mapY == 0) {
            fillCorner(ctx, pad, 1, 128, -1);
        }
        if (mapX == 0 && mapY == 127) {
            fillCorner(ctx, pad, 2, -1, 128);
        }
        if (mapX == 127 && mapY == 127) {
            fillCorner(ctx, pad, 3, 128, 128);
        }
    }

    private static void clearLeft(CivilMapTintPaddingState pad, int y) {
        pad.leftTint[y] = CivilMapTintPalette.UNKNOWN;
        pad.leftKnown[y] = 0;
    }

    private static void clearRight(CivilMapTintPaddingState pad, int y) {
        pad.rightTint[y] = CivilMapTintPalette.UNKNOWN;
        pad.rightKnown[y] = 0;
    }

    private static void clearTop(CivilMapTintPaddingState pad, int x) {
        pad.topTint[x] = CivilMapTintPalette.UNKNOWN;
        pad.topKnown[x] = 0;
    }

    private static void clearBottom(CivilMapTintPaddingState pad, int x) {
        pad.bottomTint[x] = CivilMapTintPalette.UNKNOWN;
        pad.bottomKnown[x] = 0;
    }

    private static void clearCorner(CivilMapTintPaddingState pad, int cornerIndex) {
        pad.cornerTint[cornerIndex] = CivilMapTintPalette.UNKNOWN;
        pad.cornerKnown[cornerIndex] = 0;
    }

    private static void fillLeft(Context ctx, CivilMapTintPaddingState pad, int y) {
        MapItemSavedData data = ctx.mapData;
        int cx = CivilMapTintPixelGeometry.chunkX(data, -1, y);
        int cz = CivilMapTintPixelGeometry.chunkZ(data, -1, y);
        byte tint = ctx.tintForChunkCached(cx, cz);
        pad.leftTint[y] = tint;
        pad.leftKnown[y] = 1;
    }

    private static void fillRight(Context ctx, CivilMapTintPaddingState pad, int y) {
        MapItemSavedData data = ctx.mapData;
        int cx = CivilMapTintPixelGeometry.chunkX(data, 128, y);
        int cz = CivilMapTintPixelGeometry.chunkZ(data, 128, y);
        byte tint = ctx.tintForChunkCached(cx, cz);
        pad.rightTint[y] = tint;
        pad.rightKnown[y] = 1;
    }

    private static void fillTop(Context ctx, CivilMapTintPaddingState pad, int x) {
        MapItemSavedData data = ctx.mapData;
        int cx = CivilMapTintPixelGeometry.chunkX(data, x, -1);
        int cz = CivilMapTintPixelGeometry.chunkZ(data, x, -1);
        byte tint = ctx.tintForChunkCached(cx, cz);
        pad.topTint[x] = tint;
        pad.topKnown[x] = 1;
    }

    private static void fillBottom(Context ctx, CivilMapTintPaddingState pad, int x) {
        MapItemSavedData data = ctx.mapData;
        int cx = CivilMapTintPixelGeometry.chunkX(data, x, 128);
        int cz = CivilMapTintPixelGeometry.chunkZ(data, x, 128);
        byte tint = ctx.tintForChunkCached(cx, cz);
        pad.bottomTint[x] = tint;
        pad.bottomKnown[x] = 1;
    }

    private static void fillCorner(Context ctx, CivilMapTintPaddingState pad, int cornerIndex, int mapX, int mapY) {
        MapItemSavedData data = ctx.mapData;
        int cx = CivilMapTintPixelGeometry.chunkX(data, mapX, mapY);
        int cz = CivilMapTintPixelGeometry.chunkZ(data, mapX, mapY);
        byte tint = ctx.tintForChunkCached(cx, cz);
        pad.cornerTint[cornerIndex] = tint;
        pad.cornerKnown[cornerIndex] = 1;
    }
}
