package civil.towncenter;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One-shot client burst when a town center is first activated with an emerald.
 */
public record TownCenterActivationBurstPayload(int x, int y, int z) implements CustomPacketPayload {

    public static final Type<TownCenterActivationBurstPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("civil", "town_center_activation_burst"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TownCenterActivationBurstPayload> CODEC =
            StreamCodec.ofMember(TownCenterActivationBurstPayload::encode, TownCenterActivationBurstPayload::decode);

    private static void encode(TownCenterActivationBurstPayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(payload.x());
        buf.writeVarInt(payload.y());
        buf.writeVarInt(payload.z());
    }

    private static TownCenterActivationBurstPayload decode(RegistryFriendlyByteBuf buf) {
        return new TownCenterActivationBurstPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
