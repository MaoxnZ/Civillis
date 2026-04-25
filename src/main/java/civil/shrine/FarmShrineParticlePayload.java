package civil.shrine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload: activated farm shrine anchors for ambient ring particles.
 */
public record FarmShrineParticlePayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<FarmShrineParticlePayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("civil", "farm_shrine_particles"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarmShrineParticlePayload> CODEC =
            StreamCodec.ofMember(FarmShrineParticlePayload::encode, FarmShrineParticlePayload::decode);

    public record Entry(int x, int y, int z) {
    }

    private static void encode(FarmShrineParticlePayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(payload.entries.size());
        for (Entry e : payload.entries) {
            buf.writeVarInt(e.x());
            buf.writeVarInt(e.y());
            buf.writeVarInt(e.z());
        }
    }

    private static FarmShrineParticlePayload decode(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new FarmShrineParticlePayload(list);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
