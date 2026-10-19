package civil.compat;

import civil.CivilPlatform;
import java.util.List;

/**
 * Resolves optional third-party mod presence during {@link org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin}
 * discovery ({@code getMixins}).
 *
 * <p>On Forge, {@link net.minecraftforge.fml.ModList#get()} / {@code isLoaded} is often <b>not</b> reliable that early;
 * {@link net.minecraftforge.fml.loading.FMLLoader#getLoadingModList()} reflects mods discovered for the current launch
 * and matches what Mixin needs here. On Fabric, {@link CivilPlatform#isModLoaded(String)} is sufficient.
 */
public final class MixinPluginOptionalModProbe {

    private MixinPluginOptionalModProbe() {
    }

    /** {@code true} when the mod id should be treated as present for conditional mixin registration. */
    public static boolean isPresentForMixinDiscovery(String modId) {
        if (forgeLoadingModListContains(modId)) {
            return true;
        }
        try {
            return CivilPlatform.isModLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean forgeLoadingModListContains(String modId) {
        try {
            Class<?> fmlLoader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
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
