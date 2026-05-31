package civil.towncenter.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Rename / Confirm toggle for town display name (creator only). */
public class TownCenterRenameButtonWidget extends TownCenterPanelButtonWidget {

    private final Runnable onPress;
    private boolean confirmMode;

    public TownCenterRenameButtonWidget(int x, int y, int w, int h, Runnable onPress) {
        super(x, y, w, h, Component.translatable("gui.civil.town_center.rename"), true);
        this.onPress = onPress;
        this.confirmMode = false;
        refreshLabel();
    }

    public void setConfirmMode(boolean confirm) {
        this.confirmMode = confirm;
        refreshLabel();
    }

    private void refreshLabel() {
        setMessage(Component.translatable(confirmMode
                ? "gui.civil.town_center.rename.confirm"
                : "gui.civil.town_center.rename"));
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (!active) return;
        playDownSound(Minecraft.getInstance().getSoundManager());
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
