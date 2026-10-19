package civil.compat.bandsync;

import civil.CivilMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: client requests chunk-band sync subscription (logical; server still gates with {@code chunkBand.syncEnabled}).
 */
public final class ChunkBandSubscribePayload implements CustomPacketPayload {

    public static final Type<ChunkBandSubscribePayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CivilMod.MOD_ID, "chunk_band_sub"));
    public static final StreamCodec<FriendlyByteBuf, ChunkBandSubscribePayload> CODEC =
            StreamCodec.ofMember(ChunkBandSubscribePayload::encode, ChunkBandSubscribePayload::decode);

    private final boolean subscribed;

    public ChunkBandSubscribePayload(boolean subscribed) {
        this.subscribed = subscribed;
    }

    public boolean subscribed() {
        return subscribed;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private static void encode(ChunkBandSubscribePayload payload, FriendlyByteBuf buf) {
        buf.writeBoolean(payload.subscribed);
    }

    private static ChunkBandSubscribePayload decode(FriendlyByteBuf buf) {
        return new ChunkBandSubscribePayload(buf.readBoolean());
    }
}
