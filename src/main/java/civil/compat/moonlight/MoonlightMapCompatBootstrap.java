package civil.compat.moonlight;

import net.mehvahdjukaar.moonlight.api.map.ExpandedMapData;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Invoked via reflection from {@link civil.CivilMod#tryBootstrapMoonlightMapCompat(boolean)} when
 * Moonlight is loaded.
 */
public final class MoonlightMapCompatBootstrap {

    private static volatile boolean bootstrapped;

    private MoonlightMapCompatBootstrap() {}

    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        CivilTintMapData.registerType();
        CivilMapTintMoonlightSpi.addListener(MoonlightMapCompatBootstrap::onTintPayload);
        CivilMapTintMoonlightSpi.setMoonlightParallelTintActive(true);
    }

    private static void onTintPayload(CivilMapTintMoonlightSpi.Payload payload) {
        MapItemSavedData mapData = payload.mapData();
        if (!(mapData instanceof ExpandedMapData)) {
            return;
        }
        var type = CivilTintMapData.tintType();
        if (type == null) {
            return;
        }
        CivilTintMapData tint = type.get(mapData);
        if (tint == null) {
            return;
        }
        tint.recordPixel(payload.mapX(), payload.mapY(), payload.band(), payload.edge());
        tint.markDirtyOn(mapData);
    }
}
