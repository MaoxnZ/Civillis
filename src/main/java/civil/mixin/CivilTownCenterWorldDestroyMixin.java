package civil.mixin;

import civil.towncenter.TownCenterActivationHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class CivilTownCenterWorldDestroyMixin {

    @Inject(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void civil$denyProtectedTownCenterDestroy(
            BlockPos pos, boolean dropBlock, Entity entity, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerLevel level)) {
            return;
        }
        String dim = level.dimension().identifier().toString();
        if (TownCenterActivationHandler.isProtectedBlock(dim, pos)) {
            cir.setReturnValue(false);
        }
    }
}
