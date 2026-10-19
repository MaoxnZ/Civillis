package civil.shrine;

import civil.CivilMod;
import civil.network.CivilS2CPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload: activated farm shrine anchors for ambient ring particles.
 */
public record FarmShrineParticlePayload(List<Entry> entries) implements CivilS2CPacket {

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "farm_shrine_particles");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public record Entry(int x, int y, int z) {
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeVarInt(e.x());
            buf.writeVarInt(e.y());
            buf.writeVarInt(e.z());
        }
    }

    public static FarmShrineParticlePayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new FarmShrineParticlePayload(list);
    }
}
