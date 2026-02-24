package civil.civilization;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Objects;

/**
 * Voxel chunk index: custom division with all XYZ axes using 16 blocks per chunk.
 *
 * <p>X/Z consistent with MC chunk (cx = x>>4, cz = z>>4). Y extends up and down from 0:
 * sy = Math.floorDiv(y, 16), so 0 belongs to slice 0 [0,15], -1 belongs to slice -1 [-16,-1],
 * maximizes compatibility with different world types (including mod-modified bounds). End slices may be less than 16 blocks after dimension boundary clamp.
 *
 * See project docs/CIVIL_MAP_PLAN.md for details.
 */
public final class VoxelChunkKey {

    private static final int SIZE = 16;

    private final int cx;
    private final int cz;
    private final int sy;

    /** Dimension-independent bounds, calculated once during construction to avoid repeated getWorldMin/Max calculations. */
    private final int blockMinX;
    private final int blockMaxX;
    private final int blockMinZ;
    private final int blockMaxZ;
    private final int idealMinY;
    private final int idealMaxY;

    public VoxelChunkKey(int cx, int cz, int sy) {
        this.cx = cx;
        this.cz = cz;
        this.sy = sy;
        this.blockMinX = cx * SIZE;
        this.blockMaxX = cx * SIZE + SIZE - 1;
        this.blockMinZ = cz * SIZE;
        this.blockMaxZ = cz * SIZE + SIZE - 1;
        this.idealMinY = sy * SIZE;
        this.idealMaxY = sy * SIZE + SIZE - 1;
    }

    public int getCx() {
        return cx;
    }

    public int getCz() {
        return cz;
    }

    public int getSy() {
        return sy;
    }

    /** Single dimension query + Y clamp to get min/max, caller only needs to call once when both values are needed. */
    public record WorldBounds(BlockPos min, BlockPos max) {}

    /** Use pre-calculated dimension Y range to avoid 27 repeated dimensionType queries under the same world. */
    public WorldBounds getWorldBounds(ServerLevel world, int dimMinY, int dimMaxY) {
        int minY = Math.max(idealMinY, dimMinY);
        int maxY = Math.min(idealMaxY, dimMaxY);
        return new WorldBounds(
                new BlockPos(blockMinX, minY, blockMinZ),
                new BlockPos(blockMaxX, maxY, blockMaxZ));
    }

    public WorldBounds getWorldBounds(ServerLevel world) {
        DimensionType dim = world.dimensionType();
        return getWorldBounds(world, dim.minY(), dim.minY() + dim.height() - 1);
    }

    /**
     * Get the voxel chunk key that this world position belongs to (dimension-independent).
     * Y division: extends from 0, sy = floorDiv(y, 16).
     */
    public static VoxelChunkKey from(BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        int sy = Math.floorDiv(pos.getY(), SIZE);
        return new VoxelChunkKey(cx, cz, sy);
    }

    /**
     * Get key from world coordinates and dimension (equivalent to from(pos), kept overload for convenience with level).
     */
    public static VoxelChunkKey from(ServerLevel world, BlockPos pos) {
        return from(pos);
    }

    /**
     * Returns minimum and maximum BlockPos of this voxel chunk in world (clamped to dimension).
     * If both min and max are needed, prefer {@link #getWorldBounds(ServerLevel)} to do only one dimension query.
     */
    public BlockPos getWorldMin(ServerLevel world) {
        return getWorldBounds(world).min();
    }

    public BlockPos getWorldMax(ServerLevel world) {
        return getWorldBounds(world).max();
    }

    /** Whether this key is valid in current dimension (Y has intersection with dimension). Can pass pre-calculated dimMinY/dimMaxY to avoid repeated queries. */
    public boolean isValidIn(ServerLevel world, int dimMinY, int dimMaxY) {
        return idealMaxY >= dimMinY && idealMinY <= dimMaxY;
    }

    public boolean isValidIn(ServerLevel world) {
        DimensionType dim = world.dimensionType();
        return isValidIn(world, dim.minY(), dim.minY() + dim.height() - 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VoxelChunkKey that = (VoxelChunkKey) o;
        return cx == that.cx && cz == that.cz && sy == that.sy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cx, cz, sy);
    }

    /**
     * Return a new key offset by the given deltas.
     * Used for iterating detection ranges around a center VC.
     */
    public VoxelChunkKey offset(int dx, int dz, int dy) {
        return new VoxelChunkKey(cx + dx, cz + dz, sy + dy);
    }

    @Override
    public String toString() {
        return "VoxelChunkKey{cx=" + cx + ",cz=" + cz + ",sy=" + sy + "}";
    }
}
