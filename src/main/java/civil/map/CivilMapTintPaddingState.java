package civil.map;

import java.util.Arrays;

/**
 * One-cell-wide padding outside the 128×128 map: four edges (128 slots each, tint + known) and four diagonal
 * corners beyond the map (NW, NE, SW, SE). Used for rim blending without mirroring in-bounds pixels.
 */
public final class CivilMapTintPaddingState {

    public static final int EDGE = 128;
    public static final int FLAG_LEFT = 1;
    public static final int FLAG_RIGHT = 2;
    public static final int FLAG_TOP = 4;
    public static final int FLAG_BOTTOM = 8;
    public static final int FLAG_CORNERS = 16;
    public static final int FLAG_ALL = FLAG_LEFT | FLAG_RIGHT | FLAG_TOP | FLAG_BOTTOM | FLAG_CORNERS;

    /** Serialized / NBT blob: 4×(128 tint + 128 known) + 4 corner tint + 4 corner known. */
    public static final int BLOB_SIZE = EDGE * 8 + 8;

    private static final int OFF_LT = 0;
    private static final int OFF_LK = EDGE;
    private static final int OFF_RT = EDGE * 2;
    private static final int OFF_RK = EDGE * 3;
    private static final int OFF_TT = EDGE * 4;
    private static final int OFF_TK = EDGE * 5;
    private static final int OFF_BT = EDGE * 6;
    private static final int OFF_BK = EDGE * 7;
    private static final int OFF_CT = EDGE * 8;
    private static final int OFF_CK = EDGE * 8 + 4;

    public final byte[] leftTint = new byte[EDGE];
    public final byte[] leftKnown = new byte[EDGE];
    public final byte[] rightTint = new byte[EDGE];
    public final byte[] rightKnown = new byte[EDGE];
    public final byte[] topTint = new byte[EDGE];
    public final byte[] topKnown = new byte[EDGE];
    public final byte[] bottomTint = new byte[EDGE];
    public final byte[] bottomKnown = new byte[EDGE];
    public final byte[] cornerTint = new byte[4];
    public final byte[] cornerKnown = new byte[4];

    public CivilMapTintPaddingState() {
        Arrays.fill(leftTint, CivilMapTintPalette.UNKNOWN);
        Arrays.fill(rightTint, CivilMapTintPalette.UNKNOWN);
        Arrays.fill(topTint, CivilMapTintPalette.UNKNOWN);
        Arrays.fill(bottomTint, CivilMapTintPalette.UNKNOWN);
        Arrays.fill(cornerTint, CivilMapTintPalette.UNKNOWN);
    }

    public void clearToUnknown() {
        Arrays.fill(leftTint, CivilMapTintPalette.UNKNOWN);
        Arrays.fill(leftKnown, (byte) 0);
        Arrays.fill(rightTint, CivilMapTintPalette.UNKNOWN);
        Arrays.fill(rightKnown, (byte) 0);
        Arrays.fill(topTint, CivilMapTintPalette.UNKNOWN);
        Arrays.fill(topKnown, (byte) 0);
        Arrays.fill(bottomTint, CivilMapTintPalette.UNKNOWN);
        Arrays.fill(bottomKnown, (byte) 0);
        Arrays.fill(cornerTint, CivilMapTintPalette.UNKNOWN);
        Arrays.fill(cornerKnown, (byte) 0);
    }

    public byte[] toBlob() {
        byte[] b = new byte[BLOB_SIZE];
        System.arraycopy(leftTint, 0, b, OFF_LT, EDGE);
        System.arraycopy(leftKnown, 0, b, OFF_LK, EDGE);
        System.arraycopy(rightTint, 0, b, OFF_RT, EDGE);
        System.arraycopy(rightKnown, 0, b, OFF_RK, EDGE);
        System.arraycopy(topTint, 0, b, OFF_TT, EDGE);
        System.arraycopy(topKnown, 0, b, OFF_TK, EDGE);
        System.arraycopy(bottomTint, 0, b, OFF_BT, EDGE);
        System.arraycopy(bottomKnown, 0, b, OFF_BK, EDGE);
        System.arraycopy(cornerTint, 0, b, OFF_CT, 4);
        System.arraycopy(cornerKnown, 0, b, OFF_CK, 4);
        return b;
    }

