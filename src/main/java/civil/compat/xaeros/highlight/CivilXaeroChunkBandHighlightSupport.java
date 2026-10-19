package civil.compat.xaeros.highlight;

import civil.compat.bandsync.ChunkBandClientCache;
import civil.compat.xaeros.XaerosChunkBandHooks;
import civil.config.CivilConfig;
import civil.map.CivilMapTintLogic;
import civil.map.CivilMapTintPalette;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Shared chunk-band -> Xaero highlighter helpers (minimap + world map).
 *
 * <p>Xaero's {@code ChunkHighlighter} color format is {@code 0xBBGGRRAA} (alpha in low byte),
 * NOT standard MC ARGB {@code 0xAARRGGBB}. Use {@link #xaeroColorForBand} (not {@link #argbForBand})
 * when feeding colors into the highlighter pipeline.
 */
public final class CivilXaeroChunkBandHighlightSupport {

    /** OPAC / anvil-style region size in chunk coordinates (see Xaero OPAC {@code getRegion} usage). */
    public static final int REGION_SIZE_CHUNKS = 32;

    private CivilXaeroChunkBandHighlightSupport() {
    }

    public static boolean minimapGateOk() {
        return XaerosChunkBandHooks.shouldDrawXaerosMinimapChunkBandOverlay();
    }

    public static boolean worldMapGateOk() {
        return XaerosChunkBandHooks.shouldDrawXaerosWorldMapChunkBandOverlay();
    }

    public static String dimensionId(ResourceKey<Level> levelKey) {
        return levelKey.location().toString();
    }

    public static boolean isOverlayBand(byte band) {
        return band == CivilMapTintPalette.HIGH || band == CivilMapTintPalette.ZONE || band == CivilMapTintPalette.MONSTER;
    }

    /** Standard MC ARGB ({@code 0xAARRGGBB}) for the given band, read from {@link CivilConfig}. */
    public static int argbForBand(byte band) {
        if (band == CivilMapTintPalette.HIGH) {
            return CivilConfig.chunkBandColorHigh;
        }
        if (band == CivilMapTintPalette.ZONE) {
            return CivilConfig.chunkBandColorZone;
        }
        if (band == CivilMapTintPalette.MONSTER) {
            return CivilConfig.chunkBandColorMonster;
        }
        return 0;
    }

    /**
     * Converts standard ARGB {@code 0xAARRGGBB} to Xaero's highlight format {@code 0xBBGGRRAA}.
     * Derived from {@code ClaimsHighlighter.getColors} bytecode (25.3.10).
     */
    public static int argbToXaeroHighlight(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (b << 24) | (g << 16) | (r << 8) | a;
    }

    /** Xaero highlight format ({@code 0xBBGGRRAA}) for the given band. */
    public static int xaeroColorForBand(byte band) {
        return argbToXaeroHighlight(argbForBand(band));
    }

    public static boolean regionHasAnyHighlight(String dimId, int regionX, int regionZ) {
        Long2ByteOpenHashMap map = ChunkBandClientCache.getRawDimensionMap(dimId);
        if (map == null || map.isEmpty()) {
            return false;
        }
        int baseCx = regionX * REGION_SIZE_CHUNKS;
        int baseCz = regionZ * REGION_SIZE_CHUNKS;
        for (int dz = 0; dz < REGION_SIZE_CHUNKS; dz++) {
            int cz = baseCz + dz;
            for (int dx = 0; dx < REGION_SIZE_CHUNKS; dx++) {
                byte b = map.get(CivilMapTintLogic.packChunk(baseCx + dx, cz));
                if (isOverlayBand(b)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int regionContentHash(String dimId, int regionX, int regionZ) {
        Long2ByteOpenHashMap map = ChunkBandClientCache.getRawDimensionMap(dimId);
        if (map == null || map.isEmpty()) {
            return 1;
        }
        int h = 1;
        int baseCx = regionX * REGION_SIZE_CHUNKS;
        int baseCz = regionZ * REGION_SIZE_CHUNKS;
        for (int dz = 0; dz < REGION_SIZE_CHUNKS; dz++) {
            int cz = baseCz + dz;
            for (int dx = 0; dx < REGION_SIZE_CHUNKS; dx++) {
                h = 31 * h + map.get(CivilMapTintLogic.packChunk(baseCx + dx, cz));
            }
        }
        return h;
    }

    public static void fillUniformChunkColors(int[] resultStore, int argb) {
        resultStore[0] = argb;
        resultStore[1] = argb;
        resultStore[2] = argb;
        resultStore[3] = argb;
        resultStore[4] = argb;
    }
}
