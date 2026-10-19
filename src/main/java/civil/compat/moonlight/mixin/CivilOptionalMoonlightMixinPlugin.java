package civil.compat.moonlight.mixin;

import civil.compat.MixinPluginOptionalModProbe;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Registers Moonlight-dependent client mixins only when mod {@code moonlight} is loaded (see
 * {@link MixinPluginOptionalModProbe#isPresentForMixinDiscovery(String)} — avoid {@code Class.forName} on Moonlight API classes in
 * {@link #getMixins()} to match the Xaero optional-mixin plugin pattern). Lives with
 * {@link CivilMapMoonlightClientMixin}.
 */
public final class CivilOptionalMoonlightMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        if (!moonlightPresent()) {
            return Collections.emptyList();
        }
        if (MixinEnvironment.getDefaultEnvironment().getSide() != MixinEnvironment.Side.CLIENT) {
            return Collections.emptyList();
        }
        return List.of("CivilMapMoonlightClientMixin");
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {}

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {}

    private static boolean moonlightPresent() {
        return MixinPluginOptionalModProbe.isPresentForMixinDiscovery("moonlight");
    }
}
