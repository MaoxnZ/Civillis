package civil.registry;

import net.minecraft.world.entity.EntityType;

import java.util.Set;

/**
 * Datapack-driven spawn gate entity lists ({@code civil_spawn_gate_entities}).
 *
 * <p>{@code blacklist}: extra entity types that enter the natural-spawn gate mixin alongside
 * {@link net.minecraft.world.entity.MobCategory#MONSTER} (not “deny spawn”).
 * {@code whitelist}: types that receive an allow pass inside {@link civil.spawn.SpawnPolicy}
 * before zone policy.
 */
public final class SpawnGateEntityRegistry {

    private static volatile Set<EntityType<?>> blacklistTypes = Set.of();
    private static volatile Set<EntityType<?>> whitelistTypes = Set.of();

    private SpawnGateEntityRegistry() {
    }

    public static boolean isBlacklist(EntityType<?> type) {
        return type != null && blacklistTypes.contains(type);
    }

    public static boolean isWhitelist(EntityType<?> type) {
        return type != null && whitelistTypes.contains(type);
    }

    public static void reload(Set<EntityType<?>> blacklist, Set<EntityType<?>> whitelist) {
        blacklistTypes = Set.copyOf(blacklist);
        whitelistTypes = Set.copyOf(whitelist);
    }
}
