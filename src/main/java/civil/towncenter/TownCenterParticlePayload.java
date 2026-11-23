package civil.towncenter;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload: gameplay-active town centers for client particles.
 * Tier 1–5 = active level; 6 = shutdown countdown visuals.
 */
public record TownCenterParticlePayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<TownCenterParticlePayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("civil", "town_center_particles"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TownCenterParticlePayload> CODEC =
            StreamCodec.ofMember(TownCenterParticlePayload::encode, TownCenterParticlePayload::decode);

    public record Entry(int x, int y, int z, byte tier) {
        public static final byte TIER_ACTIVE_1 = 1;
        public static final byte TIER_ACTIVE_5 = 5;
        public static final byte TIER_SHUTDOWN = 6;
    }

    private static void encode(TownCenterParticlePayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(payload.entries.size());
        for (Entry e : payload.entries) {
            buf.writeVarInt(e.x());
            buf.writeVarInt(e.y());
            buf.writeVarInt(e.z());
            buf.writeByte(e.tier());
        }
    }

    private static TownCenterParticlePayload decode(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte()));
        }
        return new TownCenterParticlePayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
