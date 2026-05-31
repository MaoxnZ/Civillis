package civil.towncenter.gui;

import civil.towncenter.TownCenterLevelTable;
import civil.towncenter.gui.widgets.ConfirmUpgradeButtonWidget;
import civil.towncenter.gui.widgets.NavButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Town center options page: donation, ingot, buff preview, confirm upgrade. */
public class TownCenterOptionsScreen extends TownCenterScreenBase {

    private static final int RGB_COST_SQUARE = 0x34D399;
    private static final int RGB_COST_SQUARE_BORDER = 0x1A7A52;
    private static final String ARROW = "→";
    private static final String UNKNOWN = "?";
    private static final String BUFF_PLUS = "+";

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            "civil", "textures/gui/town_center_options_panel.png");

    private ConfirmUpgradeButtonWidget confirmWidget;

    public TownCenterOptionsScreen(TownCenterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected Identifier backgroundTexture() {
        return BACKGROUND;
    }

    @Override
    protected TownCenterGuiPage page() {
        return TownCenterGuiPage.OPTIONS;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;
        menu.setPage(TownCenterGuiPage.OPTIONS);

        addRenderableWidget(new NavButtonWidget(
                this,
                TownCenterGuiPage.MAIN,
                x + TownCenterMenuLayout.BACK_BUTTON_X,
                y + TownCenterMenuLayout.BACK_BUTTON_Y,
                TownCenterMenuLayout.BACK_BUTTON_W,
                TownCenterMenuLayout.BACK_BUTTON_H,
                Component.translatable("gui.civil.town_center.back")));

        confirmWidget = addRenderableWidget(new ConfirmUpgradeButtonWidget(
                this,
                x + TownCenterMenuLayout.OPTIONS_CONFIRM_X,
                y + TownCenterMenuLayout.OPTIONS_CONFIRM_Y,
                TownCenterMenuLayout.NAV_BUTTON_W,
                TownCenterMenuLayout.PANEL_CHIP_H));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderOptionsContent(graphics);
    }

    private void renderOptionsContent(GuiGraphics graphics) {
        drawSubpageTitle(graphics, Component.translatable("gui.civil.town_center.options.title"));
        renderUpgradeHint(graphics);
        renderZonePreview(graphics);
    }

    private void renderZonePreview(GuiGraphics graphics) {
        int x = leftPos + TownCenterMenuLayout.OPTIONS_TEXT_X;
        int y = topPos + TownCenterMenuLayout.OPTIONS_PREVIEW_TEXT_Y;
        int step = TownCenterMenuLayout.LINE_HEIGHT;
        int muted = TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_MUTED);
        int textRgb = TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_TEXT);
        int goldRgb = TownCenterGuiPalette.argb(RGB_MAX);

        int level = menu.getLevel();
        int target = TownCenterClientState.pendingTargetLevel();
        boolean pendingUpgrade = target == level + 1 && target > 0;
        int cost = menu.getUpgradeCost();
        boolean donationReady = pendingUpgrade
                && cost > 0
                && menu.getDonationStack().is(net.minecraft.world.item.Items.EMERALD_BLOCK)
                && menu.getDonationStack().getCount() >= cost;

        String reachNow = TownCenterScreenBase.formatReach(menu.getHorizRadiusVc(), menu.getVertRadiusVc());
        String boostNow = formatBoost(menu.getBaseScorePermille() / 1000.0);
        String reachNext = donationReady
                ? TownCenterScreenBase.formatReach(TownCenterLevelTable.horizRadius(target), TownCenterLevelTable.vertRadius(target))
                : UNKNOWN;
        String boostNext = donationReady
                ? formatBoost(TownCenterLevelTable.rawValue(target))
                : UNKNOWN;

        if (pendingUpgrade) {
            drawArrowPair(graphics, x, y,
                    Component.translatable("gui.civil.town_center.options.preview.range", reachNow),
                    reachNext, muted, donationReady ? textRgb : muted);
            drawArrowPair(graphics, x, y + step,
                    Component.translatable("gui.civil.town_center.options.preview.boost", boostNow),
                    boostNext, muted, donationReady ? textRgb : muted);
        } else {
            graphics.drawString(font,
                    Component.translatable("gui.civil.town_center.options.preview.range", reachNow),
                    x, y, textRgb, false);
            graphics.drawString(font,
                    Component.translatable("gui.civil.town_center.options.preview.boost", boostNow),
                    x, y + step, textRgb, false);
        }

        drawBuffLine(graphics, x, y + step * 2, muted, goldRgb, pendingUpgrade);
    }

    private void drawArrowPair(
            GuiGraphics graphics,
            int x,
            int y,
            Component left,
            String right,
            int arrowRgb,
            int rightRgb) {
        graphics.drawString(font, left, x, y, TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_TEXT), false);
        int arrowX = x + font.width(left) + 4;
        graphics.drawString(font, ARROW, arrowX, y, arrowRgb, false);
        int rightX = arrowX + font.width(ARROW) + 4;
        graphics.drawString(font, right, rightX, y, rightRgb, false);
    }

    private void drawBuffLine(
            GuiGraphics graphics,
            int x,
            int y,
            int mutedRgb,
            int goldRgb,
            boolean pendingUpgrade) {
        if (!pendingUpgrade) {
            return;
        }
        graphics.drawString(font, BUFF_PLUS, x, y, mutedRgb, false);
        int tailX = x + font.width(BUFF_PLUS) + 2;
        int maxW = TownCenterMenuLayout.OPTIONS_TEXT_W - (tailX - x);

        Component buff = TownCenterClientState.zoneBuffForIngot(menu.getIngotStack());
        if (buff != null) {
            String line = ellipsis(font, buff.getString(), maxW);
            graphics.drawString(font, line, tailX, y, goldRgb, false);
        } else {
            graphics.drawString(font, UNKNOWN, tailX, y, mutedRgb, false);
        }
    }

    private void renderUpgradeHint(GuiGraphics graphics) {
        if (!menu.isGameplayActive()) {
            return;
        }
        int textY = topPos + TownCenterMenuLayout.OPTIONS_SLOT_ROW_TEXT_Y;
        int donLeft = leftPos + TownCenterMenuLayout.OPTIONS_DONATION_SLOT_X;
        int donRight = leftPos + TownCenterMenuLayout.optionsDonationSlotRightX();
        int ingLeft = leftPos + TownCenterMenuLayout.optionsIngotSlotLeftX();
        int cost = menu.getUpgradeCost();

        if (cost <= 0) {
            Component max = Component.translatable("gui.civil.town_center.max_level");
            int midX = donRight + (ingLeft - donRight - font.width(max)) / 2;
            graphics.drawString(font, max, midX, textY,
                    TownCenterGuiPalette.argb(RGB_MAX), false);
            return;
        }

        String countText = String.valueOf(cost);
        int textW = font.width(countText);
        int sq = TownCenterMenuLayout.OPTIONS_COST_HINT_SQUARE;
        int gap = TownCenterMenuLayout.OPTIONS_COST_HINT_GAP;
        int sqLeft = donLeft - gap - sq - TownCenterMenuLayout.OPTIONS_COST_HINT_LEFT_OFFSET;
        int textX = sqLeft - gap - textW;

        graphics.drawString(font, countText, textX, textY,
                TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_TEXT), false);
        int sqTop = textY + (8 - sq) / 2;
        graphics.fill(sqLeft, sqTop, sqLeft + sq, sqTop + sq, TownCenterGuiPalette.argb(RGB_COST_SQUARE_BORDER));
        graphics.fill(sqLeft + 1, sqTop + 1, sqLeft + sq - 1, sqTop + sq - 1,
                TownCenterGuiPalette.argb(RGB_COST_SQUARE));

        int level = menu.getLevel();
        Component levelLine = Component.translatable(
                "gui.civil.town_center.options.donation.level",
                level,
                level + 1);
        int midX = donRight + (ingLeft - donRight - font.width(levelLine)) / 2;
        graphics.drawString(font, levelLine, midX, textY,
                TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_TEXT), false);
    }
}
