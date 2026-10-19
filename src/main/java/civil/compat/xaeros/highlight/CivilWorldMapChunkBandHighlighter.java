package civil.compat.xaeros.highlight;

import civil.compat.bandsync.ChunkBandClientCache;
import civil.map.CivilMapTintPalette;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

/**
 * Registers into Xaero world-map {@code xaero.map.highlight.HighlighterRegistry} so chunk bands use the same
 * highlight path as OPAC on WM.
 */
public final class CivilWorldMapChunkBandHighlighter extends ChunkHighlighter {

    public CivilWorldMapChunkBandHighlighter() {
        super(false);
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> levelKey, int regionX, int regionZ) {
        if (!CivilXaeroChunkBandHighlightSupport.worldMapGateOk()) {
            return 0;
        }
        String dim = CivilXaeroChunkBandHighlightSupport.dimensionId(levelKey);
        int content = CivilXaeroChunkBandHighlightSupport.regionContentHash(dim, regionX, regionZ);
        return 31 * dim.hashCode() + 31 * regionX + regionZ + 31 * content + ChunkBandClientCache.overlayStamp();
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> levelKey, int regionX, int regionZ) {
        if (!CivilXaeroChunkBandHighlightSupport.worldMapGateOk()) {
            return false;
        }
        String dim = CivilXaeroChunkBandHighlightSupport.dimensionId(levelKey);
        return CivilXaeroChunkBandHighlightSupport.regionHasAnyHighlight(dim, regionX, regionZ);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> levelKey, int chunkX, int chunkZ) {
        if (!CivilXaeroChunkBandHighlightSupport.worldMapGateOk()) {
            return false;
        }
        String dim = CivilXaeroChunkBandHighlightSupport.dimensionId(levelKey);
        byte b = ChunkBandClientCache.getBand(dim, chunkX, chunkZ);
        return CivilXaeroChunkBandHighlightSupport.isOverlayBand(b);
    }

    @Override
    protected int[] getColors(ResourceKey<Level> levelKey, int chunkX, int chunkZ) {
        if (!CivilXaeroChunkBandHighlightSupport.worldMapGateOk()) {
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
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> levelKey, int chunkX, int chunkZ) {
        return tooltipFor(levelKey, chunkX, chunkZ);
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> levelKey, int chunkX, int chunkZ) {
        return tooltipFor(levelKey, chunkX, chunkZ);
    }

    @Override
    public void addMinimapBlockHighlightTooltips(
            List<Component> lines, ResourceKey<Level> levelKey, int chunkX, int chunkZ, int subCell) {
    }

    private static Component tooltipFor(ResourceKey<Level> levelKey, int chunkX, int chunkZ) {
        if (!CivilXaeroChunkBandHighlightSupport.worldMapGateOk()) {
            return null;
        }
        String dim = CivilXaeroChunkBandHighlightSupport.dimensionId(levelKey);
        byte b = ChunkBandClientCache.getBand(dim, chunkX, chunkZ);
        if (b == CivilMapTintPalette.HIGH) {
            return Component.translatable("civil.xaero.tooltip.band.high");
        }
        if (b == CivilMapTintPalette.ZONE) {
            return Component.translatable("civil.xaero.tooltip.band.zone");
        }
        if (b == CivilMapTintPalette.MONSTER) {
            return Component.translatable("civil.xaero.tooltip.band.monster");
        }
        return null;
    }
}
