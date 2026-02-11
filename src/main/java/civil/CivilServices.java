package civil;

import civil.civilization.MobHeadRegistry;
import civil.civilization.cache.CivilizationCache;
import civil.civilization.cache.ResultCache;
import civil.civilization.core.CivilizationService;
import civil.civilization.core.ScalableCivilizationService;

/**
 * Global service access entry point for Civil module.
 *
 * Exposes civilization scoring service, civilization cache (L1 read/write),
 * result cache, and mob head registry (for head attraction system).
 */
public final class CivilServices {

    private static CivilizationService civilizationService;
    private static CivilizationCache civilizationCache;
    private static MobHeadRegistry mobHeadRegistry;

    private CivilServices() {
    }

    public static void initCivilizationService(CivilizationService service) {
        civilizationService = service;
    }

    public static void initCivilizationCache(CivilizationCache cache) {
        civilizationCache = cache;
    }

    public static void initMobHeadRegistry(MobHeadRegistry registry) {
        mobHeadRegistry = registry;
    }

    public static CivilizationService getCivilizationService() {
        return civilizationService;
    }

    /** Civilization cache (L1 read/write). null if not initialized. */
    public static CivilizationCache getCivilizationCache() {
        return civilizationCache;
    }

    /** Mob head registry for head attraction system. null if not initialized. */
    public static MobHeadRegistry getMobHeadRegistry() {
        return mobHeadRegistry;
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
