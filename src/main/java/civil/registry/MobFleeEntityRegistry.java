package civil.registry;

import net.minecraft.world.entity.EntityType;

import java.util.Set;

/**
 * Datapack-driven mob flee entity lists ({@code civil_mob_flee_entities}).
 *
 * <p>{@code blacklist}: entity types that receive civilization flee goals even when not
 * {@link net.minecraft.world.entity.MobCategory#MONSTER}, when the mob is a {@link net.minecraft.world.entity.PathfinderMob}.
 * {@code whitelist}: types that never receive flee goals (bypass).
 *
 * <p>Combination logic lives in {@link civil.mixin.CivilMobGoalMixin} (inline), mirroring
 * {@link civil.mixin.CivilServerLevelSpawnGateMixin} + {@link SpawnGateEntityRegistry}.
 */
public final class MobFleeEntityRegistry {

    private static volatile Set<EntityType<?>> blacklistTypes = Set.of();
    private static volatile Set<EntityType<?>> whitelistTypes = Set.of();

    private MobFleeEntityRegistry() {
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
