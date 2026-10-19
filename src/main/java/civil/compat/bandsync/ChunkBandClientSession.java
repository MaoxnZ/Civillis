package civil.compat.bandsync;

import civil.CivilMod;
import civil.CivilPlatform;
import civil.compat.xaeros.XaerosChunkBandHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.Objects;

/**
 * Client bandsync session orchestration: E1/E3 around {@link ChunkBandSessionKey}, L2 snapshots, and {@link ChunkBandSubscriptionHub}.
 */
public final class ChunkBandClientSession {

    private static ChunkBandSessionKey currentKey;
    private static String lastMergedDimensionId;
    /**
     * Join fired before {@link ChunkBandSessionKey#resolve} can succeed (e.g. multiplayer: player not ready yet).
     * We already ran E3 to avoid cross-session tint; this flag means "retry resolve + E1 merge on a later client tick".
     */
    private static boolean pendingSessionBegin;
    /**
     * Last server {@link ChunkBandSubscriptionHub#serverBandCacheScope()} applied to {@link ChunkBandSessionKey} for MP.
     * Last hub {@link ChunkBandSubscriptionHub#serverBandCacheScope()} value consumed by {@link #pollServerBandCacheScope}
     * (including when sync is off, to avoid spinning). Re-synced from the hub after {@link #endSession}.
     */
    private static String lastObservedServerBandCacheScope = "";

    private ChunkBandClientSession() {
    }

    /**
     * When chunk-band C2S subscription drops to zero mid-play-session, persist current L1 to L2 before the Hub clears
     * {@link ChunkBandClientCache} so exploration is not lost.
     */
    public static void flushSnapshotBeforeSubscriptionIdleClear() {
        if (!CivilPlatform.isPhysicalClient()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ChunkBandSessionKey key = currentKey;
        if (key == null) {
            return;
        }
        var snap = ChunkBandClientCache.copyAllDimensionsForPersistence();
        ChunkBandClientSnapshotStore.writeSessionSnapshots(key, snap, mc);
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[bandsync][client] flushed L2 before subscription idle clear (dims={})", snap.size());
        }
    }

    /**
     * After chunk-band subscription becomes active again, merge all on-disk dimension snapshots for this session key
     * into L1 (same order as E1: persisted first; later {@link ChunkBandClientCache#apply} overwrites overlaps).
     */
    public static void reloadPersistedAfterChunkBandSubscribed() {
        if (!CivilPlatform.isPhysicalClient()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ChunkBandSessionKey key = currentKey;
        if (key == null) {
            return;
        }
        ChunkBandClientSnapshotStore.loadAllPersistedDimensions(key, mc);
    }

    public static void tick(Minecraft mc) {
        if (!civil.CivilPlatform.isPhysicalClient()) {
            return;
        }
        if (pendingSessionBegin && mc.level != null && mc.player != null) {
            ChunkBandSessionKey k =
                    ChunkBandSessionKey.resolve(mc, mc.getConnection(), ChunkBandSubscriptionHub.serverBandCacheScope());
            if (k != null) {
                pendingSessionBegin = false;
                if (currentKey != null && !currentKey.equals(k)) {
                    endSession(mc);
                }
                beginSessionKey(mc, k);
            }
        }
        pollServerBandCacheScope(mc);
        XaerosChunkBandHooks.tickSubscription(mc);
        if (mc.level == null || currentKey == null) {
            return;
        }
        String dim = mc.level.dimension().location().toString();
        if (!dim.equals(lastMergedDimensionId)) {
            lastMergedDimensionId = dim;
            ChunkBandClientSnapshotStore.loadAndMergeDimension(currentKey, dim, mc);
        }
    }

    /**
     * When the server-reported band cache scope changes mid-connection (e.g. Velocity-style forced transfer), rebuild MP
     * session so L2 paths and L1 cannot reuse the previous backend's snapshot.
     */
    private static void pollServerBandCacheScope(Minecraft mc) {
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return;
        }
        String s = ChunkBandSubscriptionHub.serverBandCacheScope();
        if (s.isEmpty()) {
            return;
        }
        if (Objects.equals(s, lastObservedServerBandCacheScope)) {
            return;
        }
        lastObservedServerBandCacheScope = s;
        onServerBandCacheScopeChanged(mc, s);
    }

