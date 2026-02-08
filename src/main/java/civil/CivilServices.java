package civil;

import civil.civilization.cache.CivilizationCache;
import civil.civilization.core.CivilizationService;

/**
 * Global service access entry point for Civil module.
 *
 * Exposes civilization scoring service and civilization cache
 * (for markChunkDirtyAt etc. when blocks change).
 */
public final class CivilServices {

    private static CivilizationService civilizationService;
    private static CivilizationCache civilizationCache;

    private CivilServices() {
    }

    public static void initCivilizationService(CivilizationService service) {
        civilizationService = service;
    }

    public static void initCivilizationCache(CivilizationCache cache) {
        civilizationCache = cache;
    }

    public static CivilizationService getCivilizationService() {
        return civilizationService;
    }

    /** Civilization cache, used for markChunkDirtyAt when blocks change. null if not initialized. */
    public static CivilizationCache getCivilizationCache() {
        return civilizationCache;
    }
}
