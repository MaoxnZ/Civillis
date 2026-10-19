package civil.item;

import civil.component.ModComponents;
import civil.map.CivilMapNbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

/** Adds a fixed lore line to civil map stacks (filled map + {@link ModComponents#CIVIL_MAP}). */
public final class CivilMapLore {

    private static final String LORE_KEY = "item.civil.civil_map.lore";
    private static final TextColor LORE_COLOR = TextColor.fromRgb(0xE8C896);

    private CivilMapLore() {
    }

    public static void appendIfCivilFilledMap(ItemStack stack) {
        if (!ModComponents.isCivilMap(stack)) {
            return;
        }
        if (CivilMapNbt.getVanillaMapId(stack) == null) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag display = tag.contains("display", Tag.TAG_COMPOUND) ? tag.getCompound("display") : new CompoundTag();
        ListTag lore =
                display.contains("Lore", Tag.TAG_LIST)
                        ? display.getList("Lore", Tag.TAG_STRING)
                        : new ListTag();
        if (hasCivilMapLoreLine(lore)) {
            return;
        }
        Component line = Component.translatable(LORE_KEY).withStyle(Style.EMPTY.withColor(LORE_COLOR));
        lore.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        display.put("Lore", lore);
        tag.put("display", display);
    }

    private static boolean hasCivilMapLoreLine(ListTag lore) {
        for (int i = 0; i < lore.size(); i++) {
            String s = lore.getString(i);
            if (s.contains(LORE_KEY)) {
                return true;
            }
        }
        return false;
    }
}
