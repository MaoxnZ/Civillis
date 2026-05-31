package civil.towncenter.gui.widgets;

import civil.towncenter.gui.TownCenterGuiPalette;
import civil.towncenter.gui.TownCenterMenu;
import civil.towncenter.gui.TownCenterMenuLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Custom activation switch with vanilla-like hover (highlight + pointing hand cursor).
 */
public class ActivationToggleWidget extends AbstractWidget {

    private static final int RGB_TOGGLE_ON = 0x2F8F62;
    private static final int RGB_TOGGLE_OFF = 0x9A4545;
    private static final int RGB_THUMB_FACE = 0xE8EDF7;
    private static final int RGB_THUMB_EDGE = 0x9BA7BD;
    private static final int RGB_HOVER_OVERLAY = 0x28FFFFFF;

    private final TownCenterMenu menu;

    public ActivationToggleWidget(TownCenterMenu menu, int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
        this.menu = menu;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered();
        boolean shutdown = menu.isShutdownPending();
        boolean active = menu.isGameplayActive() && !shutdown;
        int offRgb = shutdown ? 0xB85C5C : RGB_TOGGLE_OFF;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int trackH = TownCenterMenuLayout.TOGGLE_TRACK_HEIGHT;
        int thumb = TownCenterMenuLayout.TOGGLE_THUMB_SIZE;
        int trackY = y + (h - trackH) / 2;
        int grooveLeft = x + 2;
        int grooveRight = x + w - 2;

        int borderRgb = hovered ? TownCenterGuiPalette.RGB_ACCENT : TownCenterGuiPalette.RGB_BORDER;
        graphics.fill(x, y, x + w, y + h, TownCenterGuiPalette.argb(borderRgb));

        int fillRgb = brighten(active ? RGB_TOGGLE_ON : offRgb, hovered);
        graphics.fill(grooveLeft, trackY, grooveRight, trackY + trackH, TownCenterGuiPalette.argb(fillRgb));

        int thumbX = active ? x + w - thumb - 2 : x + 2;
        int thumbY = y + (h - thumb) / 2;
        int thumbEdge = hovered ? TownCenterGuiPalette.RGB_TEXT : RGB_THUMB_EDGE;
        int thumbFace = hovered ? 0xF4F7FC : RGB_THUMB_FACE;
        graphics.fill(thumbX, thumbY, thumbX + thumb, thumbY + thumb, TownCenterGuiPalette.argb(thumbEdge));
        graphics.fill(thumbX + 1, thumbY + 1, thumbX + thumb - 1, thumbY + thumb - 1,
                TownCenterGuiPalette.argb(thumbFace));

        if (hovered) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, RGB_HOVER_OVERLAY);
        }
        handleCursor(graphics);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            playDownSound(minecraft.getSoundManager());
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TownCenterMenu.BUTTON_TOGGLE_ACTIVATION);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(
                NarratedElementType.TITLE,
                Component.translatable(menu.isActivated()
                        ? "gui.civil.town_center.status.active"
                        : "gui.civil.town_center.status.inactive"));
    }

    private static int brighten(int rgb, boolean hovered) {
        if (!hovered) {
            return rgb;
        }
        int r = Math.min(255, ((rgb >> 16) & 0xFF) + 28);
        int g = Math.min(255, ((rgb >> 8) & 0xFF) + 28);
        int b = Math.min(255, (rgb & 0xFF) + 28);
        return (r << 16) | (g << 8) | b;
    }
}
