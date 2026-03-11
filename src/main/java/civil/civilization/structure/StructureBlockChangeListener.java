package civil.civilization.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Listener for structure-related block removals. Called from {@code CivilLevelBlockChangeMixin}
 * when a block is removed; implementations check if the removed block affects their structure
 * and update internal state (e.g. anchor tracker) accordingly.
 *
 * <p>Add new structure types by implementing this interface and registering in
 * {@link StructureBlockChangeListeners#LISTENERS}.
 */
public interface StructureBlockChangeListener {

    /**
     * Called when a block is removed. {@code oldState} is the block state before removal.
     *
     * @param level    the server level
     * @param pos      the position where the block was removed
     * @param oldState the block state before removal (never null when this is called)
     */
    void onBlockRemoved(ServerLevel level, BlockPos pos, BlockState oldState);
}
