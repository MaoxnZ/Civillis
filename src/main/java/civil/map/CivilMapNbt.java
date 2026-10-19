package civil.map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla map id on stacks ({@code map} int) for MC versions without item components. */
public final class CivilMapNbt {

    /** Vanilla filled-map NBT key for map data id. */
    public static final String VANILLA_MAP_KEY = "map";

    private CivilMapNbt() {
    }

    public static Integer getVanillaMapId(ItemStack stack) {
        if (!stack.is(Items.FILLED_MAP)) {
            return null;
        }
        CompoundTag t = stack.getTag();
        if (t == null || !t.contains(VANILLA_MAP_KEY, Tag.TAG_INT)) {
            return null;
        }
        return t.getInt(VANILLA_MAP_KEY);
    }

    public static void setVanillaMapId(ItemStack stack, int mapId) {
        stack.getOrCreateTag().putInt(VANILLA_MAP_KEY, mapId);
    }
}
