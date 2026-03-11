package civil.respawn;

import civil.CivilServices;
import civil.civilization.UndyingAnchorStructureValidator;
import civil.civilization.UndyingAnchorTracker;
import civil.civilization.scoring.CivilizationService;
import civil.config.CivilConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Handles undying anchor activation: right-click emerald with totem when structure is valid.
 * If already activated, does not consume totem.
 */
public final class UndyingAnchorActivationHandler {

    private UndyingAnchorActivationHandler() {}

    /**
     * Try to activate an undying anchor at the given block position.
     *
     * @param player the player (must hold totem in the given hand)
     * @param level  the level (must be ServerLevel)
     * @param pos    the block position (must be emerald block)
     * @param hand   the hand holding the totem
     * @return true if we handled the interaction (consumed totem or already activated)
     */
    public static boolean tryActivate(ServerPlayer player, Level level, BlockPos pos, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        if (!level.getBlockState(pos).is(Blocks.EMERALD_BLOCK)) return false;

        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(Items.TOTEM_OF_UNDYING)) return false;

        if (!CivilConfig.undyingAnchorEnabled) return false;

        UndyingAnchorTracker tracker = CivilServices.getUndyingAnchorTracker();
        if (tracker == null || !tracker.isInitialized()) return false;

        if (!UndyingAnchorStructureValidator.validateStructure(level, pos)) {
            level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5f, 0.8f);
            return true;  // handled (no totem consume)
        }

        String dim = serverLevel.dimension().identifier().toString();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        var entry = tracker.getAnchorAt(dim, x, y, z);
        if (entry != null && entry.activated()) {
            level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5f, 0.8f);
            return true;  // already activated, no totem consume
        }

        // Require civ >= greenLine + 80% toward 1.0 to consume totem; avoid wasting totem in low-civ areas
        CivilizationService civService = CivilServices.getCivilizationService();
        if (civService != null) {
            double required = CivilConfig.getUndyingAnchorCivRequired();
            double score = civService.getCScoreAt(serverLevel, pos).score();
            if (score + UndyingAnchorTracker.CIV_EPSILON < required) {
                level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5f, 0.8f);
                return true; // handled (no totem consume), civ too low
            }
        }

        if (entry != null && !entry.activated()) {
            tracker.reactivateAnchor(dim, x, y, z);
        } else {
            tracker.onAnchorActivated(dim, x, y, z);
        }

        stack.shrink(1);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_AMBIENT, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.6f, 1.0f);
        return true;
    }
}
