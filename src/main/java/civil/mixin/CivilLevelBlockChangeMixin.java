package civil.mixin;

import civil.CivilServices;
import civil.civilization.BlockScanner;
import civil.civilization.HeadTracker;
import civil.civilization.structure.StructureBlockChangeListener;
import civil.civilization.structure.StructureBlockChangeListeners;
import civil.civilization.scoring.ScalableCivilizationService;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Objects;

@Mixin(Level.class)
public abstract class CivilLevelBlockChangeMixin {

    @Unique
    private static final ThreadLocal<BlockState> civil$oldBlockState = ThreadLocal.withInitial(() -> null);

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD")
    )
    @SuppressWarnings("null")
    private void civil$captureOldState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        BlockPos safePos = Objects.requireNonNull(pos, "pos");
        if ((Object) this instanceof ServerLevel) {
            civil$oldBlockState.set(((Level) (Object) this).getBlockState(safePos));
        }
    }

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

        // Fusion Architecture: only trigger L1 recompute when a civilization block
        // is involved in the transition (old or new). Non-civilization-only changes
        // are ignored to avoid unnecessary 4096-block scans.
        ScalableCivilizationService scalableService = CivilServices.getScalableService();
        BlockState oldStateForCivil = civil$oldBlockState.get();
        boolean oldIsCivil = oldStateForCivil != null && BlockScanner.isTargetBlock(oldStateForCivil);
        boolean newIsCivil = BlockScanner.isTargetBlock(state);
        if (scalableService != null && (oldIsCivil || newIsCivil)) {
            scalableService.onCivilBlockChanged(serverWorld, pos);
        }

        // Track head placement/removal in HeadTracker.
        HeadTracker registry = CivilServices.getHeadTracker();
        if (registry != null && registry.isInitialized()) {
            String dim = serverWorld.dimension().identifier().toString();

            if (isNowHead) {
                AbstractSkullBlock skull = (AbstractSkullBlock) state.getBlock();
                String skullType = skull.getType().toString();
                registry.onHeadAdded(dim, pos.getX(), pos.getY(), pos.getZ(), skullType);
            } else {
                registry.onHeadRemoved(dim, pos.getX(), pos.getY(), pos.getZ());
            }
        }

        // Structure invalidation (uses OLD block state). Delegates to per-structure listeners.
        BlockState oldState = civil$oldBlockState.get();
        civil$oldBlockState.remove();
        if (oldState != null) {
            for (StructureBlockChangeListener listener : StructureBlockChangeListeners.LISTENERS) {
                listener.onBlockRemoved(serverWorld, pos, oldState);
            }
        }
    }
}
