package civil.civilization;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Validates farm shrine layout: {@link Blocks#CRYING_OBSIDIAN} directly below a lit
 * {@link Blocks#SOUL_CAMPFIRE} (anchor = campfire block).
 */
public final class FarmShrineStructureValidator {

    private FarmShrineStructureValidator() {
    }

    /** {@code pos} is the soul campfire block. */
    public static boolean validateStructure(Level level, BlockPos pos) {
        BlockState campfire = level.getBlockState(pos);
        if (!campfire.is(Blocks.SOUL_CAMPFIRE)) return false;
        if (!campfire.getValue(CampfireBlock.LIT)) return false;
        BlockPos below = pos.below();
        return level.getBlockState(below).is(Blocks.CRYING_OBSIDIAN);
    }
}
