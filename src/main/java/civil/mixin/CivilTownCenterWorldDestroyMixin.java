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

    /**
     * Forge 1.20.x dual track: Mojmap {@code destroyBlock} for dev/remapped
     * runs, plus SRG {@code m_7740_} for production jars.
     */
    @Inject(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"),
            require = 0,
            cancellable = true)
    private void civil$denyProtectedTownCenterDestroyMoj(
            BlockPos pos, boolean dropBlock, Entity entity, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        civil$denyProtectedTownCenterDestroyCore(pos, cir);
    }

    @Inject(
            method = "m_7740_(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
            at = @At("HEAD"),
            require = 0,
            remap = false,
            cancellable = true)
    private void civil$denyProtectedTownCenterDestroySrg(
            BlockPos pos, boolean dropBlock, Entity entity, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        civil$denyProtectedTownCenterDestroyCore(pos, cir);
    }

    private void civil$denyProtectedTownCenterDestroyCore(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerLevel level)) {
            return;
        }
        String dim = level.dimension().location().toString();
        if (TownCenterActivationHandler.isProtectedBlock(dim, pos)) {
            cir.setReturnValue(false);
        }
    }
}
