package civil;

import civil.recipe.CivilDetectorMapUpgradeRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

/**
 * Recipe serializers registered on both Fabric (direct) and NeoForge (deferred + assignment).
 */
public final class ModRecipeSerializers {

    private ModRecipeSerializers() {
    }

    public static void registerDirect() {
        CivilDetectorMapUpgradeRecipe.SERIALIZER =
                Registry.register(
                        BuiltInRegistries.RECIPE_SERIALIZER,
                        ResourceLocation.fromNamespaceAndPath(CivilMod.MOD_ID, "detector_map_upgrade"),
                        new SimpleCraftingRecipeSerializer<>(CivilDetectorMapUpgradeRecipe::new));
    }

    public static void bindDetectorMapUpgrade(RecipeSerializer<CivilDetectorMapUpgradeRecipe> serializer) {
        CivilDetectorMapUpgradeRecipe.SERIALIZER = serializer;
    }
}
