package civil.civilization.operator;

import civil.civilization.CivilValues;
import civil.civilization.structure.VoxelRegion;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SkullBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * Priority subdivision operator: caches within 16³ blocks, unconditional subdivision at first layer, corner suspicion (non-air=1) drives greedy recursion.
 * Civilization is only accumulated on first access; corner patterns are read from table for priority(c), not accumulated repeatedly.
 * Block judgment is delegated to {@link BlockCivilization}, normalization is defined in this class.
 */
public final class SubdivisionCivilizationOperator implements CivilizationOperator {

    private static final Logger OPERATOR_LOG = LoggerFactory.getLogger("civil-operator");

    /** Single block weight normalization factor, consistent with {@link SimpleCivilizationOperator}. */
    private static final double NORMALIZATION_FACTOR = 5.0;

    /** When true, prints the actual number of blocks computed for this voxel chunk after each computeScore (for plot_civil_operator_log.py parsing). */
    private static boolean debugLogging = false;

    public static void setDebugLogging(boolean enabled) {
        debugLogging = enabled;
    }

    @Override
    public double computeScore(VoxelRegion region) {
        return computeScore(region, null);
    }

    @Override
    public double computeScore(VoxelRegion region, CivilComputeContext context) {
        BlockPos min = region.getMin();
        BlockPos max = region.getMax();
        int minX = min.getX(), minY = min.getY(), minZ = min.getZ();
        int sizeX = max.getX() - minX + 1;
        int sizeY = max.getY() - minY + 1;
        int sizeZ = max.getZ() - minZ + 1;
        int volume = sizeX * sizeY * sizeZ;

        double[] civil = new double[volume];
        byte[] suspect = new byte[volume];
        Arrays.fill(civil, Double.NaN);

        double[] runningCivil = { 0.0 };
        boolean[] forceAllow = { false };

        processBox(region, min, max, minX, minY, minZ, sizeX, sizeY, sizeZ,
                civil, suspect, runningCivil, forceAllow, context, 0);

        if (debugLogging) {
            int visited = 0;
            for (int i = 0; i < civil.length; i++) {
                if (!Double.isNaN(civil[i])) visited++;
            }
            int cx = minX >> 4;
            int cz = minZ >> 4;
            int sy = Math.floorDiv(minY, 16);
            OPERATOR_LOG.info("[civil] operator_visited {} {} {} {}", cx, cz, sy, visited);
        }

        if (forceAllow[0]) {
            return CivilValues.FORCE_ALLOW_SCORE;
        }
        return Math.min(1.0, runningCivil[0] / NORMALIZATION_FACTOR);
    }

