package civil.civilization.structure;

import civil.CivilServices;
import civil.civilization.TownCenterTracker;
import civil.towncenter.TownCenterActivationHandler;
import civil.civilization.structure.TownCenterBreakContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Removes deactivated town center entries when lectern or supporting emerald is broken.
 */
public final class TownCenterBlockChangeListener implements StructureBlockChangeListener {

    @Override
    public void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (TownCenterBreakContext.isPlayerBreak()) return;
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) return;

        String dim = level.dimension().identifier().toString();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        if (oldState.is(Blocks.LECTERN) && !newState.is(Blocks.LECTERN)) {
            var entry = tracker.getEntry(dim, x, y, z);
            if (entry != null && !entry.activated()) {
                TownCenterActivationHandler.onStructureRemoved(level, dim, x, y, z);
            }
            return;
        }

        if (oldState.is(Blocks.EMERALD_BLOCK) && !newState.is(Blocks.EMERALD_BLOCK)) {
            BlockPos lecternAbove = pos.above();
            var entry = tracker.getEntry(dim, lecternAbove.getX(), lecternAbove.getY(), lecternAbove.getZ());
            if (entry != null && !entry.activated()) {
                TownCenterActivationHandler.onStructureRemoved(
                        level, dim, lecternAbove.getX(), lecternAbove.getY(), lecternAbove.getZ());
            }
        }
    }
}
