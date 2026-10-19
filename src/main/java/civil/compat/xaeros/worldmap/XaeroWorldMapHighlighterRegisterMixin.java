package civil.compat.xaeros.worldmap;

import civil.compat.xaeros.highlight.CivilXaeroChunkBandHighlighters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.map.WorldMapSession;
import xaero.map.highlight.HighlighterRegistry;

/**
 * Registers {@link civil.compat.xaeros.highlight.CivilWorldMapChunkBandHighlighter} into WM's session
 * {@link HighlighterRegistry} before {@link HighlighterRegistry#end()} (same phase as OPAC).
 */
@Mixin(value = WorldMapSession.class, remap = true)
public abstract class XaeroWorldMapHighlighterRegisterMixin {

    @Redirect(
            method = "init",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lxaero/map/highlight/HighlighterRegistry;end()V"),
            remap = false)
    private void civil$registerChunkBandHighlighterBeforeEnd(HighlighterRegistry registry) {
        CivilXaeroChunkBandHighlighters.registerWorldMap(registry);
        registry.end();
    }
}
