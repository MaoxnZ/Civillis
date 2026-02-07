package civil;

import civil.component.ModComponents;
import civil.item.CivilDetectorItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Item registration. 1.21+ requires Item.Settings to set registryKey before constructing item, otherwise runtime error "Item id not set".
 * Uses Identifier / RegistryKey / Registry under Yarn mappings for direct registration.
 */
public final class ModItems {

    private static final String CIVIL_DETECTOR_ID = "civil_detector";

    private static Item CIVIL_DETECTOR;

    private ModItems() {
    }

    public static void register() {
        CIVIL_DETECTOR = registerWithKey(CIVIL_DETECTOR_ID, CivilDetectorItem::new);
    }

    public static Item getCivilDetector() {
        return CIVIL_DETECTOR;
    }

    private static Item registerWithKey(String id, java.util.function.Function<Item.Settings, Item> factory) {
        Identifier identifier = Identifier.of(CivilMod.MOD_ID, id);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, identifier);

        Item.Settings settings = new Item.Settings()
                .registryKey(key)
                .maxCount(1);
        if (CIVIL_DETECTOR_ID.equals(id)) {
            settings = settings.component(ModComponents.DETECTOR_DISPLAY, "default");
        }
        Item item = factory.apply(settings);
        return Registry.register(Registries.ITEM, key, item);
    }

    public static void registerItemGroups() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries ->
                entries.addAfter(Items.COMPASS, getCivilDetector())
        );
    }
}
