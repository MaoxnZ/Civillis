package civil.compat.bandsync;

import civil.CivilMod;
import civil.network.CivilC2SPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: client requests chunk-band sync subscription (logical; server still gates with {@code chunkBand.syncEnabled}).
 */
public final class ChunkBandSubscribePayload implements CivilC2SPacket {

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "chunk_band_sub");

    private final boolean subscribed;

    public ChunkBandSubscribePayload(boolean subscribed) {
        this.subscribed = subscribed;
    }

    public boolean subscribed() {
        return subscribed;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(subscribed);
    }

    public static ChunkBandSubscribePayload read(FriendlyByteBuf buf) {
        return new ChunkBandSubscribePayload(buf.readBoolean());
    }
}
