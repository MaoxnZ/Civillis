package civil.compat.xaeros.minimap;

import civil.compat.xaeros.highlight.CivilXaeroChunkBandHighlighters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.common.minimap.highlight.HighlighterRegistry;
import xaero.hud.minimap.module.MinimapSession;

/**
 * Registers {@link civil.compat.xaeros.highlight.CivilMinimapChunkBandHighlighter} into Xaero's session
 * {@link HighlighterRegistry} immediately before {@link HighlighterRegistry#end()} (same phase as OPAC).
 */
@Mixin(value = MinimapSession.class, remap = true)
public abstract class XaeroMinimapHighlighterRegisterMixin {

    @Redirect(
            method = "<init>",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lxaero/common/minimap/highlight/HighlighterRegistry;end()V"),
            remap = false)
    private void civil$registerChunkBandHighlighterBeforeEnd(HighlighterRegistry registry) {
        CivilXaeroChunkBandHighlighters.registerMinimap(registry);
        registry.end();
    }
}
