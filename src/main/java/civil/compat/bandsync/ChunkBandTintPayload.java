package civil.compat.bandsync;

import civil.CivilMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: civilization tint band per chunk for third-party world maps.
 */
public final class ChunkBandTintPayload implements CustomPacketPayload {

    public static final Type<ChunkBandTintPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CivilMod.MOD_ID, "chunk_band_tint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkBandTintPayload> CODEC =
            StreamCodec.ofMember(ChunkBandTintPayload::encode, ChunkBandTintPayload::decode);

    public static final int MAX_ENTRIES_PER_PACKET = 256;

    private final String dimensionId;
    private final List<Entry> entries;

    public ChunkBandTintPayload(String dimensionId, List<Entry> entries) {
        this.dimensionId = dimensionId;
        this.entries = entries;
    }

    public String dimensionId() {
        return dimensionId;
    }

    public List<Entry> entries() {
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private static void encode(ChunkBandTintPayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeUtf(payload.dimensionId);
        buf.writeVarInt(payload.entries.size());
        for (Entry e : payload.entries) {
            buf.writeInt(e.cx());
            buf.writeInt(e.cz());
            buf.writeByte(e.band());
        }
    }

    private static ChunkBandTintPayload decode(RegistryFriendlyByteBuf buf) {
        String dim = buf.readUtf();
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readInt(), buf.readInt(), buf.readByte()));
        }
        return new ChunkBandTintPayload(dim, list);
    }

    public record Entry(int cx, int cz, byte band) {
    }
}
