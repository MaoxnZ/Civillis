package civil;

import civil.recipe.CivilDetectorMapUpgradeRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

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
                        Identifier.fromNamespaceAndPath(CivilMod.MOD_ID, "detector_map_upgrade"),
                        new CustomRecipe.Serializer<CivilDetectorMapUpgradeRecipe>(CivilDetectorMapUpgradeRecipe::new));
    }

    public static void bindDetectorMapUpgrade(RecipeSerializer<CivilDetectorMapUpgradeRecipe> serializer) {
        CivilDetectorMapUpgradeRecipe.SERIALIZER = serializer;
    }
}
