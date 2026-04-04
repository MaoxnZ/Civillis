package civil.map;

import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Whether a map pixel is considered explored for civil tint (mirrors "no tint in fog" on the client).
 *
 * <p>Vanilla initializes {@link MapItemSavedData#colors} to zero; unexplored cells stay 0 until
 * {@link MapItemSavedData#updateColor} paints them. Using {@code == 0} matches the client leaving those pixels
 * untinted when {@code known == 0}.
 */
public final class CivilMapMapExploration {

    private CivilMapMapExploration() {
    }

    public static boolean isExplored(MapItemSavedData data, int idx) {
        return data.colors[idx] != 0;
    }
}
