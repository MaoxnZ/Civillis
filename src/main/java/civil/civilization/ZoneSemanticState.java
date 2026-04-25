package civil.civilization;

/**
 * Player-facing zone semantic state used by HUD.
 */
public enum ZoneSemanticState {
    CIVILIZED(0, "civil.hud.zone_transition.civilized"),
    WILDERNESS(1, "civil.hud.zone_transition.wilderness"),
    CAUTION(2, "civil.hud.zone_transition.caution");

    private final int id;
    private final String translationKey;

    ZoneSemanticState(int id, String translationKey) {
        this.id = id;
        this.translationKey = translationKey;
    }

    public int id() {
        return id;
    }

    /** Lang key for {@link net.minecraft.network.chat.Component#translatable(String)}. */
    public String translationKey() {
        return translationKey;
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
