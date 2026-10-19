package civil.map;

import civil.component.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;

/**
 * Shared helpers for civil map discovery (DRY across driver / recipients).
 */
public final class CivilMapUtil {

    private CivilMapUtil() {
    }

    /**
     * Returns the map id for a stack that is a civil map ({@link ModComponents#CIVIL_MAP}),
     * or {@code null} if not a civil map or missing {@link DataComponents#MAP_ID}.
     */
    public static MapId getCivilMapId(ItemStack stack) {
        if (!Boolean.TRUE.equals(stack.get(ModComponents.CIVIL_MAP))) {
            return null;
        }
        return stack.get(DataComponents.MAP_ID);
    }

    public static boolean civilMapIdEquals(ItemStack stack, MapId mapId) {
        MapId id = getCivilMapId(stack);
        return id != null && id.equals(mapId);
    }
}
