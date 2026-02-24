package civil.fabric;

import civil.config.CivilConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu integration: provides a config screen button in the Mod Menu.
 * Fabric-only — Mod Menu does not exist on NeoForge.
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
