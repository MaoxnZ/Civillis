package civil.civilization.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Listener for structure-related block transitions at a single position. Invoked from
 * {@link civil.mixin.CivilLevelBlockChangeMixin} after a successful {@code Level#setBlock}.
 *
 * <p>Implementations inspect {@code oldState} → {@code newState} (or re-query the level) to
 * invalidate anchors, farm shrines, etc.
 *
 * <p>Register implementations in {@link StructureBlockChangeListeners#LISTENERS}.
 */
public interface StructureBlockChangeListener {

    /**
     * Called when a block at {@code pos} was replaced successfully.
     *
     * @param level    the server level
     * @param pos      the position that changed
     * @param oldState block state before {@code setBlock} (never null)
     * @param newState block state after {@code setBlock} (never null)
     */
    void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState);
}
