package civil.civilization.structure;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.DimensionType;

/**
 * Neighborhood sampler: based on the voxel chunk containing the center point, expands by radiusX/Z/Y,
 * calls {@link #sampleOneVoxelChunk} for each voxel chunk, returns {@link VoxelChunkRegion}.
 */
public final class SimpleNeighborhoodSampler implements NeighborhoodSampler {

    private final int radiusX;
    private final int radiusZ;
    private final int radiusY;

    /** Default 1×1×1, i.e., 3×3×3 voxel chunks. */
    public SimpleNeighborhoodSampler() {
        this(2, 2, 1);
    }

    /** Specify X/Z/Y three-direction voxel chunk expansion radius (each direction expands radius chunks, total (2*r+1) chunks). */
    public SimpleNeighborhoodSampler(int radiusX, int radiusZ, int radiusY) {
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.radiusY = radiusY;
    }

    @Override
    public VoxelChunkRegion sample(ServerWorld world, BlockPos centerPos) {
        VoxelChunkKey center = VoxelChunkKey.from(centerPos);
        int ccx = center.getCx();
        int ccz = center.getCz();
        int csy = center.getSy();
        VoxelChunkRegion chunkRegion = new VoxelChunkRegion();
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                for (int dy = -radiusY; dy <= radiusY; dy++) {
                    VoxelChunkKey key = new VoxelChunkKey(ccx + dx, ccz + dz, csy + dy);
                    VoxelRegion region = sampleOneVoxelChunk(world, key);
                    if (region != null) {
                        chunkRegion.put(key, region);
                    }
                }
            }
        }
        return chunkRegion;
    }

    @Override
    public VoxelRegion sampleOneVoxelChunk(ServerWorld world, VoxelChunkKey key) {
        DimensionType dim = world.getDimension();
        int dimMinY = dim.minY();
        int dimMaxY = dimMinY + dim.height() - 1;
        VoxelChunkKey.WorldBounds bounds = key.getWorldBounds(world, dimMinY, dimMaxY);
        if (bounds.min().getY() > bounds.max().getY()) {
            return null;
        }
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();
        VoxelRegion region = new VoxelRegion(min, max);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    mutable.set(x, y, z);
                    region.setBlock(mutable, world.getBlockState(mutable));
                }
            }
        }
        return region;
    }
}
