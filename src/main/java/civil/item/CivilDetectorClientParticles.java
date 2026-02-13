package civil.item;

import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

/**
 * Legacy detector client particles — now a no-op.
 *
 * <p>The old ENCHANT / FLAME particles around the player during detector animation
 * have been replaced by the sonar shockwave effect ({@link civil.aura.SonarShockwaveEffect}),
 * which provides much clearer zone-aware visual feedback.
 *
 * <p>Kept as a stub to avoid breaking the registration call in {@code CivilModClient}.
 */
@Environment(EnvType.CLIENT)
public final class CivilDetectorClientParticles {

    private CivilDetectorClientParticles() {
    }

    /** No-op — sonar shockwave replaces the old particle effects. */
    public static void register() {
        // Intentionally empty. See SonarShockwaveEffect for the new particle animation.
    }
}
