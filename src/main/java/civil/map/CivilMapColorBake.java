package civil.map;

import civil.config.CivilConfig;
import net.minecraft.world.level.material.MapColor;

/** Server-side bake of civilization tint into vanilla map palette bytes. */
public final class CivilMapColorBake {

    private static final int[] MAP_BYTE_ARGB = new int[256];

    /**
     * Fixed neutral for HIGH region borders: blend toward white from this parchment tone so the
     * nearest map palette entry does not depend on underlying terrain (avoids “broken” edges
     * across color boundaries after quantization).
     */
    private static final int HIGH_EDGE_NR = 200;
    private static final int HIGH_EDGE_NG = 198;
    private static final int HIGH_EDGE_NB = 190;

    /** Fixed neutral for MONSTER region borders toward the monster purple target. */
    private static final int MONSTER_EDGE_NR = 110;
    private static final int MONSTER_EDGE_NG = 95;
    private static final int MONSTER_EDGE_NB = 115;

    private static int lastHighEdgeAlphaKey = Integer.MIN_VALUE;
    private static byte cachedHighEdgeMapByte;

    private static int lastMonsterEdgeAlphaKey = Integer.MIN_VALUE;
    private static byte cachedMonsterEdgeMapByte;

    static {
        for (int i = 0; i < 256; i++) {
            MAP_BYTE_ARGB[i] = MapColor.getColorFromPackedId(i);
        }
    }

    private CivilMapColorBake() {}

    public static byte blendPackedMapByte(byte mapByte, byte tintBand, boolean edge) {
        if (tintBand != CivilMapTintPalette.HIGH && tintBand != CivilMapTintPalette.MONSTER) {
            return mapByte;
        }
        if (edge) {
            return tintBand == CivilMapTintPalette.HIGH ? highEdgeUniformMapByte() : monsterEdgeUniformMapByte();
        }
        int packed = mapByte & 0xFF;
        int base = MAP_BYTE_ARGB[packed];
        int blended = blendArgbFill(base, tintBand);
        return nearestMapByte(blended);
    }

    private static byte highEdgeUniformMapByte() {
        int a = CivilConfig.mapTintHighEdgeAlpha;
        if (a != lastHighEdgeAlphaKey) {
            lastHighEdgeAlphaKey = a;
            int blended = lerpRgb(HIGH_EDGE_NR, HIGH_EDGE_NG, HIGH_EDGE_NB, 255, 255, 255, a);
            cachedHighEdgeMapByte = nearestMapByte(blended);
        }
        return cachedHighEdgeMapByte;
    }

    private static byte monsterEdgeUniformMapByte() {
        int a = CivilConfig.mapTintMonsterEdgeAlpha;
        if (a != lastMonsterEdgeAlphaKey) {
            lastMonsterEdgeAlphaKey = a;
            int blended = lerpRgb(MONSTER_EDGE_NR, MONSTER_EDGE_NG, MONSTER_EDGE_NB, 160, 60, 190, a);
            cachedMonsterEdgeMapByte = nearestMapByte(blended);
        }
        return cachedMonsterEdgeMapByte;
    }

    private static int blendArgbFill(int baseArgb, byte band) {
        int r = (baseArgb >> 16) & 0xFF;
        int g = (baseArgb >> 8) & 0xFF;
        int b = baseArgb & 0xFF;
        return switch (band) {
            case CivilMapTintPalette.HIGH ->
                    lerpRgb(r, g, b, 255, 255, 255, CivilConfig.mapTintHighFillAlpha);
            case CivilMapTintPalette.MONSTER ->
                    lerpRgb(r, g, b, 160, 60, 190, CivilConfig.mapTintMonsterFillAlpha);
            default -> baseArgb;
        };
    }

    private static int lerpRgb(int r, int g, int b, int tr, int tg, int tb, int alpha) {
        int nr = r + (tr - r) * alpha / 255;
        int ng = g + (tg - g) * alpha / 255;
        int nb = b + (tb - b) * alpha / 255;
        return 0xFF000000 | (clamp255(nr) << 16) | (clamp255(ng) << 8) | clamp255(nb);
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static byte nearestMapByte(int argb) {
        int br = (argb >> 16) & 0xFF;
        int bg = (argb >> 8) & 0xFF;
        int bb = argb & 0xFF;
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < 256; i++) {
            int c = MAP_BYTE_ARGB[i];
            int cr = (c >> 16) & 0xFF;
            int cg = (c >> 8) & 0xFF;
            int cb = c & 0xFF;
            long dr = br - cr;
            long dg = bg - cg;
            long db = bb - cb;
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return (byte) best;
    }
}
