package civil;

import civil.item.CivilDetectorItem;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * Item registration. 1.21.1 does not require Item.Properties.setId();
 * use Registry.register(BuiltInRegistries.ITEM, ResourceLocation, Item) directly.
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
        CIVIL_DETECTOR = registerItem(CIVIL_DETECTOR_ID, CivilDetectorItem::new);
    }

    public static Item getCivilDetector() {
        return CIVIL_DETECTOR;
    }

    /** Set by NeoForge deferred registration after registry events fire. */
    public static void setCivilDetector(Item item) {
        CIVIL_DETECTOR = item;
    }

    private static Item registerItem(String id, java.util.function.Function<Item.Properties, Item> factory) {
        ResourceLocation identifier = new ResourceLocation(CivilMod.MOD_ID, id);

        Item.Properties properties = new Item.Properties().stacksTo(1);
        Item item = factory.apply(properties);
        return Registry.register(BuiltInRegistries.ITEM, identifier, item);
    }
}
