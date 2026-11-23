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

    @Inject(method = "shouldBlockExplode", at = @At("HEAD"), cancellable = true)
    private void civil$denyProtectedTownCenterExplosion(
            Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power,
            CallbackInfoReturnable<Boolean> cir) {
        ServerLevel serverLevel = explosion.level();
        String dim = serverLevel.dimension().identifier().toString();
        if (TownCenterActivationHandler.isProtectedBlock(dim, pos)) {
            cir.setReturnValue(false);
        }
    }
}
