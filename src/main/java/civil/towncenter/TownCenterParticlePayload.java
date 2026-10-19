package civil.towncenter;

import civil.CivilMod;
import civil.network.CivilS2CPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload: gameplay-active town centers for client particles.
 * Tier 1–5 = active level; 6 = shutdown countdown visuals.
 */
public record TownCenterParticlePayload(List<Entry> entries) implements CivilS2CPacket {

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "town_center_particles");

    public record Entry(int x, int y, int z, byte tier) {
        public static final byte TIER_ACTIVE_1 = 1;
        public static final byte TIER_ACTIVE_5 = 5;
        public static final byte TIER_SHUTDOWN = 6;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry e : entries) {
            buf.writeVarInt(e.x());
            buf.writeVarInt(e.y());
            buf.writeVarInt(e.z());
            buf.writeByte(e.tier());
        }
    }

    public static TownCenterParticlePayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte()));
        }
        return new TownCenterParticlePayload(list);
    }
}
