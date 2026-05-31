package civil.towncenter.gui;

/**
 * Colors aligned with {@code roadmap/src/styles.css} (midnight panel UI).
 */
public final class TownCenterGuiPalette {

    public static final int RGB_PANEL = 0x151A23;
    public static final int RGB_PANEL_ELEVATED = 0x1A2130;
    public static final int RGB_BORDER = 0x273046;
    public static final int RGB_TEXT = 0xE8EDF7;
    public static final int RGB_MUTED = 0x9BA7BD;
    public static final int RGB_ACCENT = 0x7EA2FF;
    public static final int RGB_OK = 0x34D399;
    public static final int RGB_SLOT_INNER = 0x373737;

    private TownCenterGuiPalette() {}

    /** Full ARGB color; required for {@link net.minecraft.client.gui.GuiGraphics#drawString}. */
    public static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

}
