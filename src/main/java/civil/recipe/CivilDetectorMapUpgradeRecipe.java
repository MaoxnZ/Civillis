package civil.recipe;

import civil.ModItems;
import civil.component.ModComponents;
import civil.item.CivilMapLore;
import civil.map.CivilMapNbt;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Shapeless special recipe: one {@link Items#FILLED_MAP} with vanilla {@code map} id (not yet civil) + one detector.
 */
public final class CivilDetectorMapUpgradeRecipe extends CustomRecipe {

    public static RecipeSerializer<CivilDetectorMapUpgradeRecipe> SERIALIZER;

    public CivilDetectorMapUpgradeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    private static ItemStack findFilledMapIn(CraftingContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.is(Items.FILLED_MAP)) {
                return s;
            }
        }
        return null;
    }

    private static boolean ingredientsMatchShape(CraftingContainer inv) {
        int filledMaps = 0;
        int detectors = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            if (s.is(Items.FILLED_MAP)) {
                if (ModComponents.isCivilMap(s)) {
                    return false;
                }
                if (CivilMapNbt.getVanillaMapId(s) == null) {
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
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        if (!ingredientsMatchShape(inv)) {
            return false;
        }
        if (level instanceof ServerLevel sl) {
            ItemStack map = findFilledMapIn(inv);
            if (map == null) {
                return false;
            }
            return mapUnlockedOnServer(map, sl);
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registryAccess) {
        ItemStack map = findFilledMapIn(inv);
        if (map == null) {
            return ItemStack.EMPTY;
        }
        if (!ingredientsMatchShape(inv)) {
            return ItemStack.EMPTY;
        }
        ItemStack out = map.copy();
        out.setCount(1);
        ModComponents.setCivilMap(out, true);
        CivilMapLore.appendIfCivilFilledMap(out);
        return out;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER;
    }
}
