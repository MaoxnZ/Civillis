package civil.mixin;

import civil.CivilServices;
import civil.civilization.TownCenterTracker;
import civil.civilization.TownCenterTracker.TownCenterEntry;
import civil.towncenter.TownCenterActivationHandler;
import civil.civilization.structure.TownCenterBreakContext;
import civil.towncenter.TownCenterSpoilService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class CivilTownCenterBlockBreakMixin {

    private static final ThreadLocal<PendingBreak> PENDING = new ThreadLocal<>();

    private record PendingBreak(String dim, TownCenterEntry snapshot, BlockPos lecternPos) {}

    @Shadow @Final protected ServerLevel level;
    @Shadow public ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void civil$townCenterBreakHead(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        PENDING.remove();
        String dim = level.dimension().identifier().toString();
        if (TownCenterActivationHandler.isProtectedBlock(dim, pos)) {
            cir.setReturnValue(false);
            return;
        }

        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) return;

        BlockState state = level.getBlockState(pos);
        TownCenterEntry entry = null;
        BlockPos lecternPos = null;

        if (state.is(Blocks.LECTERN)) {
            entry = tracker.getEntry(dim, pos.getX(), pos.getY(), pos.getZ());
            lecternPos = pos;
        } else if (state.is(Blocks.EMERALD_BLOCK)) {
            lecternPos = pos.above();
            entry = tracker.getEntry(dim, lecternPos.getX(), lecternPos.getY(), lecternPos.getZ());
        }

        if (entry != null && !entry.activated()) {
            PENDING.set(new PendingBreak(dim, entry, lecternPos));
            TownCenterBreakContext.markPlayerBreak();
        }
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void civil$townCenterBreakReturn(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        PendingBreak pending = PENDING.get();
        PENDING.remove();
        TownCenterBreakContext.clearPlayerBreak();
        if (pending == null || !Boolean.TRUE.equals(cir.getReturnValue())) return;

        long gameTime = level.getGameTime();
        TownCenterSpoilService.onTcRemoved(pending.snapshot(), player, gameTime);
        TownCenterActivationHandler.onStructureRemoved(
                level,
                pending.dim(),
                pending.lecternPos().getX(),
                pending.lecternPos().getY(),
                pending.lecternPos().getZ());
    }
}
