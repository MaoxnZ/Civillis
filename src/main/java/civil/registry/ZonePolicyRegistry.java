package civil.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Set;

/**
 * Data-driven zone policy registry.
 *
 * <p>Loaded from datapack JSON files by {@link ZonePolicyLoader}.
 */
public final class ZonePolicyRegistry {

    public record Rules(boolean allowHostileSpawn, Set<EntityType<?>> allowMobs) {
        public boolean allows(EntityType<?> entityType) {
            if (allowHostileSpawn) return true;
            return entityType != null && allowMobs.contains(entityType);
        }
    }

    public record ZonePolicyRule(String id, Set<Identifier> structures, Rules rules) {}

    private static volatile List<ZonePolicyRule> rules = List.of();

    private ZonePolicyRegistry() {}

    public static List<ZonePolicyRule> rules() {
        return rules;
    }

    public static void reload(List<ZonePolicyRule> newRules) {
        rules = List.copyOf(newRules);
    }
}
