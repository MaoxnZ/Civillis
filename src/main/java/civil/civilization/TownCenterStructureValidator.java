package civil.civilization;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Validates town center physical structure (lectern + emerald block below).
 */
public final class TownCenterStructureValidator {

    private TownCenterStructureValidator() {}

    public static boolean isEmeraldBelow(Level level, BlockPos lecternPos) {
        BlockState lectern = level.getBlockState(lecternPos);
        if (!lectern.is(Blocks.LECTERN)) return false;
        BlockPos below = lecternPos.below();
        return level.getBlockState(below).is(Blocks.EMERALD_BLOCK);
    }

    public static boolean lecternHasWrittenBook(Level level, BlockPos lecternPos) {
        if (!(level.getBlockEntity(lecternPos) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity lectern)) {
            return false;
        }
        return lectern.getBook().is(net.minecraft.world.item.Items.WRITTEN_BOOK);
    }
}
