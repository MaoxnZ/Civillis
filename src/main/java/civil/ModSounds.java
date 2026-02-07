package civil;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Civilization detector sound effects played by detection state: default / low / medium / high / monster.
 * Uses custom SoundEvent (civil:detector_*), pointed to by assets/civil/sounds.json to custom OGG
 * (civil:detector_default etc., files placed in assets/civil/sounds/*.ogg).
 * Playback controlled by {@link #PREFER_MOD_DETECTOR_SOUNDS} to prefer vanilla or custom; use {@link #getDetectorPlayback} to get sound + pitch at once.
 * Vanilla uses same sound effect + different pitch to distinguish; custom OGGs already have their own pitch, pitch fixed at 1.0 no secondary scaling.
 */
public final class ModSounds {

    public static final SoundEvent DETECTOR_DEFAULT = register("detector_default");
    public static final SoundEvent DETECTOR_LOW = register("detector_low");
    public static final SoundEvent DETECTOR_MEDIUM = register("detector_medium");
    public static final SoundEvent DETECTOR_HIGH = register("detector_high");
    public static final SoundEvent DETECTOR_MONSTER = register("detector_monster");

    /**
     * Whether to prefer mod custom sound effects (civil:detector_*).
     * true = prefer custom ogg (pitch fixed 1.0), fallback to vanilla if unavailable;
     * false = prefer vanilla sound effects (pitch distinguished by state), use custom if unavailable.
     */
    public static boolean PREFER_MOD_DETECTOR_SOUNDS = false;

    /** Packaged: one-time result needed for playing detector sound effect, contains sound and pitch, compatible with vanilla/custom sources. */
    public record DetectorPlayback(SoundEvent sound, float pitch) {}

    private ModSounds() {
    }

    public static void initialize() {
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[civil] sounds registered: detector_default, detector_low, detector_medium, detector_high, detector_monster");
        }
    }

    private static SoundEvent register(String path) {
        Identifier id = Identifier.of(CivilMod.MOD_ID, path);
        SoundEvent event = SoundEvent.of(id);
        return Registry.register(Registries.SOUND_EVENT, id, event);
    }

    /** Get SoundEvent from vanilla registry by id (client must have loaded), to ensure sound can be heard; returns null on failure. */
    private static SoundEvent getVanillaSound(String namespace, String path) {
        Identifier id = Identifier.of(namespace, path);
        return Registries.SOUND_EVENT.get(id);
    }

    // Vanilla sound effect ids (1.21 dot format)
    private static final String MC = "minecraft";
    private static final String BASS = "block.note_block.bass";
    private static final String CHIME = "block.note_block.chime";
    private static final String WOLOLO = "entity.evoker.prepare_wololo";

    /** Vanilla pitch distinguished by state (low/medium use same bass, distinguished by pitch); custom OGG not scaled, use 1.0. */
    private static float vanillaPitchFor(String displayState) {
        if (displayState == null || "default".equals(displayState)) return 0.65f;
        return switch (displayState) {
            case "low" -> 0.55f;
            case "medium" -> 0.85f;
            case "high", "monster" -> 1.0f;
            default -> 0.65f;
        };
    }

    /**
     * Returns "sound + pitch" packaged result based on display state, for direct use in playSound.
     * Determined by {@link #PREFER_MOD_DETECTOR_SOUNDS} whether to use vanilla or custom; vanilla pitch distinguished by state, custom pitch=1.0 (no secondary scaling).
     */
    public static DetectorPlayback getDetectorPlayback(String displayState) {
        SoundEvent modSound = switch (displayState == null ? "default" : displayState) {
            case "low" -> DETECTOR_LOW;
            case "medium" -> DETECTOR_MEDIUM;
            case "high" -> DETECTOR_HIGH;
            case "monster" -> DETECTOR_MONSTER;
            default -> DETECTOR_DEFAULT;
        };
        SoundEvent vanilla = switch (displayState == null || "default".equals(displayState) ? "default" : displayState) {
            case "low", "medium" -> getVanillaSound(MC, BASS);
            case "high" -> getVanillaSound(MC, CHIME);
            case "monster" -> getVanillaSound(MC, WOLOLO);
            default -> getVanillaSound(MC, BASS);
        };
        if (PREFER_MOD_DETECTOR_SOUNDS) {
            return new DetectorPlayback(modSound, 1.0f);
        }
        SoundEvent sound = vanilla != null ? vanilla : modSound;
        float pitch = (vanilla != null) ? vanillaPitchFor(displayState) : 1.0f;
        return new DetectorPlayback(sound, pitch);
    }
}
