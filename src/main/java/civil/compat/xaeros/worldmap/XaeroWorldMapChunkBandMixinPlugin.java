package civil.compat.xaeros.worldmap;

import civil.CivilMod;
import civil.compat.MixinPluginOptionalModProbe;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Registers {@link XaeroWorldMapHighlighterRegisterMixin} only when {@code xaerominimap} and {@code xaeroworldmap} are
 * both on the loading classpath. Civil Xaero chunk-band is a single feature gated on the pair: either mod alone cannot
 * load the full {@code ChunkHighlighter} superclass chain (see {@link civil.compat.xaeros.highlight.CivilXaeroChunkBandHighlighters}).
 * See {@link MixinPluginOptionalModProbe#isPresentForMixinDiscovery(String)} — do not probe {@code xaero.map.gui.GuiMap} via
 * {@code Class.forName} in {@link #getMixins()}. See {@code civil.xaeros-worldmap.mixins.json} ({@code client} must stay empty).
 */
public final class XaeroWorldMapChunkBandMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName != null && mixinClassName.endsWith("XaeroWorldMapHighlighterRegisterMixin")) {
            return xaeroMinimapModPresent() && xaeroWorldMapModPresent();
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        if (MixinEnvironment.getDefaultEnvironment().getSide() != MixinEnvironment.Side.CLIENT) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[xaeros][wm][plugin] getMixins: skip (not CLIENT)");
            }
            return Collections.emptyList();
        }
        if (!xaeroWorldMapModPresent()) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[xaeros][wm][plugin] getMixins: skip (xaeroworldmap not loaded)");
            }
            return Collections.emptyList();
        }
        if (!xaeroMinimapModPresent()) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[xaeros][wm][plugin] getMixins: skip (xaerominimap not loaded; Xaero chunk-band needs MM+WM)");
            }
            return Collections.emptyList();
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[xaeros][wm][plugin] getMixins: register XaeroWorldMapHighlighterRegisterMixin");
        }
        return List.of("XaeroWorldMapHighlighterRegisterMixin");
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

    /**
     * Do not {@code Class.forName} Xaero classes here: resolving {@code xaero.map.gui.GuiMap} during
     * {@link #getMixins()} triggers Mixin "target was loaded too early" for that mixin target.
     */
    private static boolean xaeroMinimapModPresent() {
        return MixinPluginOptionalModProbe.isPresentForMixinDiscovery("xaerominimap");
    }

    private static boolean xaeroWorldMapModPresent() {
        return MixinPluginOptionalModProbe.isPresentForMixinDiscovery("xaeroworldmap");
    }
}
