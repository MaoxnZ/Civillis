package civil.towncenter.gui;

/** Town center screens that share one {@link TownCenterMenu}. */
public interface TownCenterGuiHost {

    TownCenterMenu getMenu();

    void openPage(TownCenterGuiPage page);

    boolean canConfirmUpgrade();
}
