package civil.recipe;

import civil.ModItems;
import civil.component.ModComponents;
import civil.item.CivilMapLore;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Shapeless special recipe: one {@link Items#FILLED_MAP} with {@link net.minecraft.core.component.DataComponents#MAP_ID}
 * (not yet {@link ModComponents#CIVIL_MAP}, not {@link MapItemSavedData#locked}) + one {@link ModItems#getCivilDetector()}
 * upgrades the same map stack to carry {@link ModComponents#CIVIL_MAP} and civil lore (same {@code MAP_ID} / saved data).
 */
public final class CivilDetectorMapUpgradeRecipe extends CustomRecipe {

    public static RecipeSerializer<CivilDetectorMapUpgradeRecipe> SERIALIZER;

    public CivilDetectorMapUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    private static ItemStack findFilledMapIn(CraftingInput input) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.is(Items.FILLED_MAP)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Exactly one filled map (with map id, not civil) and one detector; no other items.
     */
    private static boolean ingredientsMatchShape(CraftingInput input) {
        int filledMaps = 0;
        int detectors = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.is(Items.FILLED_MAP)) {
                if (Boolean.TRUE.equals(s.get(ModComponents.CIVIL_MAP))) {
                    return false;
                }
                if (s.get(DataComponents.MAP_ID) == null) {
                    return false;
                }
                filledMaps++;
            } else if (s.is(ModItems.getCivilDetector())) {
                detectors++;
            } else {
                return false;
            }
        }
        return filledMaps == 1 && detectors == 1;
    }

    private static boolean mapUnlockedOnServer(ItemStack map, ServerLevel level) {
        MapItemSavedData data = MapItem.getSavedData(map, level);
        return data != null && !data.locked;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!ingredientsMatchShape(input)) {
            return false;
        }
        if (level instanceof ServerLevel sl) {
            ItemStack map = findFilledMapIn(input);
            if (map == null) {
                return false;
            }
            return mapUnlockedOnServer(map, sl);
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack map = findFilledMapIn(input);
        if (map == null) {
            return ItemStack.EMPTY;
        }
        if (!ingredientsMatchShape(input)) {
            return ItemStack.EMPTY;
        }
        ItemStack out = map.copyWithCount(1);
        out.set(ModComponents.CIVIL_MAP, true);
        CivilMapLore.appendIfCivilFilledMap(out);
        return out;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER;
    }
}
