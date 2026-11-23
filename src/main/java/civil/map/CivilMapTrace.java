package civil.map;

import civil.CivilMod;

/**
 * Civil map pipeline tracing (tint sync, client cache). Gated by {@link CivilMod#DEBUG} like other mod diagnostics;
 * grep {@code [civil-map-trace]} in {@code latest.log} when {@code CivilMod.DEBUG} is {@code true}.
 */
public final class CivilMapTrace {

    public static final boolean ON = CivilMod.DEBUG;

    private CivilMapTrace() {
    }

    public static void log(String message, Object... args) {
        if (!ON) {
            return;
        }
        CivilMod.LOGGER.info("[civil-map-trace] " + message, args);
    }
}
