package civil.map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Civil map tint band per chunk for civilization overlay; baked into {@link MapItemSavedData#colors} during
 * {@link net.minecraft.world.item.MapItem#update} ({@link civil.mixin.CivilMapItemUpdateTintMixin}).
 */
public final class CivilMapTintLogic {

    private CivilMapTintLogic() {
    }

    public static long packChunk(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /**
     * Tint band byte for {@code (cx, cz)} using surface-Y-aware SY and {@link CivilMapTintPalette}.
     */
    public static byte computeTintForChunk(
            ServerLevel mapLevel, int cx, int cz, CivilMapSurfaceSy.WorldSurfaceHeightStats heightStats) {
        CivilMapSurfaceSy.SurfaceSyComputation syComp = CivilMapSurfaceSy.compute(mapLevel, cx, cz, heightStats);
        if (syComp.sy().isEmpty()) {
            return CivilMapTintPalette.UNKNOWN;
        }
        return CivilMapTintPalette.evaluateTintForChunk(mapLevel, cx, cz, syComp.sy().getAsInt()).band();
    }

    /**
     * Resolves saved map data for {@code mapId} by probing each loaded dimension (same as vanilla lookup).
     */
    public static MapItemSavedData resolveMapData(MinecraftServer server, MapId mapId) {
        ItemStack probe = probeStack(mapId);
        for (ServerLevel sl : server.getAllLevels()) {
            MapItemSavedData data = MapItem.getSavedData(probe, sl);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    private static ItemStack probeStack(MapId mapId) {
        ItemStack stack = new ItemStack(Items.FILLED_MAP, 1);
        stack.set(DataComponents.MAP_ID, mapId);
        return stack;
    }
}