    public void fromBlob(byte[] b) {
        if (b == null || b.length != BLOB_SIZE) {
            clearToUnknown();
            return;
        }
        System.arraycopy(b, OFF_LT, leftTint, 0, EDGE);
        System.arraycopy(b, OFF_LK, leftKnown, 0, EDGE);
        System.arraycopy(b, OFF_RT, rightTint, 0, EDGE);
        System.arraycopy(b, OFF_RK, rightKnown, 0, EDGE);
        System.arraycopy(b, OFF_TT, topTint, 0, EDGE);
        System.arraycopy(b, OFF_TK, topKnown, 0, EDGE);
        System.arraycopy(b, OFF_BT, bottomTint, 0, EDGE);
        System.arraycopy(b, OFF_BK, bottomKnown, 0, EDGE);
        System.arraycopy(b, OFF_CT, cornerTint, 0, 4);
        System.arraycopy(b, OFF_CK, cornerKnown, 0, 4);
    }

    public static int networkBlobLength(int flags) {
        int n = 0;
        if ((flags & FLAG_LEFT) != 0) {
            n += EDGE * 2;
        }
        if ((flags & FLAG_RIGHT) != 0) {
            n += EDGE * 2;
        }
        if ((flags & FLAG_TOP) != 0) {
            n += EDGE * 2;
        }
        if ((flags & FLAG_BOTTOM) != 0) {
            n += EDGE * 2;
        }
        if ((flags & FLAG_CORNERS) != 0) {
            n += 8;
        }
        return n;
    }

    /** Writes strips in order L, R, T, B, corners (same order as {@link #readNetworkBlob}). */
    public void writeNetworkBlob(int flags, byte[] out, int offset) {
        int p = offset;
        if ((flags & FLAG_LEFT) != 0) {
            System.arraycopy(leftTint, 0, out, p, EDGE);
            p += EDGE;
            System.arraycopy(leftKnown, 0, out, p, EDGE);
            p += EDGE;
        }
        if ((flags & FLAG_RIGHT) != 0) {
            System.arraycopy(rightTint, 0, out, p, EDGE);
            p += EDGE;
            System.arraycopy(rightKnown, 0, out, p, EDGE);
            p += EDGE;
        }
        if ((flags & FLAG_TOP) != 0) {
            System.arraycopy(topTint, 0, out, p, EDGE);
            p += EDGE;
            System.arraycopy(topKnown, 0, out, p, EDGE);
            p += EDGE;
        }
        if ((flags & FLAG_BOTTOM) != 0) {
            System.arraycopy(bottomTint, 0, out, p, EDGE);
            p += EDGE;
            System.arraycopy(bottomKnown, 0, out, p, EDGE);
            p += EDGE;
        }
        if ((flags & FLAG_CORNERS) != 0) {
            System.arraycopy(cornerTint, 0, out, p, 4);
            p += 4;
            System.arraycopy(cornerKnown, 0, out, p, 4);
        }
    }

    public void readNetworkBlob(int flags, byte[] in, int offset) {
        int p = offset;
        if ((flags & FLAG_LEFT) != 0) {
            System.arraycopy(in, p, leftTint, 0, EDGE);
            p += EDGE;
            System.arraycopy(in, p, leftKnown, 0, EDGE);
            p += EDGE;
        }
        if ((flags & FLAG_RIGHT) != 0) {
            System.arraycopy(in, p, rightTint, 0, EDGE);
            p += EDGE;
            System.arraycopy(in, p, rightKnown, 0, EDGE);
            p += EDGE;
        }
        if ((flags & FLAG_TOP) != 0) {
            System.arraycopy(in, p, topTint, 0, EDGE);
            p += EDGE;
            System.arraycopy(in, p, topKnown, 0, EDGE);
            p += EDGE;
        }
        if ((flags & FLAG_BOTTOM) != 0) {
            System.arraycopy(in, p, bottomTint, 0, EDGE);
            p += EDGE;
            System.arraycopy(in, p, bottomKnown, 0, EDGE);
            p += EDGE;
        }
        if ((flags & FLAG_CORNERS) != 0) {
            System.arraycopy(in, p, cornerTint, 0, 4);
            p += 4;
            System.arraycopy(in, p, cornerKnown, 0, 4);
        }
    }

    public void copyFrom(CivilMapTintPaddingState o) {
        System.arraycopy(o.leftTint, 0, leftTint, 0, EDGE);
        System.arraycopy(o.leftKnown, 0, leftKnown, 0, EDGE);
        System.arraycopy(o.rightTint, 0, rightTint, 0, EDGE);
        System.arraycopy(o.rightKnown, 0, rightKnown, 0, EDGE);
        System.arraycopy(o.topTint, 0, topTint, 0, EDGE);
        System.arraycopy(o.topKnown, 0, topKnown, 0, EDGE);
        System.arraycopy(o.bottomTint, 0, bottomTint, 0, EDGE);
        System.arraycopy(o.bottomKnown, 0, bottomKnown, 0, EDGE);
        System.arraycopy(o.cornerTint, 0, cornerTint, 0, 4);
        System.arraycopy(o.cornerKnown, 0, cornerKnown, 0, 4);
    }
}
