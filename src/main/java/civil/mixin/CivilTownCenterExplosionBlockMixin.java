package civil.mixin;

import civil.towncenter.TownCenterActivationHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ExplosionDamageCalculator.class, EntityBasedExplosionDamageCalculator.class})
public abstract class CivilTownCenterExplosionBlockMixin {

    /**
     * Forge 1.20.x dual track: Mojmap {@code shouldBlockExplode} for dev/remapped
     * runs, plus SRG {@code m_6714_} for production jars.
     */
    @Inject(
            method = "shouldBlockExplode(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;F)Z",
            at = @At("HEAD"),
            require = 0,
            cancellable = true)
    private void civil$denyProtectedTownCenterExplosionMoj(
            Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power,
            CallbackInfoReturnable<Boolean> cir) {
        civil$denyProtectedTownCenterExplosionCore(level, pos, cir);
    }

    @Inject(
            method = "m_6714_(Lnet/minecraft/world/level/Explosion;Lnet/minecraft/world/level/BlockGetter;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;F)Z",
            at = @At("HEAD"),
            require = 0,
            remap = false,
            cancellable = true)
    private void civil$denyProtectedTownCenterExplosionSrg(
            Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power,
            CallbackInfoReturnable<Boolean> cir) {
        civil$denyProtectedTownCenterExplosionCore(level, pos, cir);
    }

    private static void civil$denyProtectedTownCenterExplosionCore(
            BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        String dim = serverLevel.dimension().location().toString();
        if (TownCenterActivationHandler.isProtectedBlock(dim, pos)) {
            cir.setReturnValue(false);
        }
    }
}
