package civil.aura;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: sonar charge-up start; carries player {@link #regionKindId}
 * (see {@link civil.civilization.CivilRegionKind#id()}) and scan center.
 */
public record SonarChargePayload(
        double centerX,
        double centerY,
        double centerZ,
        byte regionKindId,
        byte sonarType
) implements CustomPacketPayload {

    public static final Type<SonarChargePayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("civil", "sonar_charge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SonarChargePayload> CODEC =
            StreamCodec.ofMember(SonarChargePayload::encode, SonarChargePayload::decode);

    private static void encode(SonarChargePayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeDouble(payload.centerX);
        buf.writeDouble(payload.centerY);
        buf.writeDouble(payload.centerZ);
        buf.writeByte(payload.regionKindId);
        buf.writeByte(payload.sonarType);
    }

    private static SonarChargePayload decode(RegistryFriendlyByteBuf buf) {
        double centerX = buf.readDouble();
        double centerY = buf.readDouble();
        double centerZ = buf.readDouble();
        byte regionKindId = buf.readByte();
        byte sonarType = buf.readByte();
        return new SonarChargePayload(centerX, centerY, centerZ, regionKindId, sonarType);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
