package civil.compat.bandsync;

import civil.CivilMod;
import civil.network.CivilS2CPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: authoritative {@link civil.config.CivilConfig#chunkBandSyncEnabled} for this session so the
 * client {@link ChunkBandSubscriptionHub} mirrors it and aligns refcount / cache when the server master switch flips.
 * Also carries a stable per-world {@link #cacheScope()} so multiplayer clients do not reuse the same on-disk cache
 * across different backend worlds that share one proxy address.
 */
public final class ChunkBandSyncPolicyPayload implements CivilS2CPacket {

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "chunk_band_sync_policy");
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
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(syncEnabled);
        buf.writeUtf(cacheScope, MAX_CACHE_SCOPE_UTF);
    }

    public static ChunkBandSyncPolicyPayload read(FriendlyByteBuf buf) {
        boolean en = buf.readBoolean();
        String scope = buf.readUtf(MAX_CACHE_SCOPE_UTF);
        return new ChunkBandSyncPolicyPayload(en, scope);
    }
}
