package civil.civilization.structure;

import java.util.Objects;

/**
 * L3 cache block index: horizontal 9×9, vertical 3-layer voxel chunk aggregation.
 *
 * <p>Coordinate naming aligned with L1 (VoxelChunkKey):
 * <ul>
 *   <li>c3x = Math.floorDiv(cx, 9)</li>
 *   <li>c3z = Math.floorDiv(cz, 9)</li>
 *   <li>s3y = Math.floorDiv(sy, 3)</li>
 * </ul>
 *
 * <p>L1 range covered by one L3 block:
 * <ul>
 *   <li>cx ∈ [c3x*9, c3x*9+8]</li>
 *   <li>cz ∈ [c3z*9, c3z*9+8]</li>
 *   <li>sy ∈ [s3y*3, s3y*3+2]</li>
 * </ul>
 */
public final class L3Key {

    /** L3 block horizontal side length (in voxel chunks). */
    public static final int SIZE_XZ = 9;
    /** L3 block vertical layer count. */
    public static final int SIZE_Y = 3;
    /** Number of L1 cells contained in L3 block. */
    public static final int CELL_COUNT = SIZE_XZ * SIZE_XZ * SIZE_Y;  // 243

    private final int c3x;
    private final int c3z;
    private final int s3y;

    public L3Key(int c3x, int c3z, int s3y) {
        this.c3x = c3x;
        this.c3z = c3z;
        this.s3y = s3y;
    }

    /**
     * Get the L3Key that the L1 coordinates (cx, cz, sy) belong to.
     */
    public static L3Key from(int cx, int cz, int sy) {
        return new L3Key(
                Math.floorDiv(cx, SIZE_XZ),
                Math.floorDiv(cz, SIZE_XZ),
                Math.floorDiv(sy, SIZE_Y)
        );
    }

    /**
     * Get the L3Key that the VoxelChunkKey belongs to.
     */
    public static L3Key from(VoxelChunkKey l1) {
        return from(l1.getCx(), l1.getCz(), l1.getSy());
    }

    public int getC3x() {
        return c3x;
    }

    public int getC3z() {
        return c3z;
    }

    public int getS3y() {
        return s3y;
    }

    /**
     * Minimum cx of L1 range covered by this L3 block.
     */
    public int getMinCx() {
        return c3x * SIZE_XZ;
    }

    /**
     * Maximum cx of L1 range covered by this L3 block.
     */
    public int getMaxCx() {
        return c3x * SIZE_XZ + SIZE_XZ - 1;
    }

    /**
     * Minimum cz of L1 range covered by this L3 block.
     */
    public int getMinCz() {
        return c3z * SIZE_XZ;
    }

    /**
     * Maximum cz of L1 range covered by this L3 block.
     */
    public int getMaxCz() {
        return c3z * SIZE_XZ + SIZE_XZ - 1;
    }

    /**
     * Minimum sy of L1 range covered by this L3 block.
     */
    public int getMinSy() {
        return s3y * SIZE_Y;
    }

    /**
     * Maximum sy of L1 range covered by this L3 block.
     */
    public int getMaxSy() {
        return s3y * SIZE_Y + SIZE_Y - 1;
    }

    /**
     * Center voxel chunk of this L3 block (for distance calculation).
     */
    public VoxelChunkKey getCenterChunk() {
        return new VoxelChunkKey(
                c3x * SIZE_XZ + 4,  // Center cx (9/2 = 4)
                c3z * SIZE_XZ + 4,  // Center cz
                s3y * SIZE_Y + 1    // Center sy (3/2 = 1)
        );
    }

    /**
     * Calculate index (0~242) in this L3 block's detail array from L1 coordinates (cx, cz, sy).
     * 
     * <p>Formula: idx = (cx - minCx) + SIZE_XZ * (cz - minCz) + SIZE_XZ * SIZE_XZ * (sy - minSy)
     */
    public int l1ToIndex(int cx, int cz, int sy) {
        return (cx - getMinCx()) 
             + SIZE_XZ * (cz - getMinCz()) 
             + SIZE_XZ * SIZE_XZ * (sy - getMinSy());
    }

    /**
     * Calculate index in this L3 block's detail array from VoxelChunkKey.
     */
    public int l1ToIndex(VoxelChunkKey l1) {
        return l1ToIndex(l1.getCx(), l1.getCz(), l1.getSy());
    }

    /**
     * Reverse calculate L1 coordinates from detail array index (0~242).
     */
    public VoxelChunkKey indexToL1(int idx) {
        int localX = idx % SIZE_XZ;
        int localZ = (idx / SIZE_XZ) % SIZE_XZ;
        int localY = idx / (SIZE_XZ * SIZE_XZ);
        return new VoxelChunkKey(
                getMinCx() + localX,
                getMinCz() + localZ,
                getMinSy() + localY
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        L3Key l3Key = (L3Key) o;
        return c3x == l3Key.c3x && c3z == l3Key.c3z && s3y == l3Key.s3y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(c3x, c3z, s3y);
    }

    @Override
    public String toString() {
        return "L3Key{c3x=" + c3x + ",c3z=" + c3z + ",s3y=" + s3y + "}";
    }
}
