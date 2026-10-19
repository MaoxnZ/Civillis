package civil.compat.bandsync;

import civil.CivilMod;
import civil.network.CivilS2CPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C: civilization tint band per chunk for third-party world maps.
 */
public final class ChunkBandTintPayload implements CivilS2CPacket {

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "chunk_band_tint");
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
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(dimensionId);
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeInt(e.cx());
            buf.writeInt(e.cz());
            buf.writeByte(e.band());
        }
    }

    public static ChunkBandTintPayload read(FriendlyByteBuf buf) {
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
