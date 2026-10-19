package civil.compat.xaeros.highlight;

import civil.compat.bandsync.ChunkBandClientCache;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.hud.minimap.info.render.compile.InfoDisplayCompiler;
import xaero.common.minimap.highlight.ChunkHighlighter;

/**
 * Registers into Xaero minimap {@code HighlighterRegistry} (same pipeline as OPAC claims) so chunk bands align with
 * internal chunk tiles instead of a late {@code GuiGraphics} overlay.
 */
public final class CivilMinimapChunkBandHighlighter extends ChunkHighlighter {

    public CivilMinimapChunkBandHighlighter() {
        super(false);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> levelKey, int regionX, int regionZ) {
        if (!CivilXaeroChunkBandHighlightSupport.minimapGateOk()) {
            return false;
        }
        String dim = CivilXaeroChunkBandHighlightSupport.dimensionId(levelKey);
        return CivilXaeroChunkBandHighlightSupport.regionHasAnyHighlight(dim, regionX, regionZ);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> levelKey, int chunkX, int chunkZ) {
        if (!CivilXaeroChunkBandHighlightSupport.minimapGateOk()) {
            return false;
        }
        String dim = CivilXaeroChunkBandHighlightSupport.dimensionId(levelKey);
        byte b = ChunkBandClientCache.getBand(dim, chunkX, chunkZ);
        return CivilXaeroChunkBandHighlightSupport.isOverlayBand(b);
    }

    @Override
    protected int[] getColors(ResourceKey<Level> levelKey, int chunkX, int chunkZ) {
        if (!CivilXaeroChunkBandHighlightSupport.minimapGateOk()) {
            return null;
        }
        String dim = CivilXaeroChunkBandHighlightSupport.dimensionId(levelKey);
        byte b = ChunkBandClientCache.getBand(dim, chunkX, chunkZ);
        if (!CivilXaeroChunkBandHighlightSupport.isOverlayBand(b)) {
            return null;
        }
        int xaeroColor = CivilXaeroChunkBandHighlightSupport.xaeroColorForBand(b);
        CivilXaeroChunkBandHighlightSupport.fillUniformChunkColors(resultStore, xaeroColor);
        return resultStore;
    }

    @Override
    public void addChunkHighlightTooltips(
            InfoDisplayCompiler compiler, ResourceKey<Level> levelKey, int chunkX, int chunkZ, int subCell) {
    }
}
