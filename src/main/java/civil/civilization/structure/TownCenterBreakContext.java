package civil.civilization.structure;

/** Guards block-change listener when player break mixin owns TC removal. */
public final class TownCenterBreakContext {

    private static final ThreadLocal<Boolean> PLAYER_BREAK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private TownCenterBreakContext() {}

    public static void markPlayerBreak() {
        PLAYER_BREAK.set(Boolean.TRUE);
    }

    public static void clearPlayerBreak() {
        PLAYER_BREAK.remove();
    }

    public static boolean isPlayerBreak() {
        return Boolean.TRUE.equals(PLAYER_BREAK.get());
    }
}
