package civil.registry;

import net.minecraft.world.entity.EntityType;

import java.util.Map;

/**
 * Datapack-driven entity types that trigger civilization presence keepalive sweeps
 * ({@code civil_presence_keepalive}).
 *
 * <p>Each {@link EntityType} maps to a voxel-chunk radius box (same axes as detection/patrol).
 */
public final class PresenceKeepAliveRegistry {

    /** Half-extent in voxel chunks (±N from center VC on each axis). */
    public record RadiusVc(int rx, int rz, int ry) {}

    private static volatile Map<EntityType<?>, RadiusVc> types = Map.of();

    private PresenceKeepAliveRegistry() {
    }

    /**
     * Replace registry contents (typically from {@link PresenceKeepAliveLoader#reload}).
     */
    public static void reload(Map<EntityType<?>, RadiusVc> merged) {
        types = Map.copyOf(merged);
    }

    public static Map<EntityType<?>, RadiusVc> snapshot() {
        return types;
    }

    public static boolean isEmpty() {
        return types.isEmpty();
    }
}
