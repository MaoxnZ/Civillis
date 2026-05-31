package civil.registry;

import net.minecraft.world.effect.MobEffect;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Town center level steps and zone buff offers loaded from datapack by {@link TownCenterLevelLoader}.
 */
public final class TownCenterLevelRegistry {

    public enum PaymentTier {
        NONE,
        IRON,
        GOLD,
        NETHERITE;

        public static PaymentTier fromString(String raw) {
            if (raw == null || raw.isBlank()) return NONE;
            return PaymentTier.valueOf(raw.toUpperCase());
        }
    }

    public record UpgradeStep(int emeraldBlockCost, int horizRadiusVc, int vertRadiusVc, double baseScoreRaw) {}

    public record ZoneBuffOffer(
            String id,
            MobEffect effect,
            int increment,
            PaymentTier paymentTier,
            String translationKey,
            boolean ambient,
            boolean showParticles,
            boolean showIcon) {}

    public record LevelStep(int targetLevel, UpgradeStep upgrade, List<ZoneBuffOffer> zoneBuffOffers) {}

    private static Map<Integer, LevelStep> steps = Map.of();
    private static Map<String, ZoneBuffOffer> offersById = Map.of();
    private static int maxLevel = 5;

    private TownCenterLevelRegistry() {}

    public static void reload(Map<Integer, LevelStep> newSteps, Map<String, ZoneBuffOffer> newOffers, int newMaxLevel) {
        steps = Collections.unmodifiableMap(Map.copyOf(newSteps));
        offersById = Collections.unmodifiableMap(Map.copyOf(newOffers));
        maxLevel = Math.max(1, newMaxLevel);
    }

    public static Optional<LevelStep> getStep(int targetLevel) {
        return Optional.ofNullable(steps.get(targetLevel));
    }

    public static Optional<ZoneBuffOffer> getOffer(String id) {
        return Optional.ofNullable(offersById.get(id));
    }

    public static int maxLevel() {
        return maxLevel;
    }

    public static boolean isLoaded() {
        return !steps.isEmpty();
    }

    /** Stats for the town at {@code level}; level 1 uses {@link TownCenterLevelTable} defaults. */
    public static Optional<LevelStep> getStepForCurrentLevel(int level) {
        if (level <= 1) return Optional.empty();
        return getStep(Math.min(level, maxLevel()));
    }
}
