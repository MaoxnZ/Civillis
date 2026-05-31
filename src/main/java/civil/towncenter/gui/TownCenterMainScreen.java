package civil.towncenter.gui;

import civil.towncenter.gui.widgets.ActivationToggleWidget;
import civil.towncenter.gui.widgets.NavButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Town center main page: status, activation, navigation to Members/Options. */
public class TownCenterMainScreen extends TownCenterScreenBase {

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            "civil", "textures/gui/town_center.png");

    public TownCenterMainScreen(TownCenterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected Identifier backgroundTexture() {
        return BACKGROUND;
    }

    @Override
    protected TownCenterGuiPage page() {
        return TownCenterGuiPage.MAIN;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;
        menu.setPage(TownCenterGuiPage.MAIN);

        addRenderableWidget(new ActivationToggleWidget(
                menu, x + TownCenterMenuLayout.TOGGLE_X, y + TownCenterMenuLayout.TOGGLE_Y,
                TownCenterMenuLayout.TOGGLE_WIDTH, TownCenterMenuLayout.TOGGLE_HEIGHT));

        addRenderableWidget(new NavButtonWidget(
                this,
                TownCenterGuiPage.MEMBERS,
                x + TownCenterMenuLayout.MEMBERS_BUTTON_X,
                y + TownCenterMenuLayout.MEMBERS_BUTTON_Y,
                TownCenterMenuLayout.NAV_BUTTON_W,
                TownCenterMenuLayout.NAV_BUTTON_H,
                Component.translatable("gui.civil.town_center.manage")));

        addRenderableWidget(new NavButtonWidget(
                this,
                TownCenterGuiPage.OPTIONS,
                x + TownCenterMenuLayout.OPTIONS_BUTTON_X,
                y + TownCenterMenuLayout.OPTIONS_BUTTON_Y,
                TownCenterMenuLayout.NAV_BUTTON_W,
                TownCenterMenuLayout.NAV_BUTTON_H,
                Component.translatable("gui.civil.town_center.upgrade")));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderMainStatus(graphics);
        int y = topPos + TownCenterMenuLayout.SECTION_TITLE_Y;
        drawSectionTitle(graphics, TownCenterMenuLayout.MAIN_X, TownCenterMenuLayout.MAIN_WIDTH,
                Component.translatable("gui.civil.town_center.section.status"), y);
    }
}
