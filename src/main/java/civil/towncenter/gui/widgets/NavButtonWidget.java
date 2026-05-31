package civil.towncenter.gui.widgets;

import civil.towncenter.gui.TownCenterGuiHost;
import civil.towncenter.gui.TownCenterGuiPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class NavButtonWidget extends TownCenterPanelButtonWidget {

    private final TownCenterGuiHost host;
    private final TownCenterGuiPage target;

    public NavButtonWidget(
            TownCenterGuiHost host,
            TownCenterGuiPage target,
            int x,
            int y,
            int w,
            int h,
            Component label) {
        super(x, y, w, h, label, true);
        this.host = host;
        this.target = target;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        playDownSound(Minecraft.getInstance().getSoundManager());
        host.openPage(target);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
