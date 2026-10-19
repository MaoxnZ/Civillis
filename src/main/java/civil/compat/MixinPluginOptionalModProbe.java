package civil.compat;

import civil.CivilPlatform;
import java.util.List;

/**
 * Resolves optional third-party mod presence during {@link org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin}
 * discovery ({@code getMixins}).
 *
 * <p>On Forge/NeoForge, {@code ModList.get().isLoaded()} is often <b>not</b> reliable that early;
 * the FML loading mod list reflects mods discovered for the current launch and matches what Mixin
 * needs here. Falls back to {@link CivilPlatform#isModLoaded(String)} on Fabric.
 */
public final class MixinPluginOptionalModProbe {

    private MixinPluginOptionalModProbe() {
    }

    /** {@code true} when the mod id should be treated as present for conditional mixin registration. */
    public static boolean isPresentForMixinDiscovery(String modId) {
        if (fmlLoadingModListContains("net.neoforged.fml.loading.FMLLoader", modId)) {
            return true;
        }
        if (fmlLoadingModListContains("net.minecraftforge.fml.loading.FMLLoader", modId)) {
            return true;
        }
        try {
            return CivilPlatform.isModLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean fmlLoadingModListContains(String fmlLoaderClass, String modId) {
        try {
            Class<?> fmlLoader = Class.forName(fmlLoaderClass);
            Object loadingModList = fmlLoader.getMethod("getLoadingModList").invoke(null);
            if (loadingModList == null) {
                return false;
            }
            Object listObj = loadingModList.getClass().getMethod("getMods").invoke(loadingModList);
            if (!(listObj instanceof List<?> mods)) {
                return false;
            }
            for (Object mod : mods) {
                if (mod == null) {
                    continue;
                }
                Object idObj = mod.getClass().getMethod("getModId").invoke(mod);
                if (modId.equals(idObj)) {
                    return true;
                }
            }
            return false;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
