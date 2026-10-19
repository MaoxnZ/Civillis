package civil.map;

import civil.CivilMod;

/**
 * Aggregated one-second diagnostics for Civil map bake path on the server.
 * Keeps DEBUG logs informative without per-call flood.
 */
public final class CivilMapPerfTrace {

    private static long serverSec = -1L;
    private static int serverTintCalls = 0;
    private static int serverTintDirtyChunks = 0;
    private static int serverTintTouchedPixels = 0;
    private static int serverTintChangedPixels = 0;
    private static long serverTintUsTotal = 0L;
    private static long serverTintUsMax = 0L;

    private CivilMapPerfTrace() {
    }

    /**
     * @param dirtyChunks reserved (pass 0); kept for log schema compatibility with older builds
     */
    public static synchronized void onServerTintPass(
            int dirtyChunks,
            int touchedPixels,
            int changedPixels,
            long elapsedUs) {
        if (!CivilMod.DEBUG) {
            return;
        }
        rollServerWindowIfNeeded();
        serverTintCalls++;
        serverTintDirtyChunks += dirtyChunks;
        serverTintTouchedPixels += touchedPixels;
        serverTintChangedPixels += changedPixels;
        serverTintUsTotal += elapsedUs;
        serverTintUsMax = Math.max(serverTintUsMax, elapsedUs);
    }

    public static synchronized void flushServerWindow() {
        if (!CivilMod.DEBUG) {
            return;
        }
        rollServerWindowIfNeeded();
    }

    private static void rollServerWindowIfNeeded() {
        long nowSec = System.currentTimeMillis() / 1000L;
        if (serverSec < 0L) {
            serverSec = nowSec;
            return;
        }
        if (nowSec == serverSec) {
            return;
        }
        logServerWindow(serverSec);
        resetServer(nowSec);
    }

    private static void logServerWindow(long sec) {
        if (serverTintCalls == 0) {
            return;
        }
        long tintAvgUs = serverTintCalls == 0 ? 0L : serverTintUsTotal / serverTintCalls;
        CivilMod.LOGGER.info(
                "[civil-map-perf][server][sec={}] tintCalls={} dirtyChunks={} touchedPixels={} changedPixels={} tintAvgUs={} tintMaxUs={}",
                sec,
                serverTintCalls,
                serverTintDirtyChunks,
                serverTintTouchedPixels,
                serverTintChangedPixels,
                tintAvgUs,
                serverTintUsMax);
    }

    private static void resetServer(long nextSec) {
        serverSec = nextSec;
        serverTintCalls = 0;
        serverTintDirtyChunks = 0;
        serverTintTouchedPixels = 0;
        serverTintChangedPixels = 0;
        serverTintUsTotal = 0L;
        serverTintUsMax = 0L;
    }
}
