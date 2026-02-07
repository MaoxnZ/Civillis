package civil;

import civil.civilization.cache.CivilizationCache;
import civil.civilization.core.CivilizationService;

/**
 * Global service access entry point for Civil module.
 *
 * Currently exposes civilization scoring service and civilization cache (for markChunkDirtyAt etc. when blocks change), more subsystems can be centrally managed here in the future.
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

    /** Civilization cache (may be a debounced wrapper), used for markChunkDirtyAt when blocks change. null if not initialized. */
    public static CivilizationCache getCivilizationCache() {
        return civilizationCache;
    }

    // ========== Backward Compatibility ==========

    /** @deprecated Use {@link #initCivilizationCache(CivilizationCache)} */
    @Deprecated
    public static void initCivilizationMap(CivilizationCache cache) {
        initCivilizationCache(cache);
    }

    /** @deprecated Use {@link #getCivilizationCache()} */
    @Deprecated
    public static CivilizationCache getCivilizationMap() {
        return getCivilizationCache();
    }
}
