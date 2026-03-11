package civil.mixin;

import civil.respawn.UndyingAnchorCinematicEffect;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies undying anchor cinematic FOV shrink on Fabric (NeoForge uses ComputeFovModifierEvent).
 */
@Mixin(GameRenderer.class)
public abstract class CivilGameRendererFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void civil$modifyFovForCinematic(Camera camera, float tickProgress, boolean changingFov,
            CallbackInfoReturnable<Float> cir) {
        float mult = UndyingAnchorCinematicEffect.getFovMultiplier();
        if (mult > 0 && mult < 1f) {
            cir.setReturnValue(cir.getReturnValue() * mult);
        }
    }
}
