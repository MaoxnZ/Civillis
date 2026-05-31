package civil.towncenter;

import civil.registry.TownCenterLevelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Town center level queries — delegates to {@link TownCenterLevelRegistry}.
 */
public final class TownCenterLevelTable {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-town-center");
    private static boolean missingWarned;

    private TownCenterLevelTable() {}

    private static final int LEVEL1_HORIZ = 5;
    private static final int LEVEL1_VERT = 1;
    private static final double LEVEL1_RAW = 0.2;

    public static int horizRadius(int level) {
        if (level <= 1) return LEVEL1_HORIZ;
        return TownCenterLevelRegistry.getStepForCurrentLevel(level)
                .map(s -> s.upgrade().horizRadiusVc())
                .orElseGet(() -> fallbackInt("horizRadius", LEVEL1_HORIZ));
    }

    public static int vertRadius(int level) {
        if (level <= 1) return LEVEL1_VERT;
        return TownCenterLevelRegistry.getStepForCurrentLevel(level)
                .map(s -> s.upgrade().vertRadiusVc())
                .orElseGet(() -> fallbackInt("vertRadius", LEVEL1_VERT));
    }

    public static double rawValue(int level) {
        if (level <= 1) return LEVEL1_RAW;
        return TownCenterLevelRegistry.getStepForCurrentLevel(level)
                .map(s -> s.upgrade().baseScoreRaw())
                .orElseGet(() -> fallbackDouble("rawValue", LEVEL1_RAW));
    }

    /** Blocks required to upgrade from {@code level} to {@code level + 1}; 0 if max level. */
    public static int upgradeCost(int level) {
        if (level < 1 || level >= maxLevel()) return 0;
        int target = level + 1;
        return TownCenterLevelRegistry.getStep(target)
                .map(s -> s.upgrade().emeraldBlockCost())
                .orElseGet(() -> fallbackInt("upgradeCost", 0));
    }

    public static int maxLevel() {
        if (TownCenterLevelRegistry.isLoaded()) {
            return TownCenterLevelRegistry.maxLevel();
        }
        warnMissingDatapack();
        return 5;
    }

    private static int fallbackInt(String field, int value) {
        warnMissingDatapack();
        return value;
    }

    private static double fallbackDouble(String field, double value) {
        warnMissingDatapack();
        return value;
    }

    private static void warnMissingDatapack() {
        if (!missingWarned) {
            missingWarned = true;
            LOGGER.warn("[civil-town-center] Town center level datapack not loaded; using conservative defaults");
        }
    }
}
