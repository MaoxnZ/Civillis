package civil.map;

import civil.component.ModComponents;
import net.minecraft.world.item.ItemStack;

/** Shared helpers for civil map discovery (DRY across driver / recipients). */
public final class CivilMapUtil {

    private CivilMapUtil() {
    }

    /**
     * Returns the vanilla map id for a stack that is a civil map, or {@code null} if not civil
     * or missing {@link CivilMapNbt#VANILLA_MAP_KEY}.
     */
    public static Integer getCivilMapId(ItemStack stack) {
        if (!ModComponents.isCivilMap(stack)) {
            return null;
        }
        return CivilMapNbt.getVanillaMapId(stack);
    }

    public static boolean civilMapIdEquals(ItemStack stack, int mapId) {
        Integer id = getCivilMapId(stack);
        return id != null && id == mapId;
    }
}
