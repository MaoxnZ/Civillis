package civil.towncenter.gui.widgets;

import civil.towncenter.gui.TownCenterGuiPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * Panel-styled button with hover highlight and vanilla cursor when active.
 */
public abstract class TownCenterPanelButtonWidget extends AbstractWidget {

    static final int RGB_HOVER_OVERLAY = 0x28FFFFFF;

    private final boolean elevatedFill;

    protected TownCenterPanelButtonWidget(int x, int y, int w, int h, Component message, boolean elevatedFill) {
        super(x, y, w, h, message);
        this.elevatedFill = elevatedFill;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean enabled = active;
        boolean hovered = isHovered() && enabled;
        drawPanelButton(graphics, getX(), getY(), width, height, enabled, hovered, elevatedFill);
        drawCenteredLabel(graphics, getMessage(), getX(), getY(), width, height, enabled);
        handleCursor(graphics);
    }

    private static void drawPanelButton(
            GuiGraphics graphics,
            int x,
            int y,
            int w,
            int h,
            boolean enabled,
            boolean hovered,
            boolean useElevatedFill) {
        int borderRgb = !enabled
                ? TownCenterGuiPalette.RGB_BORDER
                : (hovered ? TownCenterGuiPalette.RGB_ACCENT : TownCenterGuiPalette.RGB_BORDER);
        int fillRgb = !enabled
                ? TownCenterGuiPalette.RGB_SLOT_INNER
                : (useElevatedFill ? TownCenterGuiPalette.RGB_PANEL_ELEVATED : TownCenterGuiPalette.RGB_PANEL);
        graphics.fill(x, y, x + w, y + h, TownCenterGuiPalette.argb(borderRgb));
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, TownCenterGuiPalette.argb(fillRgb));
        if (hovered) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, RGB_HOVER_OVERLAY);
        }
    }

    private static void drawCenteredLabel(
            GuiGraphics graphics,
            Component label,
            int x,
            int y,
            int w,
            int h,
            boolean enabled) {
        int textRgb = enabled ? TownCenterGuiPalette.RGB_TEXT : TownCenterGuiPalette.RGB_MUTED;
        var font = Minecraft.getInstance().font;
        graphics.drawString(
                font,
                label,
                x + (w - font.width(label)) / 2,
                y + (h - 8) / 2,
                TownCenterGuiPalette.argb(textRgb),
                false);
    }
}
