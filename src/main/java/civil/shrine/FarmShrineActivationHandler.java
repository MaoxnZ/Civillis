package civil.shrine;

import civil.CivilServices;
import civil.civilization.FarmShrineStructureValidator;
import civil.civilization.FarmShrineTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Activates a farm shrine: right-click a <b>lit</b> soul campfire with a bone when crying obsidian is below;
 * extinguishes the campfire and registers the anchor.
 */
public final class FarmShrineActivationHandler {

    private FarmShrineActivationHandler() {
    }

    public static boolean tryActivate(ServerPlayer player, Level level, BlockPos pos, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        BlockState campfireState = level.getBlockState(pos);
        if (!campfireState.is(Blocks.SOUL_CAMPFIRE)) {
            return false;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.BONE)) return false;

        FarmShrineTracker tracker = CivilServices.getFarmShrineTracker();
        if (tracker == null || !tracker.isInitialized()) return false;

        String dim = serverLevel.dimension().identifier().toString();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (!campfireState.getValue(CampfireBlock.LIT)) {
            if (tracker.isShrineAt(dim, x, y, z)) {
                level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5f, 0.8f);
                return true;
            }
            return false;
        }

        if (!FarmShrineStructureValidator.validateStructure(level, pos)) {
            level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5f, 0.8f);
            return true;
        }

        if (tracker.isShrineAt(dim, x, y, z)) {
            level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5f, 0.8f);
            return true;
        }

        BlockState unlit = campfireState.setValue(CampfireBlock.LIT, false);
        // If setBlock fails, do not register — avoids tracker claiming a shrine while the world stayed lit.
        if (!serverLevel.setBlock(pos, unlit, Block.UPDATE_ALL)) {
            level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5f, 0.8f);
            return true;
        }

        tracker.onShrineActivated(dim, x, y, z);
        stack.shrink(1);
        level.playSound(null, pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 0.62f, 0.74f);
        level.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 0.9f, 0.98f);
        level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.92f, 1.04f);
        return true;
    }
}
