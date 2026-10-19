package civil.respawn;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload with undying anchor particle state.
 * Sent once per second for anchors at full civilization.
 *
 * <p>Tier: 1 = CHARGE (mini pillar + enchant glow), 2 = READY (spinning circle + totem particles).
 * Anchors not in the list should stop rendering.
 */
public record UndyingAnchorParticlePayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<UndyingAnchorParticlePayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("civil", "undying_anchor_particles"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UndyingAnchorParticlePayload> CODEC =
            StreamCodec.ofMember(UndyingAnchorParticlePayload::encode, UndyingAnchorParticlePayload::decode);

    public record Entry(int x, int y, int z, byte tier) {
        public static final byte TIER_CHARGE = 1;
        public static final byte TIER_READY = 2;
    }

    private static void encode(UndyingAnchorParticlePayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(payload.entries.size());
        for (Entry e : payload.entries) {
            buf.writeVarInt(e.x());
            buf.writeVarInt(e.y());
            buf.writeVarInt(e.z());
            buf.writeByte(e.tier());
        }
    }

    private static UndyingAnchorParticlePayload decode(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte()));
        }
        return new UndyingAnchorParticlePayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
