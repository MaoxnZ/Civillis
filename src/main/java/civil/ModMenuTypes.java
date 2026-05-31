package civil;

import civil.towncenter.gui.TownCenterMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/**
 * Menu type registration (Fabric: direct register; NeoForge: deferred holder assigns via setter).
 */
public final class ModMenuTypes {

    private static MenuType<TownCenterMenu> townCenter;

    private ModMenuTypes() {}

    public static void registerDirect() {
        ResourceKey<MenuType<?>> key = ResourceKey.create(
                Registries.MENU, Identifier.fromNamespaceAndPath(CivilMod.MOD_ID, "town_center"));
        townCenter = Registry.register(
                BuiltInRegistries.MENU,
                key,
                createTownCenterMenuType());
    }

    /** NeoForge {@link net.neoforged.neoforge.registries.DeferredRegister} factory. */
    public static MenuType<TownCenterMenu> createTownCenterMenuType() {
        return new MenuType<>((id, inv) -> new TownCenterMenu(id, inv), FeatureFlags.DEFAULT_FLAGS);
    }

    public static void setTownCenter(MenuType<TownCenterMenu> type) {
        townCenter = type;
    }

    public static MenuType<TownCenterMenu> getTownCenter() {
        return townCenter;
    }
}
