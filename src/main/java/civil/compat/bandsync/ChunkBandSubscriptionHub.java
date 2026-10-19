package civil.compat.bandsync;

import civil.CivilMod;
import civil.CivilPlatform;
import civil.config.CivilConfig;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-only refcount for C2S {@link ChunkBandSubscribePayload}; one logical subscription when any consumer holds a ref.
 * Server {@code chunkBand.syncEnabled} is mirrored here via {@link ChunkBandSyncPolicyPayload}.
 */
public final class ChunkBandSubscriptionHub {

    private static final Map<String, Integer> REFS = new HashMap<>();
    private static boolean subscribed;

    /** Authoritative server master switch for this connection (S2C); default true until first policy packet. */
    private static volatile boolean serverChunkBandSyncEnabled = true;

    /**
     * Server-derived stable id from {@link ChunkBandSyncPolicyPayload#cacheScope()}; empty until first policy on this
     * client connection. Used by {@link ChunkBandSessionKey} for multiplayer L2 namespacing.
     */
    private static volatile String serverBandCacheScope = "";

    /**
     * Set by {@link civil.compat.journeymap.CivilJourneyMapPlugin} on JourneyMap {@code MAPPING_STARTED} / {@code MAPPING_STOPPED}.
     * Used so {@link #onCivilConfigReloaded()} can re-{@link #acquire} after the user turns the Civil JM toggle back on
     * mid-session (JM does not re-fire {@code MAPPING_STARTED} while mapping continues).
     */
    private static volatile boolean journeyMapMappingSessionActive;

    private ChunkBandSubscriptionHub() {
    }

    public static void setJourneyMapMappingSessionActive(boolean active) {
        journeyMapMappingSessionActive = active;
    }

    private static boolean isServerChunkBandSyncEnabled() {
        return serverChunkBandSyncEnabled;
    }

    /** Exposed for {@link ChunkBandClientSession} (e.g. avoid re-merging L2 while sync is disabled). */
    public static boolean isServerBandSyncPolicyEnabled() {
        return serverChunkBandSyncEnabled;
    }

    public static String serverBandCacheScope() {
        return serverBandCacheScope;
    }

    private static void setServerChunkBandSyncEnabled(boolean enabled) {
        serverChunkBandSyncEnabled = enabled;
    }

    private static String normalizeBandCacheScope(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        if (s.length() > 128) {
            return s.substring(0, 128);
        }
        return s;
    }

    /**
     * Applies authoritative server {@link ChunkBandSyncPolicyPayload} on the client thread: clears local refs,
     * sends C2S {@code false} if needed, {@link ChunkBandClientCache#clearAll()}, or re-evaluates subscription when re-enabled.
     */
    public static void onServerChunkBandSyncPolicy(boolean serverSyncEnabled, String rawCacheScope) {
        if (!CivilPlatform.isPhysicalClient()) {
            return;
        }
        serverBandCacheScope = normalizeBandCacheScope(rawCacheScope);
        setServerChunkBandSyncEnabled(serverSyncEnabled);
        if (!serverSyncEnabled) {
            REFS.clear();
            if (subscribed) {
                subscribed = false;
                CivilPlatform.sendToServer(new ChunkBandSubscribePayload(false));
            }
            ChunkBandClientSession.flushSnapshotBeforeSubscriptionIdleClear();
            ChunkBandClientCache.clearAll();
            return;
        }
        recomputeSubscription();
    }

    public static void onCivilConfigReloaded() {
        if (!CivilPlatform.isPhysicalClient()) {
            return;
        }
        boolean anyForceReleased = false;
        for (ChunkBandClientConsumerGates.Gate gate : ChunkBandClientConsumerGates.GATED) {
            if (gate.civilClientEnabled().getAsBoolean()) {
                continue;
            }
            if (CivilMod.DEBUG && isHeld(gate.consumerId())) {
                CivilMod.LOGGER.info("[bandsync][client] config: client toggle off -> force release {}", gate.consumerId());
            }
            if (forceReleaseConsumer(gate.consumerId())) {
                anyForceReleased = true;
            }
        }
        if (anyForceReleased) {
            ChunkBandClientCache.notifyOverlayListeners();
        }
        // Re-acquire when a gated consumer's Civil toggle is turned ON while its session is still active (JM: no second
        // MAPPING_STARTED until mapping stops).
        if (CivilConfig.chunkBandClientEnabledJourneyMap
                && journeyMapMappingSessionActive
                && !isHeld(ChunkBandConsumers.JOURNEY_MAP)) {
            acquire(ChunkBandConsumers.JOURNEY_MAP);
            ChunkBandClientCache.notifyOverlayListeners();
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[bandsync][client] config: JM toggle on mid-mapping -> re-acquire {}", ChunkBandConsumers.JOURNEY_MAP);
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            if (CivilConfig.chunkBandClientEnabledXaeros) {
                if (CivilPlatform.isModLoaded("xaerominimap")
                        && CivilPlatform.isModLoaded("xaeroworldmap")
                        && !isHeld(ChunkBandConsumers.XAEROS_MINIMAP)) {
                    acquire(ChunkBandConsumers.XAEROS_MINIMAP);
                    ChunkBandClientCache.notifyOverlayListeners();
                    if (CivilMod.DEBUG) {
                        CivilMod.LOGGER.info(
                                "[bandsync][client] config: Xaero toggle on in-world -> re-acquire {}",
                                ChunkBandConsumers.XAEROS_MINIMAP);
                    }
                }
                if (CivilPlatform.isModLoaded("xaerominimap")
                        && CivilPlatform.isModLoaded("xaeroworldmap")
                        && !isHeld(ChunkBandConsumers.XAEROS_WORLDMAP)) {
                    acquire(ChunkBandConsumers.XAEROS_WORLDMAP);
                    ChunkBandClientCache.notifyOverlayListeners();
                    if (CivilMod.DEBUG) {
                        CivilMod.LOGGER.info(
                                "[bandsync][client] config: Xaero toggle on in-world -> re-acquire {}",
                                ChunkBandConsumers.XAEROS_WORLDMAP);
                    }
                }
            }
        }
    }

