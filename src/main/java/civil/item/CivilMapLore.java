package civil.item;

import civil.component.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** Adds a fixed lore line to civil map stacks (filled map + {@link ModComponents#CIVIL_MAP}). */
public final class CivilMapLore {

    private static final String LORE_KEY = "item.civil.civil_map.lore";

    /** Pale gold for civil map flavor text (ARGB 0xFF is implicit in fromRgb). */
    private static final TextColor LORE_COLOR = TextColor.fromRgb(0xE8C896);

    private CivilMapLore() {
    }

    public static void appendIfCivilFilledMap(ItemStack stack) {
        if (!Boolean.TRUE.equals(stack.get(ModComponents.CIVIL_MAP))) {
            return;
        }
        if (stack.get(DataComponents.MAP_ID) == null) {
            return;
        }
        ItemLore existing = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        if (hasCivilMapLoreLine(existing)) {
            return;
        }
        Component line = Component.translatable(LORE_KEY).withStyle(Style.EMPTY.withColor(LORE_COLOR));
        List<Component> lines = new ArrayList<>(existing.lines().size() + 1);
        lines.addAll(existing.lines());
        lines.add(line);
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static boolean hasCivilMapLoreLine(ItemLore lore) {
        for (Component c : lore.lines()) {
            if (c.getContents() instanceof TranslatableContents tc && LORE_KEY.equals(tc.getKey())) {
                return true;
            }
        }
        return false;
    }
}
