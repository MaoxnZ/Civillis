package civil;

import civil.civilization.HeadTracker;
import civil.civilization.cache.CivilizationCache;
import civil.civilization.cache.ResultCache;
import civil.civilization.scoring.CivilizationService;
import civil.civilization.scoring.ScalableCivilizationService;

/**
 * Global service access entry point for Civil module.
 *
 * Exposes civilization scoring service, civilization cache (L1 read/write),
 * result cache, and mob head registry (for head attraction system).
 */
public final class CivilServices {

    private static CivilizationService civilizationService;
    private static CivilizationCache civilizationCache;
    private static HeadTracker headTracker;

    private CivilServices() {
    }

    public static void initCivilizationService(CivilizationService service) {
        civilizationService = service;
    }

    public static void initCivilizationCache(CivilizationCache cache) {
        civilizationCache = cache;
    }

    public static void initHeadTracker(HeadTracker tracker) {
        headTracker = tracker;
    }

    public static CivilizationService getCivilizationService() {
        return civilizationService;
    }

    /** Civilization cache (L1 read/write). null if not initialized. */
    public static CivilizationCache getCivilizationCache() {
        return civilizationCache;
    }

    /** Head tracker for head attraction system. null if not initialized. */
    public static HeadTracker getHeadTracker() {
        return headTracker;
    }

    /**
     * Get the result cache from the ScalableCivilizationService.
     * Used for delta propagation and result shard maintenance.
     * Returns null if the service is not a ScalableCivilizationService.
     */
    public static ResultCache getResultCache() {
        if (civilizationService instanceof ScalableCivilizationService scs) {
            return scs.getResultCache();
        }
        return null;
    }

    /**
     * Get the ScalableCivilizationService for delta propagation.
     * Returns null if the service is not a ScalableCivilizationService.
     */
    public static ScalableCivilizationService getScalableService() {
        if (civilizationService instanceof ScalableCivilizationService scs) {
            return scs;
        }
        return null;
    }
}