    public static boolean isHeld(String consumerId) {
        return REFS.getOrDefault(consumerId, 0) > 0;
    }

    public static void acquire(String consumerId) {
        if (!CivilPlatform.isPhysicalClient()) {
            return;
        }
        if (!isServerChunkBandSyncEnabled()) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[bandsync][client] acquire noop (server chunkBand.syncEnabled=false) consumer={}", consumerId);
            }
            return;
        }
        if (ChunkBandClientConsumerGates.isBlockedFromAcquire(consumerId)) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[bandsync][client] acquire noop (Civil client toggle off) consumer={}", consumerId);
            }
            return;
        }
        int n = REFS.merge(consumerId, 1, Integer::sum);
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[bandsync][client] acquire consumer={} refCount={}", consumerId, n);
        }
        if (n == 1) {
            recomputeSubscription();
        }
    }

    public static void release(String consumerId) {
        if (!CivilPlatform.isPhysicalClient()) {
            return;
        }
        Integer cur = REFS.get(consumerId);
        if (cur == null || cur <= 0) {
            return;
        }
        if (cur == 1) {
            REFS.remove(consumerId);
        } else {
            REFS.put(consumerId, cur - 1);
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[bandsync][client] release consumer={} refCount->{} refs={}", consumerId, REFS.getOrDefault(consumerId, 0), REFS);
        }
        recomputeSubscription();
    }

    /**
     * When a consumer toggle goes false while still “mapping”; drop that consumer's ref.
     *
     * @return {@code true} if that consumer had a positive refcount and was removed (subscription may have changed).
     */
    public static boolean forceReleaseConsumer(String consumerId) {
        if (!CivilPlatform.isPhysicalClient()) {
            return false;
        }
        if (REFS.remove(consumerId) == null) {
            return false;
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[bandsync][client] forceReleaseConsumer {} refs={}", consumerId, REFS);
        }
        recomputeSubscription();
        return true;
    }

    public static void onLastSubscriptionReleased() {
        ChunkBandClientSession.flushSnapshotBeforeSubscriptionIdleClear();
        ChunkBandClientCache.clearAll();
    }

    private static void recomputeSubscription() {
        boolean want = !REFS.isEmpty();
        if (want == subscribed) {
            return;
        }
        boolean wasSubscribed = subscribed;
        subscribed = want;
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[bandsync][client] C2S chunk_band_sub subscribed={} refs={}", want, REFS);
        }
        CivilPlatform.sendToServer(new ChunkBandSubscribePayload(want));
        if (!want) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[bandsync][client] last subscription released -> flush L2 + clear ChunkBandClientCache");
            }
            onLastSubscriptionReleased();
        } else if (!wasSubscribed) {
            ChunkBandClientSession.reloadPersistedAfterChunkBandSubscribed();
        }
    }

    /**
     * Session boundary (E3): clears local subscription refs without invoking {@link #onLastSubscriptionReleased()}
     * — the caller persists/flushes {@link ChunkBandClientCache} first, then clears it. Server {@code SUBSCRIBED}
     * is cleared on disconnect via {@link ChunkBandTintSync#onPlayerLogout}; no C2S unsubscribe here.
     */
    public static void resetForSessionEnd() {
        if (!CivilPlatform.isPhysicalClient()) {
            return;
        }
        journeyMapMappingSessionActive = false;
        REFS.clear();
        subscribed = false;
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[bandsync][client] Hub reset for session end (local refs cleared; mirrored S2C policy unchanged)");
        }
    }

    /**
     * Clears mirrored {@link ChunkBandSyncPolicyPayload} fields after the play connection is gone; the next connection
     * starts with defaults until the server sends policy again. Not invoked on in-process session key rotation.
     */
    public static void clearMirroredServerPolicyAfterDisconnect() {
        if (!CivilPlatform.isPhysicalClient()) {
            return;
        }
        setServerChunkBandSyncEnabled(true);
        serverBandCacheScope = "";
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[bandsync][client] Hub mirrored server policy cleared (disconnect)");
        }
    }
}