    /**
     * Process a sub-box: depth=0 unconditionally subdivides 16³ into 8×8³; depth≥1 first calculates 8 corner priorities, then subdivides if >0.
     */
    private void processBox(VoxelRegion region, BlockPos boxMin, BlockPos boxMax,
            int baseMinX, int baseMinY, int baseMinZ, int sizeX, int sizeY, int sizeZ,
            double[] civil, byte[] suspect, double[] runningCivil, boolean[] forceAllow,
            CivilComputeContext context, int depth) {
        if (forceAllow[0] || runningCivil[0] >= NORMALIZATION_FACTOR) {
            return;
        }

        int boxMinX = boxMin.getX(), boxMinY = boxMin.getY(), boxMinZ = boxMin.getZ();
        int boxMaxX = boxMax.getX(), boxMaxY = boxMax.getY(), boxMaxZ = boxMax.getZ();
        int boxSizeX = boxMaxX - boxMinX + 1;
        int boxSizeY = boxMaxY - boxMinY + 1;
        int boxSizeZ = boxMaxZ - boxMinZ + 1;

        if (depth == 0) {
            // First layer: unconditionally subdivide into 8 sub-boxes, in fixed order 0..7
            int midX = (boxMinX + boxMaxX) >> 1;
            int midY = (boxMinY + boxMaxY) >> 1;
            int midZ = (boxMinZ + boxMaxZ) >> 1;
            BlockPos[] subMins = {
                    new BlockPos(boxMinX, boxMinY, boxMinZ),
                    new BlockPos(midX + 1, boxMinY, boxMinZ),
                    new BlockPos(boxMinX, midY + 1, boxMinZ),
                    new BlockPos(midX + 1, midY + 1, boxMinZ),
                    new BlockPos(boxMinX, boxMinY, midZ + 1),
                    new BlockPos(midX + 1, boxMinY, midZ + 1),
                    new BlockPos(boxMinX, midY + 1, midZ + 1),
                    new BlockPos(midX + 1, midY + 1, midZ + 1),
            };
            BlockPos[] subMaxs = {
                    new BlockPos(midX, midY, midZ),
                    new BlockPos(boxMaxX, midY, midZ),
                    new BlockPos(midX, boxMaxY, midZ),
                    new BlockPos(boxMaxX, boxMaxY, midZ),
                    new BlockPos(midX, midY, boxMaxZ),
                    new BlockPos(boxMaxX, midY, boxMaxZ),
                    new BlockPos(midX, boxMaxY, boxMaxZ),
                    new BlockPos(boxMaxX, boxMaxY, boxMaxZ),
            };
            for (int i = 0; i < 8; i++) {
                processBox(region, subMins[i], subMaxs[i], baseMinX, baseMinY, baseMinZ,
                        sizeX, sizeY, sizeZ, civil, suspect, runningCivil, forceAllow, context, depth + 1);
            }
            return;
        }

        // 8 corner points
        BlockPos[] corners = corners(boxMinX, boxMinY, boxMinZ, boxMaxX, boxMaxY, boxMaxZ);
        int c = 0;
        for (BlockPos pos : corners) {
            int idx = index(pos.getX(), pos.getY(), pos.getZ(), baseMinX, baseMinY, baseMinZ, sizeX, sizeY, sizeZ);
            if (idx < 0) continue;
            ensureVisited(region, pos, idx, civil, suspect, runningCivil, forceAllow, context);
            if (!forceAllow[0]) {
                c += suspect[idx];
            }
        }
        if (forceAllow[0]) return;

        int priority = priority(c);
        if (priority <= 0) {
            return;
        }

        // Minimum granularity 2³: 8 corner points means 8 blocks, no further subdivision
        if (boxSizeX <= 2 && boxSizeY <= 2 && boxSizeZ <= 2) {
            return;
        }

        // Subdivision: 8 sub-boxes, sorted by priority descending (the 8 sub-boxes at this layer each have c, already in fixed order; here we only subdivide "this box" once, sub-box order uses fixed 0..7; documentation requires "process sub-boxes by priority descending" refers to the 8 siblings at the same layer)
        int midX = (boxMinX + boxMaxX) >> 1;
        int midY = (boxMinY + boxMaxY) >> 1;
        int midZ = (boxMinZ + boxMaxZ) >> 1;
        BlockPos[] subMins = {
                new BlockPos(boxMinX, boxMinY, boxMinZ),
                new BlockPos(midX + 1, boxMinY, boxMinZ),
                new BlockPos(boxMinX, midY + 1, boxMinZ),
                new BlockPos(midX + 1, midY + 1, boxMinZ),
                new BlockPos(boxMinX, boxMinY, midZ + 1),
                new BlockPos(midX + 1, boxMinY, midZ + 1),
                new BlockPos(boxMinX, midY + 1, midZ + 1),
                new BlockPos(midX + 1, midY + 1, midZ + 1),
        };
        BlockPos[] subMaxs = {
                new BlockPos(midX, midY, midZ),
                new BlockPos(boxMaxX, midY, midZ),
                new BlockPos(midX, boxMaxY, midZ),
                new BlockPos(boxMaxX, boxMaxY, midZ),
                new BlockPos(midX, midY, boxMaxZ),
                new BlockPos(boxMaxX, midY, boxMaxZ),
                new BlockPos(midX, boxMaxY, boxMaxZ),
                new BlockPos(boxMaxX, boxMaxY, boxMaxZ),
        };

        int[] priorities = new int[8];
        for (int i = 0; i < 8; i++) {
            int subC = 0;
            for (BlockPos p : corners(subMins[i].getX(), subMins[i].getY(), subMins[i].getZ(),
                    subMaxs[i].getX(), subMaxs[i].getY(), subMaxs[i].getZ())) {
                int ix = index(p.getX(), p.getY(), p.getZ(), baseMinX, baseMinY, baseMinZ, sizeX, sizeY, sizeZ);
                if (ix >= 0) {
                    ensureVisited(region, p, ix, civil, suspect, runningCivil, forceAllow, context);
                    if (!forceAllow[0]) subC += suspect[ix];
                }
            }
            priorities[i] = priority(subC);
        }
        if (forceAllow[0]) return;

        int[] order = argsortDesc(priorities);
        for (int i = 0; i < 8; i++) {
            int j = order[i];
            processBox(region, subMins[j], subMaxs[j], baseMinX, baseMinY, baseMinZ,
                    sizeX, sizeY, sizeZ, civil, suspect, runningCivil, forceAllow, context, depth + 1);
            if (forceAllow[0] || runningCivil[0] >= NORMALIZATION_FACTOR) return;
        }
    }

