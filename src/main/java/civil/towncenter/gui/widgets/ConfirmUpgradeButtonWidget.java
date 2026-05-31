package civil.towncenter.gui.widgets;

import civil.registry.TownCenterLevelRegistry.PaymentTier;
import civil.towncenter.network.TownCenterC2SPayload;
import civil.towncenter.gui.TownCenterClientState;
import civil.towncenter.gui.TownCenterGuiHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

public class ConfirmUpgradeButtonWidget extends TownCenterPanelButtonWidget {

    private final TownCenterGuiHost host;

    public ConfirmUpgradeButtonWidget(TownCenterGuiHost host, int x, int y, int w, int h) {
        super(x, y, w, h, Component.translatable("gui.civil.town_center.upgrade.confirm"), true);
        this.host = host;
    }

    public void refreshActive() {
        active = host.canConfirmUpgrade();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshActive();
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubled) {
        if (!active) return;
        playDownSound(Minecraft.getInstance().getSoundManager());
        int target = host.getMenu().getLevel() + 1;
        PaymentTier tier = TownCenterClientState.paymentTierForIngot(host.getMenu().getIngotStack());
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.send(new ServerboundCustomPayloadPacket(
                    TownCenterC2SPayload.confirmUpgrade(host.getMenu().containerId, target, tier)));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
