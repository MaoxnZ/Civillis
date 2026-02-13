package civil.aura;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

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
) implements CustomPayload {

    public static final Id<SonarChargePayload> ID =
            new Id<>(Identifier.of("civil", "sonar_charge"));

    public static final PacketCodec<RegistryByteBuf, SonarChargePayload> CODEC =
            PacketCodec.of(SonarChargePayload::encode, SonarChargePayload::decode);

    private static void encode(SonarChargePayload payload, RegistryByteBuf buf) {
        buf.writeBoolean(payload.playerInHigh);
        buf.writeBoolean(payload.playerInHeadZone);
    }

    private static SonarChargePayload decode(RegistryByteBuf buf) {
        boolean playerInHigh = buf.readBoolean();
        boolean playerInHeadZone = buf.readBoolean();
        return new SonarChargePayload(playerInHigh, playerInHeadZone);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
