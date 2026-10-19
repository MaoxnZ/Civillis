package civil.compat.bandsync;

import civil.CivilMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: authoritative {@link civil.config.CivilConfig#chunkBandSyncEnabled} for this session so the
 * client {@link ChunkBandSubscriptionHub} mirrors it and aligns refcount / cache when the server master switch flips.
 * Also carries a stable per-world {@link #cacheScope()} so multiplayer clients do not reuse the same on-disk cache
 * across different backend worlds that share one proxy address.
 */
public final class ChunkBandSyncPolicyPayload implements CustomPacketPayload {

    public static final Type<ChunkBandSyncPolicyPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CivilMod.MOD_ID, "chunk_band_sync_policy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkBandSyncPolicyPayload> CODEC =
            StreamCodec.ofMember(ChunkBandSyncPolicyPayload::encode, ChunkBandSyncPolicyPayload::decode);

    private static final int MAX_CACHE_SCOPE_UTF = 128;

    private final boolean syncEnabled;
    /** Server-derived stable id (typically hex); empty means client must not form an MP {@link ChunkBandSessionKey}. */
    private final String cacheScope;

    public ChunkBandSyncPolicyPayload(boolean syncEnabled, String cacheScope) {
        this.syncEnabled = syncEnabled;
        this.cacheScope = cacheScope == null ? "" : cacheScope;
    }

    public boolean syncEnabled() {
        return syncEnabled;
    }

    public String cacheScope() {
        return cacheScope;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    private static void encode(ChunkBandSyncPolicyPayload payload, RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(payload.syncEnabled);
        buf.writeUtf(payload.cacheScope, MAX_CACHE_SCOPE_UTF);
    }

    private static ChunkBandSyncPolicyPayload decode(RegistryFriendlyByteBuf buf) {
        boolean en = buf.readBoolean();
        String scope = buf.readUtf(MAX_CACHE_SCOPE_UTF);
        return new ChunkBandSyncPolicyPayload(en, scope);
    }
}
