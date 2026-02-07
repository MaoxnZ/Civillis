package civil.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Civil module configuration: reads from civil.properties in Fabric's config directory.
 * Actual path determined by {@link net.fabricmc.loader.api.FabricLoader#getConfigDir()}:
 * During development usually run/config/civil.properties, in production game .minecraft/config/civil.properties.
 * If file does not exist, load() will create default file in that directory.
 */
public final class CivilConfig {

    private static final String FILE_NAME = "civil.properties";

    /** Whether to enable periodic TPS output to log (for post-processing plotting). */
    private static boolean tpsLogEnabled = true;
    /** How many server ticks between each TPS/MSPT output line (20 ≈ 1 second). */
    private static int tpsLogIntervalTicks = 20;

    private CivilConfig() {
    }

    public static boolean isTpsLogEnabled() {
        return tpsLogEnabled;
    }

    public static int getTpsLogIntervalTicks() {
        return tpsLogIntervalTicks;
    }

    /** Called during mod initialization, loads from config/civil.properties. */
    public static void load() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        Path file = dir.resolve(FILE_NAME);
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (var is = Files.newInputStream(file)) {
                p.load(is);
            } catch (IOException e) {
                // Ignore, use default values
            }
        }
        tpsLogEnabled = parseBoolean(p.getProperty("tpsLog.enabled"), true);
        tpsLogIntervalTicks = parseInt(p.getProperty("tpsLog.intervalTicks"), 20);
        tpsLogIntervalTicks = Math.max(1, Math.min(1000, tpsLogIntervalTicks));

        if (!Files.isRegularFile(file)) {
            try {
                Files.createDirectories(dir);
                String defaultContent = "# Civil mod config\n"
                    + "# TPS periodic output to latest.log, for tests/plot_performance.py to plot timeline\n"
                    + "tpsLog.enabled=true\n"
                    + "tpsLog.intervalTicks=20\n";
                Files.writeString(file, defaultContent);
            } catch (IOException ignored) {
            }
        }
    }

    private static boolean parseBoolean(String v, boolean def) {
        if (v == null) return def;
        return "true".equalsIgnoreCase(v.trim());
    }

    private static int parseInt(String v, int def) {
        if (v == null) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
