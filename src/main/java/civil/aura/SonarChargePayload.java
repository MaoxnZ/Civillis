package civil.aura;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Lightweight server-to-client packet signaling the start of the sonar charge-up phase.
 *
 * <p>Sent a few ticks after the detector click (see {@code SonarScanManager.CHARGE_DELAY_TICKS}),
 * before the BFS scan completes. The client uses this to start the charge-up particle column
 * in sync with the server-side charge-up sound.
 *
 * <p>Only carries the minimum information needed for charge-up particle selection:
 * whether the player is in a HIGH zone and whether they are in a head (Force Allow) zone.
 */
public record SonarChargePayload(
        boolean playerInHigh,
        boolean playerInHeadZone
) implements CustomPacketPayload {

    public static final Type<SonarChargePayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("civil", "sonar_charge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SonarChargePayload> CODEC =
            StreamCodec.ofMember(SonarChargePayload::encode, SonarChargePayload::decode);

    private static void encode(SonarChargePayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(payload.playerInHigh);
        buf.writeBoolean(payload.playerInHeadZone);
    }

    private static SonarChargePayload decode(RegistryFriendlyByteBuf buf) {
        boolean playerInHigh = buf.readBoolean();
        boolean playerInHeadZone = buf.readBoolean();
        return new SonarChargePayload(playerInHigh, playerInHeadZone);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
