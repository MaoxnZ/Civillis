package civil.item;

import civil.component.ModComponents;
import net.minecraft.world.item.ItemStack;

/** Helpers for normalizing filled map stacks to vanilla {@link net.minecraft.world.item.Items#FILLED_MAP} + civil NBT. */
public final class CivilMapItemStacks {

    private CivilMapItemStacks() {
    }

    /**
     * Copy tags from a vanilla filled map stack, mark {@link ModComponents#CIVIL_MAP}, and append civil lore when missing.
     */
    public static ItemStack toCivilFilled(ItemStack vanillaFilled) {
        ItemStack out = vanillaFilled.copy();
        out.setCount(vanillaFilled.getCount());
        ModComponents.setCivilMap(out, true);
        CivilMapLore.appendIfCivilFilledMap(out);
        return out;
    }
}
