package civil.towncenter.gui.widgets;

import civil.towncenter.network.TownCenterC2SPayload;
import civil.towncenter.gui.TownCenterGuiHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** Open-registration toggle: Unlock when closed, Lock when open (creator only). */
public class OpenRegistrationWidget extends TownCenterPanelButtonWidget {

    private final TownCenterGuiHost host;

    public OpenRegistrationWidget(TownCenterGuiHost host, int x, int y, int w, int h, boolean editable) {
        super(x, y, w, h, labelFor(host.getMenu().isOpenRegistration()), true);
        this.host = host;
        this.active = editable;
    }

    private static Component labelFor(boolean openRegistration) {
        return Component.translatable(openRegistration
                ? "gui.civil.town_center.lock_registration"
                : "gui.civil.town_center.open_registration");
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (!active) return;
        playDownSound(Minecraft.getInstance().getSoundManager());
        boolean next = !host.getMenu().isOpenRegistration();
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(
                    TownCenterC2SPayload.setOpenRegistration(host.getMenu().containerId, next)));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
