package civil.respawn;

import civil.CivilMod;
import civil.network.CivilS2CPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload with undying anchor particle state.
 * Sent once per second for anchors at full civilization.
 *
 * <p>Tier: 1 = CHARGE (mini pillar + enchant glow), 2 = READY (spinning circle + totem particles).
 * Anchors not in the list should stop rendering.
 */
public record UndyingAnchorParticlePayload(List<Entry> entries) implements CivilS2CPacket {

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "undying_anchor_particles");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public record Entry(int x, int y, int z, byte tier) {
        public static final byte TIER_CHARGE = 1;
        public static final byte TIER_READY = 2;
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

    public static UndyingAnchorParticlePayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte()));
        }
        return new UndyingAnchorParticlePayload(list);
    }
}
