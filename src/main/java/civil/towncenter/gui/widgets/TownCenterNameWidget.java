package civil.towncenter.gui.widgets;

import civil.towncenter.gui.TownCenterGuiPalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Town name widget: non-founders can see the value but cannot focus or edit. */
public class TownCenterNameWidget extends EditBox {

    private static final int TEXT_PAD_X = 4;

    private boolean founderEditable = false;

    public TownCenterNameWidget(Font font, int x, int y, int width, int height, Component hint) {
        super(font, x, y, width, height, hint);
        setBordered(false);
        setTextColor(TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_TEXT));
        setTextColorUneditable(TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_MUTED));
    }

    public void setFounderEditable(boolean editable) {
        founderEditable = editable;
        setEditable(editable);
        if (!editable) {
            setFocused(false);
        }
    }

    public boolean isFounderEditable() {
        return founderEditable;
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused && !founderEditable) {
            return;
        }
        super.setFocused(focused);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (!founderEditable) {
            return false;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        super.onClick(adjustClickEvent(event), doubled);
    }

    @Override
    public int getInnerWidth() {
        return width - TEXT_PAD_X * 2;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) {
            return;
        }
        boolean enabled = founderEditable;
        boolean focused = isFocused() && enabled;
        int borderRgb = !enabled
                ? TownCenterGuiPalette.RGB_BORDER
                : (focused ? TownCenterGuiPalette.RGB_ACCENT : TownCenterGuiPalette.RGB_BORDER);
        int fillRgb = !enabled ? TownCenterGuiPalette.RGB_SLOT_INNER : TownCenterGuiPalette.RGB_PANEL;
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;
        graphics.fill(x, y, x + w, y + h, TownCenterGuiPalette.argb(borderRgb));
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, TownCenterGuiPalette.argb(fillRgb));

        graphics.pose().pushMatrix();
        graphics.pose().translate(TEXT_PAD_X, (h - 8) / 2.0F);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popMatrix();
    }

    private static MouseButtonEvent adjustClickEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() - TEXT_PAD_X, event.y(), event.buttonInfo());
    }
}
