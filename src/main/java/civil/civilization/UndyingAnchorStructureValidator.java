package civil.civilization;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Validates the 3×3 undying anchor structure:
 * - Center: emerald block
 * - Corners: gold blocks at (±1, 0, ±1) from center
 * - Edges: waxed oxidized cut copper stairs with half=BOTTOM, facing toward center
 *
 * <p>Layout (top-down):
 * <pre>
 *       G   S   G
 *       S [E] S
 *       G   S   G
 * </pre>
 * where E=emerald, G=gold, S=stairs.
 */
public final class UndyingAnchorStructureValidator {

    private UndyingAnchorStructureValidator() {}

    /** Required stairs block (waxed oxidized cut copper stairs). */
    public static boolean isRequiredStairs(BlockState state) {
        return state.is(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS);
    }

    /**
     * Validate the undying anchor structure centered at the given emerald block position.
     *
     * @param level      the level
     * @param emeraldPos block position of the emerald (center)
     * @return true if structure is valid
     */
    public static boolean validateStructure(Level level, BlockPos emeraldPos) {
        if (!level.getBlockState(emeraldPos).is(Blocks.EMERALD_BLOCK)) {
            return false;
        }

        // Corners: (±1, 0, ±1)
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                BlockPos corner = emeraldPos.offset(dx, 0, dz);
                if (!level.getBlockState(corner).is(Blocks.GOLD_BLOCK)) {
                    return false;
                }
            }
        }

        // Edges: (0,0,-1) north, (1,0,0) east, (0,0,1) south, (-1,0,0) west
        // North: stairs at (0,0,-1), facing SOUTH (toward emerald)
        if (!validateEdgeStair(level, emeraldPos.offset(0, 0, -1), Direction.SOUTH)) return false;
        // East: stairs at (1,0,0), facing WEST
        if (!validateEdgeStair(level, emeraldPos.offset(1, 0, 0), Direction.WEST)) return false;
        // South: stairs at (0,0,1), facing NORTH
        if (!validateEdgeStair(level, emeraldPos.offset(0, 0, 1), Direction.NORTH)) return false;
        // West: stairs at (-1,0,0), facing EAST
        if (!validateEdgeStair(level, emeraldPos.offset(-1, 0, 0), Direction.EAST)) return false;

        return true;
    }

    private static boolean validateEdgeStair(Level level, BlockPos pos, Direction requiredFacing) {
        BlockState state = level.getBlockState(pos);
        if (!isRequiredStairs(state)) return false;
        if (state.getValue(BlockStateProperties.HALF) != Half.BOTTOM) return false;
        return state.getValue(BlockStateProperties.HORIZONTAL_FACING) == requiredFacing;
    }

    /**
     * Given a removed stair position and its old state, get the emerald position it pointed to.
     * Stairs face toward the emerald, so emerald = pos.relative(facing).
     *
     * @param stairPos  the position where the stair was (before removal)
     * @param oldState  the old block state of the stair (must have HORIZONTAL_FACING)
     * @return the position of the emerald block this stair belonged to, or null if not a valid stair
     */
    public static BlockPos getEmeraldFromStair(BlockPos stairPos, BlockState oldState) {
        if (!isRequiredStairs(oldState)) return null;
        return stairPos.relative(oldState.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    /**
     * Get the 4 diagonal emerald positions from a removed gold block.
     * Gold is at a corner; diagonals are the 4 adjacent corners of the 3×3, but only one is the center.
     * Actually: gold at (dx,0,dz) from emerald means emerald is at gold.offset(-dx,0,-dz).
     * For a gold at a corner, the center is the unique block that is 1 step away in both X and Z.
     * Gold corners: (-1,-1), (1,-1), (-1,1), (1,1) from center. So center = gold + (-dx,-dy,-dz).
     * So we need to check the 4 diagonals from the gold: offset(±1,0,±1). One of them is the center.
     */
    public static BlockPos[] getEmeraldCandidatesFromGold(BlockPos goldPos) {
        return new BlockPos[]{
                goldPos.offset(-1, 0, -1),
                goldPos.offset(1, 0, -1),
                goldPos.offset(-1, 0, 1),
                goldPos.offset(1, 0, 1)
        };
    }
}
