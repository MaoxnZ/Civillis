package civil.civilization;

/**
 * Player-facing zone semantic state used by HUD.
 */
public enum ZoneSemanticState {
    CIVILIZED(0, "Civilized"),
    WILDERNESS(1, "Wilderness"),
    CAUTION(2, "Caution");

    private final int id;
    private final String displayText;

    ZoneSemanticState(int id, String displayText) {
        this.id = id;
        this.displayText = displayText;
    }

    public int id() {
        return id;
    }

    public String displayText() {
        return displayText;
    }

    public static ZoneSemanticState fromId(int id) {
        return switch (id) {
            case 0 -> CIVILIZED;
            case 1 -> WILDERNESS;
            case 2 -> CAUTION;
            default -> WILDERNESS;
        };
    }
}
