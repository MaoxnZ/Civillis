package civil.mixin;

import civil.respawn.UndyingAnchorCinematicEffect;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Applies undying anchor cinematic FOV shrink on the same scalar the player FOV pipeline is built from.
 *
 * <p>MC 26.x still computes {@link Camera#getFov()} from {@code calculateFov} during {@link Camera#update},
 * then derives {@code hudFov} from {@code calculateHudFov}. Patching only {@code CameraRenderState#hudFov} at
 * extract time can disagree with that dependency chain (and with what 1.21.x did by scaling
 * {@link net.minecraft.client.renderer.GameRenderer#getFov}).
 *
 * <p>Redirect the single {@code calculateFov} call inside {@code update} so {@code fov} and everything that
 * derives from it (including {@code hudFov}) stays internally consistent — same {@code mult} curve as before.
 */
@Mixin(Camera.class)
public abstract class CivilGameRendererFovMixin {

    @Invoker("calculateFov")
    abstract float civil$invokeCalculateFov(float partialTick);

    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateFov(F)F"))
    private float civil$scaleCinematicWorldFovInUpdate(Camera camera, float partialTick) {
        float base = ((CivilGameRendererFovMixin) (Object) camera).civil$invokeCalculateFov(partialTick);
        float mult = UndyingAnchorCinematicEffect.getFovMultiplier();
        if (mult > 0f && mult < 1f) {
            return base * mult;
        }
        return base;
    }
}
