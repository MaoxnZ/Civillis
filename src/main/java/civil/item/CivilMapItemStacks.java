package civil.item;

import civil.component.ModComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Helpers for normalizing filled map stacks to vanilla {@link Items#FILLED_MAP} + {@link ModComponents#CIVIL_MAP}. */
public final class CivilMapItemStacks {

    private CivilMapItemStacks() {
    }

    /**
     * Copy all components from a vanilla filled map stack onto {@link Items#FILLED_MAP} and set
     * {@link ModComponents#CIVIL_MAP} plus civil lore when missing.
     * <p>
     * Uses {@link ItemStack#transmuteCopy} so components are carried as a patch against the target item’s defaults.
     * Used after cartography outputs; crafting-table civil upgrade uses {@link ItemStack#copyWithCount} on the
     * same filled map instead.
     */
    public static ItemStack toCivilFilled(ItemStack vanillaFilled) {
        ItemStack out = vanillaFilled.transmuteCopy(Items.FILLED_MAP, vanillaFilled.getCount());
        out.set(ModComponents.CIVIL_MAP, true);
        CivilMapLore.appendIfCivilFilledMap(out);
        return out;
    }
}
