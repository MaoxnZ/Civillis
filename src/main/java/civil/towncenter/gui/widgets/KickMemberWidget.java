package civil.towncenter.gui.widgets;

import civil.towncenter.network.TownCenterC2SPayload;
import civil.towncenter.gui.TownCenterGuiHost;
import civil.towncenter.gui.TownCenterGuiPalette;
import civil.towncenter.gui.TownCenterMenuLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

import java.util.UUID;

/** 12×12 kick control for a member row (creator only). */
public class KickMemberWidget extends AbstractWidget {

    private static final int RGB_KICK = 0xB83C3C;
    private static final int RGB_KICK_BORDER = 0x7A2020;

    private final TownCenterGuiHost host;
    private final UUID targetUuid;

    public KickMemberWidget(TownCenterGuiHost host, int x, int y, UUID targetUuid) {
        super(x, y, TownCenterMenuLayout.MEMBERS_KICK_SIZE, TownCenterMenuLayout.MEMBERS_KICK_SIZE,
                Component.translatable("gui.civil.town_center.members.kick"));
        this.host = host;
        this.targetUuid = targetUuid;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean enabled = active;
        boolean hovered = isHovered() && enabled;
        int fill = enabled ? RGB_KICK : 0x5A3030;
        int border = enabled && hovered ? TownCenterGuiPalette.RGB_ACCENT : RGB_KICK_BORDER;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, TownCenterGuiPalette.argb(border));
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1,
                TownCenterGuiPalette.argb(fill));
        if (hovered) {
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1,
                    TownCenterPanelButtonWidget.RGB_HOVER_OVERLAY);
        }
        graphics.drawCenteredString(Minecraft.getInstance().font, "×",
                getX() + width / 2, getY() + 2, TownCenterGuiPalette.argb(TownCenterGuiPalette.RGB_TEXT));
        handleCursor(graphics);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (!active) return;
        playDownSound(Minecraft.getInstance().getSoundManager());
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(
                    TownCenterC2SPayload.kickMember(host.getMenu().containerId, targetUuid)));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
