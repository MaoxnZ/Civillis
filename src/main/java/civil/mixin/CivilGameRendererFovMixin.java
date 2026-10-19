package civil.mixin;

import civil.respawn.UndyingAnchorCinematicEffect;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Undying-anchor cinematic FOV shrink (client).
 *
 * <p><b>Forge 双轨（强制）</b>：与 {@link CivilLevelBlockChangeMixin} 一样，除 Mojmap
 * 全描述符外，必须再挂一条 {@code remap = false} 的 SRG 目标，否则在部分 Forge/混淆
 * 运行形态下会出现「开发环境能套上、正式环境套不上」的隐患。
 *
 * <p>同一方法上若两条 RETURN 注入都命中，逻辑会执行两次；用 {@code civil$fovApplyDoneThisCall}
 * 在每次 {@code getFov} 入口（HEAD）清零、在第一次成功应用后置位，第二次 RETURN 直接跳过。
 */
@Mixin(GameRenderer.class)
public abstract class CivilGameRendererFovMixin {

    @Unique
    private boolean civil$fovApplyDoneThisCall;

    @Inject(
            method = "getFov(Lnet/minecraft/client/Camera;FZ)D",
            at = @At("HEAD"),
            require = 0
    )
    private void civil$resetFovApplyFlagMojmap(Camera camera, float tickProgress, boolean changingFov,
            CallbackInfoReturnable<Double> cir) {
        civil$fovApplyDoneThisCall = false;
    }

    @Inject(
            method = "m_109141_(Lnet/minecraft/client/Camera;FZ)D",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void civil$resetFovApplyFlagSrg(Camera camera, float tickProgress, boolean changingFov,
            CallbackInfoReturnable<Double> cir) {
        civil$fovApplyDoneThisCall = false;
    }

    @Inject(
            method = "getFov(Lnet/minecraft/client/Camera;FZ)D",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void civil$modifyFovMojmap(Camera camera, float tickProgress, boolean changingFov,
            CallbackInfoReturnable<Double> cir) {
        civil$applyFovMultiplierOnce(cir);
    }

    @Inject(
            method = "m_109141_(Lnet/minecraft/client/Camera;FZ)D",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void civil$modifyFovSrg(Camera camera, float tickProgress, boolean changingFov,
            CallbackInfoReturnable<Double> cir) {
        civil$applyFovMultiplierOnce(cir);
    }

    @Unique
    private void civil$applyFovMultiplierOnce(CallbackInfoReturnable<Double> cir) {
        if (civil$fovApplyDoneThisCall) {
            return;
        }
        float mult = UndyingAnchorCinematicEffect.getFovMultiplier();
        if (mult > 0 && mult < 1f) {
            civil$fovApplyDoneThisCall = true;
            cir.setReturnValue(cir.getReturnValue() * (double) mult);
        }
    }
}
