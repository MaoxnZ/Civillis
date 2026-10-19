package civil;

import civil.component.ModComponents;
import civil.item.CivilDetectorItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Fabric direct item registration. MC 1.21.1 uses {@link Registry#register} with id;
 * newer lines may use {@code Item.Properties#setId(ResourceKey)} instead.
 */
public final class ModItems {

    private static final String CIVIL_DETECTOR_ID = "civil_detector";

    private static Item CIVIL_DETECTOR;

    private ModItems() {
    }

    public static void registerDirect() {
        CIVIL_DETECTOR = registerDetector();
    }

    public static Item getCivilDetector() {
        return CIVIL_DETECTOR;
    }

    public static void setCivilDetector(Item item) {
        CIVIL_DETECTOR = item;
    }

    private static Item registerDetector() {
        ResourceLocation detectorId = ResourceLocation.fromNamespaceAndPath(CivilMod.MOD_ID, CIVIL_DETECTOR_ID);
        Item.Properties properties = new Item.Properties()
                .stacksTo(1)
                .component(ModComponents.DETECTOR_DISPLAY, "default");
        Item item = new CivilDetectorItem(properties);
        return Registry.register(BuiltInRegistries.ITEM, detectorId, item);
    }
}
