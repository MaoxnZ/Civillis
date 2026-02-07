package civil.civilization.structure;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a box range in voxel chunk coordinates.
 * 
 * <p>Used to define core, brush, L2 area, L3 area etc. during imaginary layered computation.
 */
public final class ChunkBox {

    private final int minCx;
    private final int maxCx;
    private final int minCz;
    private final int maxCz;
    private final int minSy;
    private final int maxSy;

    public ChunkBox(int minCx, int maxCx, int minCz, int maxCz, int minSy, int maxSy) {
        this.minCx = minCx;
        this.maxCx = maxCx;
        this.minCz = minCz;
        this.maxCz = maxCz;
        this.minSy = minSy;
        this.maxSy = maxSy;
    }

    /**
     * Create box from center and radius.
     * 
     * @param cx Center X
     * @param cz Center Z
     * @param sy Center Y
     * @param rx X direction radius
     * @param rz Z direction radius
     * @param ry Y direction radius
     * @return Box [cx-rx, cx+rx] × [cz-rz, cz+rz] × [sy-ry, sy+ry]
     */
    public static ChunkBox centered(int cx, int cz, int sy, int rx, int rz, int ry) {
        return new ChunkBox(
                cx - rx, cx + rx,
                cz - rz, cz + rz,
                sy - ry, sy + ry
        );
    }

    /**
     * Create box from L2Key.
     */
    public static ChunkBox fromL2(L2Key key) {
        return new ChunkBox(
                key.getMinCx(), key.getMaxCx(),
                key.getMinCz(), key.getMaxCz(),
                key.getS2y(), key.getS2y()
        );
    }

    /**
     * Create box from L3Key.
     */
    public static ChunkBox fromL3(L3Key key) {
        return new ChunkBox(
                key.getMinCx(), key.getMaxCx(),
                key.getMinCz(), key.getMaxCz(),
                key.getMinSy(), key.getMaxSy()
        );
    }

    public int getMinCx() {
        return minCx;
    }

    public int getMaxCx() {
        return maxCx;
    }

    public int getMinCz() {
        return minCz;
    }

    public int getMaxCz() {
        return maxCz;
    }

    public int getMinSy() {
        return minSy;
    }

    public int getMaxSy() {
        return maxSy;
    }

    /**
     * Box X direction size (in chunks).
     */
    public int sizeX() {
        return maxCx - minCx + 1;
    }

    /**
     * Box Z direction size (in chunks).
     */
    public int sizeZ() {
        return maxCz - minCz + 1;
    }

    /**
     * Box Y direction size (in chunks).
     */
    public int sizeY() {
        return maxSy - minSy + 1;
    }

    /**
     * Total number of chunks contained in box.
     */
    public int volume() {
        return sizeX() * sizeZ() * sizeY();
    }

    /**
     * Box center chunk coordinates.
     */
    public VoxelChunkKey center() {
        return new VoxelChunkKey(
                (minCx + maxCx) / 2,
                (minCz + maxCz) / 2,
                (minSy + maxSy) / 2
        );
    }

    /**
     * Check if a chunk is inside the box.
     */
    public boolean contains(int cx, int cz, int sy) {
        return cx >= minCx && cx <= maxCx
                && cz >= minCz && cz <= maxCz
                && sy >= minSy && sy <= maxSy;
    }

    /**
     * Check if a chunk is inside the box.
     */
    public boolean contains(VoxelChunkKey key) {
        return contains(key.getCx(), key.getCz(), key.getSy());
    }

    /**
     * O(1) calculate intersection chunk count of two boxes.
     */
    public int countOverlap(ChunkBox other) {
        int overlapX = Math.max(0, Math.min(maxCx, other.maxCx) - Math.max(minCx, other.minCx) + 1);
        int overlapZ = Math.max(0, Math.min(maxCz, other.maxCz) - Math.max(minCz, other.minCz) + 1);
        int overlapY = Math.max(0, Math.min(maxSy, other.maxSy) - Math.max(minSy, other.minSy) + 1);
        return overlapX * overlapZ * overlapY;
    }

    /**
     * Iterate over all chunks in the box.
     */
    public List<VoxelChunkKey> allChunks() {
        List<VoxelChunkKey> result = new ArrayList<>(volume());
        for (int sy = minSy; sy <= maxSy; sy++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                for (int cx = minCx; cx <= maxCx; cx++) {
                    result.add(new VoxelChunkKey(cx, cz, sy));
                }
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "ChunkBox{cx=[" + minCx + "," + maxCx + "],cz=[" + minCz + "," + maxCz + "],sy=[" + minSy + "," + maxSy + "]}";
    }
}
