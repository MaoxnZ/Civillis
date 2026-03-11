package civil.civilization.structure;

import civil.CivilServices;
import civil.civilization.UndyingAnchorStructureValidator;
import civil.civilization.UndyingAnchorTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Structure block-change listener for undying anchors. When emerald, gold, or stairs
 * are removed from a 3×3 anchor structure, removes the anchor from the tracker.
 */
public final class UndyingAnchorBlockChangeListener implements StructureBlockChangeListener {

    @Override
    public void onBlockRemoved(ServerLevel level, BlockPos pos, BlockState oldState) {
        UndyingAnchorTracker tracker = CivilServices.getUndyingAnchorTracker();
        if (tracker == null || !tracker.isInitialized()) return;

        String dim = level.dimension().identifier().toString();

        if (oldState.is(Blocks.EMERALD_BLOCK)) {
            if (tracker.isAnchorAt(dim, pos.getX(), pos.getY(), pos.getZ())) {
                var entry = tracker.getAnchorAt(dim, pos.getX(), pos.getY(), pos.getZ());
                if (entry != null && entry.activated()) {
                    level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
                }
                tracker.onAnchorRemoved(dim, pos.getX(), pos.getY(), pos.getZ());
            }
            return;
        }

        if (oldState.is(Blocks.GOLD_BLOCK)) {
            for (BlockPos emerald : UndyingAnchorStructureValidator.getEmeraldCandidatesFromGold(pos)) {
                if (level.getBlockState(emerald).is(Blocks.EMERALD_BLOCK)
                        && tracker.isAnchorAt(dim, emerald.getX(), emerald.getY(), emerald.getZ())) {
                    var entry = tracker.getAnchorAt(dim, emerald.getX(), emerald.getY(), emerald.getZ());
                    if (entry != null && entry.activated()) {
                        level.playSound(null, emerald, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    }
                    tracker.onAnchorRemoved(dim, emerald.getX(), emerald.getY(), emerald.getZ());
                    return;
                }
            }
            return;
        }

        if (UndyingAnchorStructureValidator.isRequiredStairs(oldState)) {
            BlockPos emerald = UndyingAnchorStructureValidator.getEmeraldFromStair(pos, oldState);
            if (emerald != null && level.getBlockState(emerald).is(Blocks.EMERALD_BLOCK)
                    && tracker.isAnchorAt(dim, emerald.getX(), emerald.getY(), emerald.getZ())) {
                var entry = tracker.getAnchorAt(dim, emerald.getX(), emerald.getY(), emerald.getZ());
                if (entry != null && entry.activated()) {
                    level.playSound(null, emerald, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
                }
                tracker.onAnchorRemoved(dim, emerald.getX(), emerald.getY(), emerald.getZ());
            }
        }
    }
}
