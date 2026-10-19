package civil.component;

import civil.CivilMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

/**
 * Item data storage via NBT (1.20.x).
 *
 * <p>In 1.21+, this uses Data Components ({@code ComponentType}).
 * In 1.20.x, we use NBT tags on the ItemStack instead.
 *
 * <p>The Civilization Detector uses {@code DETECTOR_DISPLAY} to store display state
 * (for item model selection) and {@code DETECTOR_ANIMATION_END} to store the animation
 * end game tick (for restoring default after 2 seconds and controlling particles).
 */
public final class ModComponents {

    /** NBT key for display state: default | low | medium | high | monster */
    public static final String DETECTOR_DISPLAY = "civil:detector_display";

    /** NBT key for animation end game tick */
    public static final String DETECTOR_ANIMATION_END = "civil:detector_animation_end";

    /** True when the stack is a civil map (empty or filled); used for tint and client blending. */
    public static final String CIVIL_MAP = "civil:civil_map";

    private ModComponents() {
    }

    public static void initialize() {
        CivilMod.LOGGER.debug("Mod NBT component keys registered");
    }

    // --- Convenience accessors ---

    public static String getDisplay(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt == null || !nbt.contains(DETECTOR_DISPLAY)) return "default";
        return nbt.getString(DETECTOR_DISPLAY);
    }

    public static void setDisplay(ItemStack stack, String value) {
        stack.getOrCreateTag().putString(DETECTOR_DISPLAY, value);
    }

    public static long getAnimationEnd(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt == null || !nbt.contains(DETECTOR_ANIMATION_END)) return 0L;
        return nbt.getLong(DETECTOR_ANIMATION_END);
    }

    public static void setAnimationEnd(ItemStack stack, long value) {
        stack.getOrCreateTag().putLong(DETECTOR_ANIMATION_END, value);
    }

    public static boolean hasAnimationEnd(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        return nbt != null && nbt.contains(DETECTOR_ANIMATION_END);
    }

    public static void removeAnimationEnd(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt != null) {
            nbt.remove(DETECTOR_ANIMATION_END);
        }
    }

    public static boolean isCivilMap(ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        return nbt != null && nbt.getBoolean(CIVIL_MAP);
    }

    public static void setCivilMap(ItemStack stack, boolean value) {
        stack.getOrCreateTag().putBoolean(CIVIL_MAP, value);
    }
}
