package civil.towncenter.gui.widgets;

import civil.towncenter.network.TownCenterC2SPayload;
import civil.towncenter.gui.TownCenterGuiHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** Register or leave member action at the bottom of the Members overlay. */
public class MembersActionButtonWidget extends TownCenterPanelButtonWidget {

    public enum Kind {
        REGISTER,
        LEAVE
    }

    private final TownCenterGuiHost host;
    private final Kind kind;

    public MembersActionButtonWidget(TownCenterGuiHost host, int x, int y, int w, int h, Kind kind) {
        super(x, y, w, h, labelFor(kind), true);
        this.host = host;
        this.kind = kind;
    }

    private static Component labelFor(Kind kind) {
        return switch (kind) {
            case REGISTER -> Component.translatable("gui.civil.town_center.members.register");
            case LEAVE -> Component.translatable("gui.civil.town_center.members.leave");
        };
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (!active) return;
        playDownSound(Minecraft.getInstance().getSoundManager());
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        var payload = switch (kind) {
            case REGISTER -> TownCenterC2SPayload.registerMember(host.getMenu().containerId);
            case LEAVE -> TownCenterC2SPayload.unregisterMember(host.getMenu().containerId);
        };
        conn.send(new ServerboundCustomPayloadPacket(payload));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
