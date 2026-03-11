package civil.respawn;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client packet that starts the pre-teleport phase (Phase 0).
 * Player is still at death location; teleport happens after phase0Ticks.
 * Anchor position is sent so client can play totem sound/particles at anchor (stable, no desync).
 */
public record UndyingAnchorPreTeleportPayload(int phase0Ticks, int anchorX, int anchorY, int anchorZ) implements CustomPacketPayload {

    public static final Type<UndyingAnchorPreTeleportPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("civil", "undying_anchor_pre_teleport"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UndyingAnchorPreTeleportPayload> CODEC =
            StreamCodec.ofMember(UndyingAnchorPreTeleportPayload::encode, UndyingAnchorPreTeleportPayload::decode);

    private static void encode(UndyingAnchorPreTeleportPayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(payload.phase0Ticks());
        buf.writeVarInt(payload.anchorX());
        buf.writeVarInt(payload.anchorY());
        buf.writeVarInt(payload.anchorZ());
    }

    private static UndyingAnchorPreTeleportPayload decode(RegistryFriendlyByteBuf buf) {
        return new UndyingAnchorPreTeleportPayload(
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
