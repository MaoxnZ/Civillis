package civil.map;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Map pixel to world chunk using the same j2/k2 corner as vanilla {@link net.minecraft.world.item.MapItem#update}
 * (see Mojmap: {@code j2 = (j / i + k1 - 64) * i}, {@code k2 = (k / i + l1 - 64) * i}).
 */
public final class CivilMapTintPixelGeometry {

    private CivilMapTintPixelGeometry() {
    }

    /**
     * Vanilla map pixel coordinates {@code (mapX, mapY)} correspond to loop variables {@code (k1, l1)} in
     * {@code MapItem.update}.
     */
    public static int chunkX(MapItemSavedData data, int mapX, int mapY) {
        int i = 1 << data.scale;
        int j = data.centerX;
        int k = data.centerZ;
        int j2 = (j / i + mapX - 64) * i;
        return SectionPos.blockToSectionCoord(j2);
    }

    public static int chunkZ(MapItemSavedData data, int mapX, int mapY) {
        int i = 1 << data.scale;
        int j = data.centerX;
        int k = data.centerZ;
        int k2 = (k / i + mapY - 64) * i;
        return SectionPos.blockToSectionCoord(k2);
    }

    public static long packChunk(MapItemSavedData data, int mapX, int mapY) {
        return CivilMapTintLogic.packChunk(chunkX(data, mapX, mapY), chunkZ(data, mapX, mapY));
    }
}
