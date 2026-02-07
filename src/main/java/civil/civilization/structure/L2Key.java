package civil.civilization.structure;

import java.util.Objects;

/**
 * L2 cache block index: horizontal 3×3, vertical 1-layer voxel chunk aggregation.
 *
 * <p>Coordinate naming aligned with L1 (VoxelChunkKey):
 * <ul>
 *   <li>c2x = Math.floorDiv(cx, 3)</li>
 *   <li>c2z = Math.floorDiv(cz, 3)</li>
 *   <li>s2y = sy (L2 does not aggregate vertically, same as L1)</li>
 * </ul>
 *
 * <p>L1 range covered by one L2 block:
 * <ul>
 *   <li>cx ∈ [c2x*3, c2x*3+2]</li>
 *   <li>cz ∈ [c2z*3, c2z*3+2]</li>
 *   <li>sy = s2y</li>
 * </ul>
 */
public final class L2Key {

    /** L2 block horizontal side length (in voxel chunks). */
    public static final int SIZE_XZ = 3;
    /** L2 block vertical layer count. */
    public static final int SIZE_Y = 1;
    /** Number of L1 cells contained in L2 block. */
    public static final int CELL_COUNT = SIZE_XZ * SIZE_XZ * SIZE_Y;  // 9

    private final int c2x;
    private final int c2z;
    private final int s2y;

    public L2Key(int c2x, int c2z, int s2y) {
        this.c2x = c2x;
        this.c2z = c2z;
        this.s2y = s2y;
    }

    /**
     * Get the L2Key that the L1 coordinates (cx, cz, sy) belong to.
     */
    public static L2Key from(int cx, int cz, int sy) {
        return new L2Key(
                Math.floorDiv(cx, SIZE_XZ),
                Math.floorDiv(cz, SIZE_XZ),
                sy
        );
    }

    /**
     * Get the L2Key that the VoxelChunkKey belongs to.
     */
    public static L2Key from(VoxelChunkKey l1) {
        return from(l1.getCx(), l1.getCz(), l1.getSy());
    }

    public int getC2x() {
        return c2x;
    }

    public int getC2z() {
        return c2z;
    }

    public int getS2y() {
        return s2y;
    }

    /**
     * Minimum cx of L1 range covered by this L2 block.
     */
    public int getMinCx() {
        return c2x * SIZE_XZ;
    }

    /**
     * Maximum cx of L1 range covered by this L2 block.
     */
    public int getMaxCx() {
        return c2x * SIZE_XZ + SIZE_XZ - 1;
    }

    /**
     * Minimum cz of L1 range covered by this L2 block.
     */
    public int getMinCz() {
        return c2z * SIZE_XZ;
    }

    /**
     * Maximum cz of L1 range covered by this L2 block.
     */
    public int getMaxCz() {
        return c2z * SIZE_XZ + SIZE_XZ - 1;
    }

    /**
     * Center voxel chunk of this L2 block (for distance calculation).
     */
    public VoxelChunkKey getCenterChunk() {
        return new VoxelChunkKey(
                c2x * SIZE_XZ + 1,  // Center cx
                c2z * SIZE_XZ + 1,  // Center cz
                s2y
        );
    }

    /**
     * Calculate index (0~8) in this L2 block's detail array from L1 coordinates (cx, cz, sy).
     * 
     * <p>Formula: idx = (cx - minCx) + SIZE_XZ * (cz - minCz)
     */
    public int l1ToIndex(int cx, int cz, int sy) {
        return (cx - getMinCx()) + SIZE_XZ * (cz - getMinCz());
    }

    /**
     * Calculate index in this L2 block's detail array from VoxelChunkKey.
     */
    public int l1ToIndex(VoxelChunkKey l1) {
        return l1ToIndex(l1.getCx(), l1.getCz(), l1.getSy());
    }

    /**
     * Reverse calculate L1 coordinates from detail array index (0~8).
     */
    public VoxelChunkKey indexToL1(int idx) {
        int localX = idx % SIZE_XZ;
        int localZ = idx / SIZE_XZ;
        return new VoxelChunkKey(
                getMinCx() + localX,
                getMinCz() + localZ,
                s2y
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        L2Key l2Key = (L2Key) o;
        return c2x == l2Key.c2x && c2z == l2Key.c2z && s2y == l2Key.s2y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(c2x, c2z, s2y);
    }

    @Override
    public String toString() {
        return "L2Key{c2x=" + c2x + ",c2z=" + c2z + ",s2y=" + s2y + "}";
    }
}
