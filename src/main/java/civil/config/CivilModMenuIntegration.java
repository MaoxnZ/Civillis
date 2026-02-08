package civil.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu integration: provides a config screen button in the Mod Menu.
 *
 * <p>This class is only loaded when Mod Menu is present (registered as a
 * {@code modmenu} entrypoint). Cloth Config is checked at runtime; if absent,
 * no config screen is offered.
 */
public class CivilModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return CivilConfigScreen::create;
        }
        return parent -> null;
    }
}