    private static BlockPos[] corners(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new BlockPos[] {
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, minY, minZ),
                new BlockPos(minX, maxY, minZ),
                new BlockPos(maxX, maxY, minZ),
                new BlockPos(minX, minY, maxZ),
                new BlockPos(maxX, minY, maxZ),
                new BlockPos(minX, maxY, maxZ),
                new BlockPos(maxX, maxY, maxZ),
        };
    }

    /** priority(c): all empty 0, half-half highest, all solid second. */
    private static int priority(int c) {
        if (c == 0) return 0;
        if (c == 8) return 8;
        return c * (8 - c);
    }

    private static int index(int x, int y, int z, int baseMinX, int baseMinY, int baseMinZ,
            int sizeX, int sizeY, int sizeZ) {
        int ix = x - baseMinX, iy = y - baseMinY, iz = z - baseMinZ;
        if (ix < 0 || ix >= sizeX || iy < 0 || iy >= sizeY || iz < 0 || iz >= sizeZ) {
            return -1;
        }
        return ix + sizeX * (iz + sizeZ * iy);
    }

    private void ensureVisited(VoxelRegion region, BlockPos pos, int idx,
            double[] civil, byte[] suspect, double[] runningCivil, boolean[] forceAllow,
            CivilComputeContext context) {
        if (!Double.isNaN(civil[idx])) {
            return;
        }
        BlockState state = region.getBlock(pos);
        // 1) Air: cheapest, directly 0 weight, 0 suspicion, skip getBlockWeight
        if (state.isAir()) {
            suspect[idx] = 0;
            civil[idx] = 0.0;
            return;
        }
        suspect[idx] = 1;
        Block block = state.getBlock();
        // 2) Monster head: skip getBlockWeight, only do forceAllow + headType
        if (BlockCivilization.isMonsterHead(state) && context != null && block instanceof AbstractSkullBlock skull) {
            SkullBlock.SkullType type = skull.getSkullType();
            EntityType<?> et = BlockCivilization.skullTypeToEntityType(type);
            if (et != null) context.addHeadType(et);
            forceAllow[0] = true;
            civil[idx] = 0.0;
            return;
        }
        // 3) Civilization blocks: use getBlockWeight (multi-branch judgment)
        double w = BlockCivilization.getBlockWeight(block);
        civil[idx] = w;
        runningCivil[0] += w;
    }

    /** Returns indices sorted by value in descending order. */
    private static int[] argsortDesc(int[] a) {
        Integer[] idx = new Integer[a.length];
        for (int i = 0; i < a.length; i++) idx[i] = i;
        Arrays.sort(idx, (i, j) -> Integer.compare(a[j], a[i]));
        int[] out = new int[a.length];
        for (int i = 0; i < a.length; i++) out[i] = idx[i];
        return out;
    }
}
