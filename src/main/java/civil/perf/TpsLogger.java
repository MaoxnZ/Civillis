package civil.perf;

import civil.CivilMod;
import civil.config.CivilConfig;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configurable periodic TPS/MSPT logging: every N server ticks outputs one line to latest.log
 * "TPS: 20.0 MSPT: 5.12", for post-processing script tests/plot_performance.py to parse and plot.
 *
 * <p><b>TPS Source Explanation (not false information)</b>: Between start and end of server tick,
 * measures time to get "this tick's actual duration" ms, averages last N ticks to get MSPT,
 * then by standard definition TPS = 1000 / MSPT (number of ticks completed per second).
 */
public final class TpsLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(CivilMod.MOD_ID);

    private static long startNanos = 0L;
    private static final int CAP = 200;
    private static final double[] tickMspt = new double[CAP];
    private static int tickIndex = 0;
    private static int tickCount = 0;

    /** Called at start of each server tick (registered by platform entry point). */
    public static void onStartTick(MinecraftServer server) {
        startNanos = System.nanoTime();
    }

    /** Called at end of each server tick (registered by platform entry point). */
    public static void onEndTick(MinecraftServer server) {
        if (!CivilConfig.isTpsLogEnabled() || !CivilMod.DEBUG) return;
        long elapsedNs = System.nanoTime() - startNanos;
        double mspt = elapsedNs / 1_000_000.0;
        int interval = CivilConfig.getTpsLogIntervalTicks();
        tickMspt[tickIndex % CAP] = mspt;
        tickCount = Math.min(tickCount + 1, CAP);
        tickIndex++;
        if (tickIndex % interval != 0) return;
        int n = Math.min(interval, tickCount);
        if (n == 0) return;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += tickMspt[(tickIndex - 1 - i + CAP) % CAP];
        }
        double avgMspt = sum / n;
        double tps = avgMspt > 0 ? 1000.0 / avgMspt : 20.0;
        tps = Math.min(20.0, Math.max(0.0, tps));
        LOGGER.info(String.format("TPS: %.1f MSPT: %.2f", tps, avgMspt));
    }

    private TpsLogger() {
    }
}
