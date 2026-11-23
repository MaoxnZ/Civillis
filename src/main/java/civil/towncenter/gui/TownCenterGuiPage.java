package civil.towncenter.gui;

/** Active town center GUI page (shared client + server menu state). */
public enum TownCenterGuiPage {
    MAIN,
    MEMBERS,
    OPTIONS;

    public static TownCenterGuiPage fromOrdinal(int ord) {
        TownCenterGuiPage[] values = values();
        if (ord < 0 || ord >= values.length) return MAIN;
        return values[ord];
    }
}
