package civil.component;

import civil.CivilMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/**
 * Custom item Data Components registration.
 * Civilization detector uses {@link #DETECTOR_DISPLAY} to store display state (for item model select),
 * uses {@link #DETECTOR_ANIMATION_END} to store animation end game tick, for restoring default after 2 seconds and controlling particles.
 *
 * <p>Fields are populated by platform-specific registration code:
 * Fabric calls {@link #registerDirect()}, NeoForge uses DeferredRegister and sets fields via setters.
 */
public final class ModComponents {

    /** Display state: default | low | medium | high | monster, for items model definition's select usage. */
    public static DataComponentType<String> DETECTOR_DISPLAY;

    /** Animation end game tick (level.getTime() + 40), for restoring default after 2 seconds and client particle judgment. */
    public static DataComponentType<Long> DETECTOR_ANIMATION_END;

    private ModComponents() {
    }

    /**
     * Direct registration via vanilla Registry API. Called by Fabric entry point
     * where registries are not frozen during mod init.
     */
    public static void registerDirect() {
        DETECTOR_DISPLAY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(CivilMod.MOD_ID, "detector_display"),
                DataComponentType.<String>builder().persistent(Codec.STRING).build());
        DETECTOR_ANIMATION_END = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(CivilMod.MOD_ID, "detector_animation_end"),
                DataComponentType.<Long>builder().persistent(Codec.LONG).build());
        CivilMod.LOGGER.debug("Mod data components registered (direct)");
    }

    /** Builder helpers for platform-specific deferred registration (NeoForge). */
    public static DataComponentType<String> buildDetectorDisplay() {
        return DataComponentType.<String>builder().persistent(Codec.STRING).build();
    }

    public static DataComponentType<Long> buildDetectorAnimationEnd() {
        return DataComponentType.<Long>builder().persistent(Codec.LONG).build();
    }
}
