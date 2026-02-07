package civil.civilization.structure;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/**
 * Voxel region: describes raw block data within a continuous cuboid region.
 *
 * <p>Three-dimensional matrix structure, stores block states in neighborhood; scanning strategy responsible for filling/updating,
 * civilization operators only depend on getBlock/setBlock provided by this class to analyze the region.
 */
public final class VoxelRegion {

    private final BlockPos min;
    private final BlockPos max;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final BlockState[] blocks;

    public VoxelRegion(BlockPos min, BlockPos max) {
        this.min = min;
        this.max = max;
        this.minX = min.getX();
        this.minY = min.getY();
        this.minZ = min.getZ();
        this.sizeX = max.getX() - minX + 1;
        this.sizeY = max.getY() - minY + 1;
        this.sizeZ = max.getZ() - minZ + 1;
        this.blocks = new BlockState[sizeX * sizeY * sizeZ];
    }

    /** Region minimum world coordinates (inclusive). */
    public BlockPos getMin() {
        return min;
    }

    /** Region maximum world coordinates (inclusive). */
    public BlockPos getMax() {
        return max;
    }

    /**
     * Returns block state at specified world coordinates.
     * Input coordinates must be within [min, max] range.
     */
    public BlockState getBlock(BlockPos pos) {
        int x = pos.getX() - minX;
        int y = pos.getY() - minY;
        int z = pos.getZ() - minZ;
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            throw new IndexOutOfBoundsException("Position outside region: " + pos);
        }
        int idx = x + sizeX * (z + sizeZ * y);
        return blocks[idx];
    }

    /**
     * Write block state at specified world coordinates to region. Input coordinates must be within [min, max] range; scanning strategy fills data through this method.
     */
    public void setBlock(BlockPos pos, BlockState state) {
        int x = pos.getX() - minX;
        int y = pos.getY() - minY;
        int z = pos.getZ() - minZ;
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            throw new IndexOutOfBoundsException("Position outside region: " + pos);
        }
        int idx = x + sizeX * (z + sizeZ * y);
        blocks[idx] = state;
    }
}
