package civil.compat.xaeros.minimap;

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
 * Registers {@link XaeroMinimapHighlighterRegisterMixin} only when {@code xaerominimap} <b>and</b>
 * {@code xaeroworldmap} are on the loading classpath. Minimap {@code ChunkHighlighter} resolves
 * {@code xaero.map.highlight.AbstractHighlighter} (WM jar); registering this mixin on minimap-only installs would
 * still run the redirect and can crash before {@link civil.compat.xaeros.highlight.CivilXaeroChunkBandHighlighters}
 * guards execute reliably across loaders.
 * See {@link MixinPluginOptionalModProbe#isPresentForMixinDiscovery(String)} — avoid {@code Class.forName} on Xaero HUD
 * classes in {@link #getMixins()}.
 * See {@code civil.xaeros-minimap.mixins.json}
 * ({@code client} must stay empty so mixins are not merged from JSON in addition to {@link #getMixins()}).
 */
public final class XaeroMinimapChunkBandMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName != null
                && mixinClassName.endsWith("XaeroMinimapHighlighterRegisterMixin")) {
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
                CivilMod.LOGGER.info("[xaeros][mm][plugin] getMixins: skip (not CLIENT)");
            }
            return Collections.emptyList();
        }
        if (!xaeroMinimapModPresent()) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[xaeros][mm][plugin] getMixins: skip (xaerominimap not loaded)");
            }
            return Collections.emptyList();
        }
        if (!xaeroWorldMapModPresent()) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info(
                        "[xaeros][mm][plugin] getMixins: skip (xaeroworldmap not loaded; minimap chunk-band highlighter needs WM)");
            }
            return Collections.emptyList();
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[xaeros][mm][plugin] getMixins: register XaeroMinimapHighlighterRegisterMixin");
        }
        return List.of("XaeroMinimapHighlighterRegisterMixin");
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
     * Do not {@code Class.forName} Xaero HUD classes here: early-loading {@code MinimapRenderer} can upset
     * Mixin application order the same way as {@code GuiMap} for world map.
     */
    private static boolean xaeroMinimapModPresent() {
        return MixinPluginOptionalModProbe.isPresentForMixinDiscovery("xaerominimap");
    }

    private static boolean xaeroWorldMapModPresent() {
        return MixinPluginOptionalModProbe.isPresentForMixinDiscovery("xaeroworldmap");
    }
}
