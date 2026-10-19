package civil;

import civil.component.ModComponents;
import civil.item.CivilDetectorItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Item registration. 1.21+ requires Item.Properties to set registryKey before
 * constructing item, otherwise runtime error "Item id not set".
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
        Identifier identifier = Identifier.fromNamespaceAndPath(CivilMod.MOD_ID, CIVIL_DETECTOR_ID);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, identifier);
        Item.Properties properties = new Item.Properties()
                .setId(key)
                .stacksTo(1)
                .component(ModComponents.DETECTOR_DISPLAY, "default");
        Item item = new CivilDetectorItem(properties);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
