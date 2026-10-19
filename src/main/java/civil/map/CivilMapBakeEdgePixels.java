package civil.map;

import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Region-border edge detection for baked map colors (aligned with former client edge heuristics). */
public final class CivilMapBakeEdgePixels {

    @FunctionalInterface
    public interface MapTintProbe {
        byte tintAt(int mapX, int mapY);
    }

    private CivilMapBakeEdgePixels() {}

    public static boolean isEdgePixel(
            int x,
            int y,
            byte band,
            MapItemSavedData data,
            CivilMapTintPaddingState padding,
            MapTintProbe probe) {
        if (band != CivilMapTintPalette.HIGH
                && band != CivilMapTintPalette.MONSTER
                && band != CivilMapTintPalette.ZONE) {
            return false;
        }
        return neighborContributes(x, y, x - 1, y, band, data, padding, probe)
                || neighborContributes(x, y, x + 1, y, band, data, padding, probe)
                || neighborContributes(x, y, x, y - 1, band, data, padding, probe)
                || neighborContributes(x, y, x, y + 1, band, data, padding, probe)
                || neighborContributes(x, y, x - 1, y - 1, band, data, padding, probe)
                || neighborContributes(x, y, x + 1, y - 1, band, data, padding, probe)
                || neighborContributes(x, y, x - 1, y + 1, band, data, padding, probe)
                || neighborContributes(x, y, x + 1, y + 1, band, data, padding, probe);
    }

    private static boolean neighborContributes(
            int x,
            int y,
            int nx,
            int ny,
            byte band,
            MapItemSavedData data,
            CivilMapTintPaddingState p,
            MapTintProbe probe) {
        if (nx >= 0 && nx < 128 && ny >= 0 && ny < 128) {
            int idx = nx + ny * 128;
            if (!CivilMapMapExploration.isExplored(data, idx)) {
                return false;
            }
            return probe.tintAt(nx, ny) != band;
        }
        return paddingNeighborDiffers(x, y, nx, ny, band, p);
    }

    private static boolean paddingNeighborDiffers(
            int x, int y, int nx, int ny, byte band, CivilMapTintPaddingState p) {
        if (p == null) {
            return false;
        }
        int dx = nx - x;
        int dy = ny - y;
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || (dx == 0 && dy == 0)) {
            return false;
        }
        if (nx == -1 && ny == -1 && x == 0 && y == 0) {
            return p.cornerKnown[0] != 0 && p.cornerTint[0] != band;
        }
        if (nx == 128 && ny == -1 && x == 127 && y == 0) {
            return p.cornerKnown[1] != 0 && p.cornerTint[1] != band;
        }
        if (nx == -1 && ny == 128 && x == 0 && y == 127) {
            return p.cornerKnown[2] != 0 && p.cornerTint[2] != band;
        }
        if (nx == 128 && ny == 128 && x == 127 && y == 127) {
            return p.cornerKnown[3] != 0 && p.cornerTint[3] != band;
        }
        if (nx == -1 && ny >= 0 && ny < CivilMapTintPaddingState.EDGE && x == 0) {
            return p.leftKnown[ny] != 0 && p.leftTint[ny] != band;
        }
        if (nx == 128 && ny >= 0 && ny < CivilMapTintPaddingState.EDGE && x == 127) {
            return p.rightKnown[ny] != 0 && p.rightTint[ny] != band;
        }
        if (ny == -1 && nx >= 0 && nx < CivilMapTintPaddingState.EDGE && y == 0) {
            return p.topKnown[nx] != 0 && p.topTint[nx] != band;
        }
        if (ny == 128 && nx >= 0 && nx < CivilMapTintPaddingState.EDGE && y == 127) {
            return p.bottomKnown[nx] != 0 && p.bottomTint[nx] != band;
        }
        return false;
    }
}
