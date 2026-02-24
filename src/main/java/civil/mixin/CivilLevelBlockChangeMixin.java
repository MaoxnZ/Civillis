package civil.mixin;

import civil.CivilServices;
import civil.civilization.BlockScanner;
import civil.civilization.HeadTracker;
import civil.civilization.scoring.ScalableCivilizationService;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class CivilLevelBlockChangeMixin {

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN")
    )
    private void civil$onBlockSet(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        if (!((Object) this instanceof ServerLevel serverWorld)) {
            return;
        }

        // ===== Fusion Architecture: Special block filtering + Delta propagation =====
        // Only trigger L1 recompute + delta propagation when a "civilization block" is
        // placed or removed. Non-civilization blocks (dirt, stone, redstone, etc.) are
        // ignored — they don't affect civilization score.
        //
        // Note: We need the OLD block state to compare weights, but the mixin fires
        // after the block change. We use a simplified approach: if the NEW block has
        // non-zero weight OR the position changed (which we can't easily tell),
        // we trigger the update. The ScalableCivilizationService.onCivilBlockChanged()
        // handles the delta calculation by comparing cached vs recomputed L1 scores.

        boolean isNowHead = BlockScanner.isSkullBlock(state);

        // Fusion Architecture: immediate L1 recompute + delta propagation.
        // onCivilBlockChanged compares old vs new L1 scores; if delta is non-zero,
        // it propagates to all affected ResultEntries. For non-special→non-special
        // block changes, the delta will be 0 and propagation is skipped (cheap).
        ScalableCivilizationService scalableService = CivilServices.getScalableService();
        if (scalableService != null) {
            scalableService.onCivilBlockChanged(serverWorld, pos);
        }

        // Track head placement/removal in HeadTracker.
        HeadTracker registry = CivilServices.getHeadTracker();
        if (registry != null && registry.isInitialized()) {
            String dim = serverWorld.dimension().toString();

            if (isNowHead) {
                AbstractSkullBlock skull = (AbstractSkullBlock) state.getBlock();
                String skullType = skull.getType().toString();
                registry.onHeadAdded(dim, pos.getX(), pos.getY(), pos.getZ(), skullType);
            } else {
                registry.onHeadRemoved(dim, pos.getX(), pos.getY(), pos.getZ());
            }
        }
    }
}