    private static void onServerBandCacheScopeChanged(Minecraft mc, String scope) {
        if (!ChunkBandSubscriptionHub.isServerBandSyncPolicyEnabled()) {
            return;
        }
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return;
        }
        ChunkBandSessionKey k = ChunkBandSessionKey.resolve(mc, mc.getConnection(), scope);
        if (k == null) {
            endSession(mc);
            pendingSessionBegin = true;
            return;
        }
        if (currentKey != null && currentKey.equals(k)) {
            return;
        }
        if (currentKey != null) {
            endSession(mc);
        }
        beginSessionKey(mc, k);
        pendingSessionBegin = false;
    }

    /**
     * Play connection ready: resolve {@link ChunkBandSessionKey}; if it changed, end the previous session (E3) first, then E1.
     * If the key cannot be resolved yet, still run E3 (plan: no stale L1/Hub), then retry begin from {@link #tick}.
     */
    public static void onPlayJoined(Minecraft mc, ClientPacketListener listener) {
        if (!civil.CivilPlatform.isPhysicalClient()) {
            return;
        }
        Objects.requireNonNull(mc, "mc");
        pendingSessionBegin = false;
        ChunkBandSessionKey key =
                ChunkBandSessionKey.resolve(mc, listener, ChunkBandSubscriptionHub.serverBandCacheScope());
        if (key == null) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[bandsync][client] session key unresolved on join -> E3 clear; will retry begin on tick");
            }
            endSession(mc);
            pendingSessionBegin = true;
            return;
        }
        if (currentKey != null && !currentKey.equals(key)) {
            endSession(mc);
        }
        beginSessionKey(mc, key);
    }

    /** Connection closed / return to title (E3). */
    public static void onPlayDisconnect(Minecraft mc) {
        if (!civil.CivilPlatform.isPhysicalClient()) {
            return;
        }
        pendingSessionBegin = false;
        endSession(mc);
        ChunkBandSubscriptionHub.clearMirroredServerPolicyAfterDisconnect();
        lastObservedServerBandCacheScope = ChunkBandSubscriptionHub.serverBandCacheScope();
    }

    public static void endSession(Minecraft mc) {
        if (!civil.CivilPlatform.isPhysicalClient()) {
            return;
        }
        ChunkBandSessionKey key = currentKey;
        if (key != null) {
            var snap = ChunkBandClientCache.copyAllDimensionsForPersistence();
            ChunkBandClientSnapshotStore.writeSessionSnapshots(key, snap, mc);
        }
        ChunkBandSubscriptionHub.resetForSessionEnd();
        ChunkBandClientCache.clearAll();
        currentKey = null;
        lastMergedDimensionId = null;
        lastObservedServerBandCacheScope = ChunkBandSubscriptionHub.serverBandCacheScope();
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[bandsync][client] session end (E3) complete");
        }
    }

    private static void beginSessionKey(Minecraft mc, ChunkBandSessionKey key) {
        currentKey = key;
        lastMergedDimensionId = null;
        if (mc.level != null) {
            lastMergedDimensionId = mc.level.dimension().location().toString();
            ChunkBandClientSnapshotStore.loadAndMergeDimension(currentKey, lastMergedDimensionId, mc);
        }
        if (!key.integrated()) {
            lastObservedServerBandCacheScope = ChunkBandSubscriptionHub.serverBandCacheScope();
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info(
                    "[bandsync][client] session begin integrated={} dir={}",
                    key.integrated(),
                    key.integrated() ? key.integratedWorldRoot() : key.dirSegment());
        }
    }
}
