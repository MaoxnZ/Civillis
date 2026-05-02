package civil.map;

import civil.config.CivilConfig;
import net.minecraft.world.level.material.MapColor;

/**
 * Server-side bake of civilization tint into vanilla map palette bytes.
 *
 * <p>Simple model: fixed semantic targets per band — fill lerps terrain toward the band color; edge is a
 * uniform blend independent of underlying terrain (stable borders at any map scale). Tune RGB constants and
 * {@link CivilConfig} alphas only; no contrast / nudge / second pass.
 */
public final class CivilMapColorBake {

    private static final int[] MAP_BYTE_ARGB = new int[256];

    /** HIGH edge: near-white neutral → white (uniform; not terrain-dependent). */
    private static final int HIGH_EDGE_NR = 255;
    private static final int HIGH_EDGE_NG = 255;
    private static final int HIGH_EDGE_NB = 255;

    private static final int MONSTER_EDGE_NR = 160;
    private static final int MONSTER_EDGE_NG = 60;
    private static final int MONSTER_EDGE_NB = 190;

    private static final int ZONE_EDGE_NR = 255;
    private static final int ZONE_EDGE_NG = 140;
    private static final int ZONE_EDGE_NB = 90;

    private static final int HIGH_TR = 240;
    private static final int HIGH_TG = 240;
    private static final int HIGH_TB = 240;

    private static final int MONSTER_TR = 150;
    private static final int MONSTER_TG = 55;
    private static final int MONSTER_TB = 180;

    private static final int ZONE_TR = 240;
    private static final int ZONE_TG = 130;
    private static final int ZONE_TB = 85;

    private static int lastHighEdgeAlphaKey = Integer.MIN_VALUE;
    private static byte cachedHighEdgeMapByte;

    private static int lastMonsterEdgeAlphaKey = Integer.MIN_VALUE;
    private static byte cachedMonsterEdgeMapByte;

    private static int lastZoneEdgeAlphaKey = Integer.MIN_VALUE;
    private static byte cachedZoneEdgeMapByte;

    static {
        for (int i = 0; i < 256; i++) {
            MAP_BYTE_ARGB[i] = MapColor.getColorFromPackedId(i);
        }
    }

    private CivilMapColorBake() {}

    public static byte blendPackedMapByte(byte mapByte, byte tintBand, boolean edge) {
        if (tintBand != CivilMapTintPalette.HIGH
                && tintBand != CivilMapTintPalette.MONSTER
                && tintBand != CivilMapTintPalette.ZONE) {
            return mapByte;
        }
        int alpha = clamp255(alphaForBand(tintBand, edge));
        if (edge) {
            return edgeUniformMapByte(tintBand, alpha);
        }
        return blendFill(mapByte, tintBand, alpha);
    }

    private static int alphaForBand(byte tintBand, boolean edge) {
        return switch (tintBand) {
            case CivilMapTintPalette.HIGH -> edge ? CivilConfig.mapTintHighEdgeAlpha : CivilConfig.mapTintHighFillAlpha;
            case CivilMapTintPalette.MONSTER -> edge ? CivilConfig.mapTintMonsterEdgeAlpha : CivilConfig.mapTintMonsterFillAlpha;
            case CivilMapTintPalette.ZONE -> edge ? CivilConfig.mapTintZoneEdgeAlpha : CivilConfig.mapTintZoneFillAlpha;
            default -> 0;
        };
    }

    private static byte blendFill(byte originalMapByte, byte tintBand, int alpha) {
        int base = MAP_BYTE_ARGB[originalMapByte & 0xFF];
        int r = (base >> 16) & 0xFF;
        int g = (base >> 8) & 0xFF;
        int b = base & 0xFF;
        int tr;
        int tg;
        int tb;
        switch (tintBand) {
            case CivilMapTintPalette.HIGH -> {
                tr = HIGH_TR;
                tg = HIGH_TG;
                tb = HIGH_TB;
            }
            case CivilMapTintPalette.MONSTER -> {
                tr = MONSTER_TR;
                tg = MONSTER_TG;
                tb = MONSTER_TB;
            }
            case CivilMapTintPalette.ZONE -> {
                tr = ZONE_TR;
                tg = ZONE_TG;
                tb = ZONE_TB;
            }
            default -> {
                return originalMapByte;
            }
        }
        int blended = lerpRgb(r, g, b, tr, tg, tb, alpha);
        return nearestMapByte(blended);
    }

    private static byte edgeUniformMapByte(byte tintBand, int alpha) {
        return switch (tintBand) {
            case CivilMapTintPalette.HIGH -> highEdgeUniformMapByte(alpha);
            case CivilMapTintPalette.MONSTER -> monsterEdgeUniformMapByte(alpha);
            case CivilMapTintPalette.ZONE -> zoneEdgeUniformMapByte(alpha);
            default -> (byte) 0;
        };
    }

    private static byte highEdgeUniformMapByte(int alpha) {
        if (alpha != lastHighEdgeAlphaKey) {
            lastHighEdgeAlphaKey = alpha;
            int blended = lerpRgb(HIGH_EDGE_NR, HIGH_EDGE_NG, HIGH_EDGE_NB, HIGH_TR, HIGH_TG, HIGH_TB, alpha);
            cachedHighEdgeMapByte = nearestMapByte(blended);
        }
        return cachedHighEdgeMapByte;
    }

    private static byte monsterEdgeUniformMapByte(int alpha) {
        if (alpha != lastMonsterEdgeAlphaKey) {
            lastMonsterEdgeAlphaKey = alpha;
            int blended = lerpRgb(MONSTER_EDGE_NR, MONSTER_EDGE_NG, MONSTER_EDGE_NB, MONSTER_TR, MONSTER_TG, MONSTER_TB, alpha);
            cachedMonsterEdgeMapByte = nearestMapByte(blended);
        }
        return cachedMonsterEdgeMapByte;
    }

    private static byte zoneEdgeUniformMapByte(int alpha) {
        if (alpha != lastZoneEdgeAlphaKey) {
            lastZoneEdgeAlphaKey = alpha;
            int blended = lerpRgb(ZONE_EDGE_NR, ZONE_EDGE_NG, ZONE_EDGE_NB, ZONE_TR, ZONE_TG, ZONE_TB, alpha);
            cachedZoneEdgeMapByte = nearestMapByte(blended);
        }
        return cachedZoneEdgeMapByte;
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
