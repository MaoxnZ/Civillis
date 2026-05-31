package civil.towncenter.gui;

import civil.towncenter.TownCenterLevelTable;
import civil.towncenter.gui.widgets.TownCenterNameWidget;
import civil.towncenter.network.TownCenterC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Shared town center container screen: navigation, keyboard handling, hotbar slots.
 */
public abstract class TownCenterScreenBase extends AbstractContainerScreen<TownCenterMenu>
        implements TownCenterGuiHost {

    /** World dim when menu is open (vanilla ~0xC0). Lower = brighter background. */
    private static final int BACKGROUND_DIM_ALPHA = 0x80;

    protected static final int RGB_MAX = 0xD7B45A;

    protected EditBox nameBox;

    protected TownCenterScreenBase(TownCenterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = TownCenterMenuLayout.WIDTH;
        this.imageHeight = TownCenterMenuLayout.HEIGHT;
        this.titleLabelX = TownCenterMenuLayout.TITLE_X;
        this.titleLabelY = TownCenterMenuLayout.TITLE_Y;
        this.inventoryLabelY = this.imageHeight + 100;
    }

    protected abstract Identifier backgroundTexture();

    protected abstract TownCenterGuiPage page();

    @Override
    public TownCenterMenu getMenu() {
        return menu;
    }

    @Override
    public void openPage(TownCenterGuiPage page) {
        cancelRenameEditing();
        menu.setPage(page);
        sendPayload(TownCenterC2SPayload.setPage(menu.containerId, page.ordinal()));
        Minecraft mc = Minecraft.getInstance();
        var inv = minecraft.player.getInventory();
        TownCenterScreenBase next = switch (page) {
            case MAIN -> new TownCenterMainScreen(menu, inv, title);
            case MEMBERS -> new TownCenterMembersScreen(menu, inv, title);
            case OPTIONS -> new TownCenterOptionsScreen(menu, inv, title);
        };
        mc.setScreen(next);
    }

    @Override
    public boolean canConfirmUpgrade() {
        if (page() != TownCenterGuiPage.OPTIONS || !menu.hasBenefit() || !menu.isGameplayActive()) {
            return false;
        }
        if (menu.getLevel() >= TownCenterLevelTable.maxLevel()) return false;
        int target = menu.getLevel() + 1;
        if (TownCenterClientState.pendingTargetLevel() != target) return false;
        int cost = menu.getUpgradeCost();
        var donation = menu.getDonationStack();
        if (cost <= 0 || donation.isEmpty()
                || !donation.is(net.minecraft.world.item.Items.EMERALD_BLOCK)
                || donation.getCount() < cost) {
            return false;
        }
        return true;
    }

    @Override
    public void removed() {
        super.removed();
        var mc = Minecraft.getInstance();
        if (mc.screen == null || !(mc.screen instanceof TownCenterScreenBase)) {
            TownCenterClientState.clear();
        }
    }

    public void onProfileSync() {
        cancelRenameEditing();
    }

    /** Members page: exit rename edit without saving. */
    protected void cancelRenameEditing() {
        if (nameBox != null) {
            nameBox.setValue(displayNameForBox());
            nameBox.setFocused(false);
        }
        if (this instanceof TownCenterMembersScreen members) {
            members.setRenaming(false);
            members.applyRenameUi();
        }
    }

    protected String displayNameForBox() {
        String d = TownCenterClientState.displayName();
        return d != null && !d.isBlank() ? d : "";
    }

    protected static void sendPayload(TownCenterC2SPayload payload) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(payload));
        }
    }

    protected EditBox getFocusedNameBox() {
        if (nameBox == null || !nameBox.isFocused()) {
            return null;
        }
        if (nameBox instanceof TownCenterNameWidget townName) {
            return townName.isFounderEditable() ? nameBox : null;
        }
        return menu.isCreator() ? nameBox : null;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        EditBox focused = getFocusedNameBox();
        if (focused != null) {
            if (event.isEscape()) {
                cancelRenameEditing();
                return true;
            }
            if (focused.keyPressed(event)) return true;
            if (minecraft.options.keyInventory.matches(event)) return true;
            if (isTextInputKey(event)) return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        EditBox focused = getFocusedNameBox();
        if (focused != null && focused.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    private static boolean isTextInputKey(KeyEvent event) {
        if (event.isEscape()) return false;
        return true;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, backgroundTexture(),
                leftPos, topPos, 0, 0, imageWidth, imageHeight,
                TownCenterMenuLayout.ATLAS_SIZE, TownCenterMenuLayout.ATLAS_SIZE);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX - font.width(title) / 2, titleLabelY,
                TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_TEXT), false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // AbstractContainerScreen calls this from inside its render pass; the town center
        // draws its backdrop explicitly before that pass so it never covers the atlas.
    }

    private void renderTownCenterBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft != null && minecraft.level != null) {
            int color = BACKGROUND_DIM_ALPHA << 24;
            graphics.fillGradient(0, 0, width, height, color, color);
            return;
        }
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTownCenterBackground(graphics, mouseX, mouseY, partialTick);
        renderBg(graphics, partialTick, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderLevelStrip(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    protected void renderLevelStrip(GuiGraphics graphics) {
        int level = Math.max(1, menu.getLevel());
        int max = TownCenterLevelTable.maxLevel();
        int fillRgb = level >= max ? RGB_MAX : 0xB7C0CE;
        int cellW = TownCenterMenuLayout.levelCellWidth();
        int filled = Math.min(level, max);
        int fillTop = topPos + TownCenterMenuLayout.LEVEL_FILL_TOP;
        int fillBottom = topPos + TownCenterMenuLayout.LEVEL_FILL_BOTTOM;
        for (int i = 0; i < filled; i++) {
            int cx = leftPos + TownCenterMenuLayout.levelCellStart(i);
            graphics.fill(cx, fillTop, cx + cellW, fillBottom, TownCenterGuiPalette.argb(fillRgb));
        }
    }

    protected void renderMainStatus(GuiGraphics graphics) {
        Component display = displayNameComponent();
        String displayStr = display.getString();
        int nameX = leftPos + TownCenterMenuLayout.DISPLAY_NAME_X;
        int nameY = topPos + TownCenterMenuLayout.DISPLAY_NAME_Y;
        graphics.drawString(font,
                ellipsis(font, displayStr, TownCenterMenuLayout.DISPLAY_NAME_MAX_W),
                nameX, nameY, TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_TEXT), false);

        int maxLevel = TownCenterLevelTable.maxLevel();
        boolean atMaxLevel = menu.getLevel() >= maxLevel;
        Component level = atMaxLevel
                ? Component.translatable("gui.civil.town_center.max_level")
                : Component.translatable(
                        "gui.civil.town_center.level",
                        menu.getLevel(),
                        maxLevel);
        int levelRgb = atMaxLevel ? RGB_MAX : TownCenterGuiPalette.RGB_MUTED;
        int levelX = leftPos + TownCenterMenuLayout.DISPLAY_LEVEL_RIGHT - font.width(level);
        graphics.drawString(font, level, levelX, nameY,
                TownCenterGuiPalette.argb(levelRgb), false);

        int titleY = topPos + TownCenterMenuLayout.STATUS_LINE_Y;
        Component status;
        int statusRgb;
        if (menu.getShutdownSecondsRemaining() > 0) {
            status = Component.translatable("gui.civil.town_center.shutdown",
                    menu.getShutdownSecondsRemaining());
            statusRgb = 0xE8A0A0;
        } else {
            status = Component.translatable(menu.isGameplayActive()
                    ? "gui.civil.town_center.status.active"
                    : "gui.civil.town_center.status.inactive");
            statusRgb = menu.isGameplayActive() ? TownCenterGuiPalette.RGB_OK : 0xF2A0A0;
        }
        int statusX = leftPos + TownCenterMenuLayout.MAIN_X + TownCenterMenuLayout.MAIN_WIDTH / 2
                - font.width(status) / 2;
        graphics.drawString(font, status, statusX, titleY, TownCenterGuiPalette.argb(statusRgb), false);

        int muted = TownCenterGuiPalette.RGB_MUTED;
        int leftX = leftPos + TownCenterMenuLayout.MAIN_STATUS_LEFT_X;
        int rightX = leftPos + TownCenterMenuLayout.MAIN_STATUS_RIGHT_X;
        int y = topPos + TownCenterMenuLayout.MAIN_STATUS_Y;
        int step = TownCenterMenuLayout.LINE_HEIGHT;

        String reach = formatReach(menu.getHorizRadiusVc(), menu.getVertRadiusVc());
        drawLine(graphics, leftX, y,
                Component.translatable("gui.civil.town_center.options.preview.range", reach),
                muted);
        drawLine(graphics, leftX, y + step,
                Component.translatable("gui.civil.town_center.options.preview.boost",
                        formatBoost(menu.getBaseScorePermille() / 1000.0)),
                muted);

        var buffLines = TownCenterClientState.mergedAppliedBuffLines();
        int maxRows = TownCenterMenuLayout.MAIN_STATUS_MAX_ROWS;
        if (buffLines.isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("gui.civil.town_center.status.no_active_buff"),
                    rightX, y, TownCenterGuiPalette.argb(muted), false);
        } else {
            for (int i = 0; i < buffLines.size() && i < maxRows; i++) {
                String line = ellipsis(font, buffLines.get(i).getString(), TownCenterMenuLayout.MAIN_STATUS_RIGHT_W);
                graphics.drawString(font, line, rightX, y + i * step, TownCenterGuiPalette.argb(muted), false);
            }
        }
    }

    protected Component displayNameComponent() {
        String d = TownCenterClientState.displayName();
        if (d != null && !d.isBlank()) {
            return Component.literal(d);
        }
        return Component.translatable("gui.civil.town_center.name.unset");
    }

    protected int drawLine(GuiGraphics graphics, int x, int y, Component text, int rgb) {
        graphics.drawString(font, text, x, y, TownCenterGuiPalette.argb(rgb), false);
        return y + TownCenterMenuLayout.LINE_HEIGHT;
    }

    protected static String formatReach(int horiz, int vert) {
        return "±" + horiz + "/±" + vert;
    }

    protected static String formatBoost(double raw) {
        return String.format("%.2f", raw);
    }

    protected static String ellipsis(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String dots = "...";
        int budget = Math.max(0, maxWidth - font.width(dots));
        return font.plainSubstrByWidth(text, budget) + dots;
    }

    protected void drawSectionTitle(GuiGraphics graphics, int colX, int colW, Component text, int y) {
        int colLeft = leftPos + colX;
        int center = colLeft + colW / 2;
        int textW = font.width(text);
        int textX = center - textW / 2;
        int lineY = y + 4;
        int margin = TownCenterMenuLayout.SECTION_TITLE_MARGIN;
        int gap = TownCenterMenuLayout.SECTION_TITLE_LINE_GAP;
        int lineRgb = TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_BORDER);

        int leftEnd = textX - gap;
        int leftStart = colLeft + margin;
        if (leftEnd > leftStart + 2) {
            graphics.fill(leftStart, lineY, leftEnd, lineY + 1, lineRgb);
        }
        int rightStart = textX + textW + gap;
        int rightEnd = colLeft + colW - margin;
        if (rightEnd > rightStart + 2) {
            graphics.fill(rightStart, lineY, rightEnd, lineY + 1, lineRgb);
        }
        graphics.drawString(font, text, textX, y, TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_MUTED), false);
    }

    /** Subpage header: Back on the left, centered title with divider lines (not an input box). */
    protected void drawSubpageTitle(GuiGraphics graphics, Component text) {
        int colLeft = leftPos + TownCenterMenuLayout.MAIN_X;
        int colW = TownCenterMenuLayout.MAIN_WIDTH;
        int center = colLeft + colW / 2;
        int y = topPos + TownCenterMenuLayout.SUBPAGE_TITLE_Y;
        int textW = font.width(text);
        int textX = center - textW / 2;
        int lineY = y + 4;
        int gap = TownCenterMenuLayout.SECTION_TITLE_LINE_GAP;
        int lineRgb = TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_BORDER);

        int leftStart = leftPos + TownCenterMenuLayout.SUBPAGE_TITLE_LEFT_START;
        int leftEnd = textX - gap;
        if (leftEnd > leftStart + 2) {
            graphics.fill(leftStart, lineY, leftEnd, lineY + 1, lineRgb);
        }

        int rightStart = textX + textW + gap;
        int rightEnd = colLeft + colW - TownCenterMenuLayout.SECTION_TITLE_MARGIN;
        if (rightEnd > rightStart + 2) {
            graphics.fill(rightStart, lineY, rightEnd, lineY + 1, lineRgb);
        }

        graphics.drawString(font, text, textX, y, TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_MUTED), false);
    }
}
