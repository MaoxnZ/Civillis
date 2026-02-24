package civil;

import civil.component.ModComponents;
import civil.item.CivilDetectorItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

/**
 * Item registration. 1.21+ requires Item.Properties to set registryKey before
 * constructing item, otherwise runtime error "Item id not set".
 *
 * <p>Item group registration is platform-specific and handled by the
 * platform entry point (Fabric: ItemGroupEvents, NeoForge: BuildCreativeModeTabContentsEvent).
 */
public final class ModItems {

    private static final String CIVIL_DETECTOR_ID = "civil_detector";

    private static Item CIVIL_DETECTOR;

    private ModItems() {
    }

    /**
     * Direct registration via vanilla Registry API. Called by Fabric entry point
     * where registries are not frozen during mod init.
     */
    public static void registerDirect() {
        CIVIL_DETECTOR = registerWithKey(CIVIL_DETECTOR_ID, CivilDetectorItem::new);
    }

    public static Item getCivilDetector() {
        return CIVIL_DETECTOR;
    }

    /** Set by NeoForge deferred registration after registry events fire. */
    public static void setCivilDetector(Item item) {
        CIVIL_DETECTOR = item;
    }

    private static Item registerWithKey(String id, java.util.function.Function<Item.Properties, Item> factory) {
        Identifier identifier = Identifier.fromNamespaceAndPath(CivilMod.MOD_ID, id);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, identifier);

        Item.Properties properties = new Item.Properties()
                .setId(key)
                .stacksTo(1);
        if (CIVIL_DETECTOR_ID.equals(id)) {
            properties = properties.component(ModComponents.DETECTOR_DISPLAY, "default");
        }
        Item item = factory.apply(properties);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
