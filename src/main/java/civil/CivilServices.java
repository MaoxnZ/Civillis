package civil;

import civil.civilization.MobHeadRegistry;
import civil.civilization.cache.CivilizationCache;
import civil.civilization.core.CivilizationService;

/**
 * Global service access entry point for Civil module.
 *
 * Exposes civilization scoring service, civilization cache
 * (for markChunkDirtyAt etc. when blocks change), and mob head registry
 * (for head attraction system).
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

    /** Civilization cache, used for markChunkDirtyAt when blocks change. null if not initialized. */
    public static CivilizationCache getCivilizationCache() {
        return civilizationCache;
    }

    /** Mob head registry for head attraction system. null if not initialized. */
    public static MobHeadRegistry getMobHeadRegistry() {
        return mobHeadRegistry;
    }
}
