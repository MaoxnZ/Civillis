package civil.compat.xaeros.highlight;

import civil.CivilPlatform;

/**
 * Hooks invoked from optional Xaero mixins right before {@code HighlighterRegistry.end()} seals the session registry.
 * <p>
 * Policy: Civil Xaero chunk-band is one feature — both {@code xaerominimap} and {@code xaeroworldmap} must be loaded.
 * Either mod alone can miss half of the {@code ChunkHighlighter} superclass chain (WM-only vs MM-only crashes).
 */
public final class CivilXaeroChunkBandHighlighters {

    private CivilXaeroChunkBandHighlighters() {
    }

    public static void registerMinimap(xaero.common.minimap.highlight.HighlighterRegistry registry) {
        if (!CivilPlatform.isModLoaded("xaerominimap") || !CivilPlatform.isModLoaded("xaeroworldmap")) {
            return;
        }
        registry.register(new CivilMinimapChunkBandHighlighter());
    }

    public static void registerWorldMap(xaero.map.highlight.HighlighterRegistry registry) {
        if (!CivilPlatform.isModLoaded("xaerominimap") || !CivilPlatform.isModLoaded("xaeroworldmap")) {
            return;
        }
        registry.register(new CivilWorldMapChunkBandHighlighter());
    }
}
