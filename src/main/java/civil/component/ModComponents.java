package civil.component;

import civil.CivilMod;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Custom item Data Components registration.
 * Civilization detector uses {@link #DETECTOR_DISPLAY} to store display state (for item model select),
 * uses {@link #DETECTOR_ANIMATION_END} to store animation end game tick, for restoring default after 2 seconds and controlling particles.
 */
public final class ModComponents {

    /** Display state: default | low | medium | high | monster, for items model definition's select usage. */
    public static final ComponentType<String> DETECTOR_DISPLAY = registerString("detector_display", Codec.STRING);

    /** Animation end game tick (level.getTime() + 40), for restoring default after 2 seconds and client particle judgment. */
    public static final ComponentType<Long> DETECTOR_ANIMATION_END = registerLong("detector_animation_end", Codec.LONG);

    private ModComponents() {
    }

    public static void initialize() {
        CivilMod.LOGGER.debug("Mod data components registered");
    }

    private static ComponentType<String> registerString(String path, Codec<String> codec) {
        ComponentType<String> type = ComponentType.<String>builder().codec(codec).build();
        return register(path, type);
    }

    private static ComponentType<Long> registerLong(String path, Codec<Long> codec) {
        ComponentType<Long> type = ComponentType.<Long>builder().codec(codec).build();
        return register(path, type);
    }

    private static <T> ComponentType<T> register(String path, ComponentType<T> type) {
        Identifier id = Identifier.of(CivilMod.MOD_ID, path);
        return Registry.register(Registries.DATA_COMPONENT_TYPE, id, type);
    }
}
