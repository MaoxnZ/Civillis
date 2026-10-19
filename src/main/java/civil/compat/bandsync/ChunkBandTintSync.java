package civil.compat.bandsync;

import civil.CivilMod;
import civil.CivilPlatform;
import civil.config.CivilConfig;
import civil.map.CivilMapTintLogic;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server: subscribed players, tick-driven chunk ring scan; each wave sends all loaded chunks in the ring via
 * {@link ChunkBandTintPayload} (client merges sparse overlay entries).
 */
public final class ChunkBandTintSync {

    /** Same semantics as prefetch gate: skip chunk-band wave when Chebyshev block step exceeds this. */
    private static final int CHUNK_BAND_MAX_BLOCK_STEP = 2;

    private static final Set<UUID> SUBSCRIBED = ConcurrentHashMap.newKeySet();
    private static final Object2ObjectOpenHashMap<UUID, PlayerBandState> STATE = new Object2ObjectOpenHashMap<>();

    private static boolean lastSyncEnabled = true;

    /** Per scan wave (same server tick interval pass), for {@link CivilMod#DEBUG} stats only. */
    private static int dbgWavePackets;
    private static int dbgWaveEntries;

    private ChunkBandTintSync() {
    }

    public static void applySubscribe(ServerPlayer player, boolean subscribed) {
        if (!CivilConfig.chunkBandSyncEnabled) {
            if (subscribed) {
                if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.info("[bandsync][server] subscribe ignored (chunkBand.syncEnabled=false) player={}", player.getUUID());
                }
                return;
            }
        }
        UUID id = player.getUUID();
        if (subscribed) {
            SUBSCRIBED.add(id);
            STATE.put(id, new PlayerBandState());
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[bandsync][server] subscribed player={} (total subscribers={})", id, SUBSCRIBED.size());
            }
        } else {
            SUBSCRIBED.remove(id);
            STATE.remove(id);
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[bandsync][server] unsubscribed player={} (total subscribers={})", id, SUBSCRIBED.size());
            }
        }
    }

    public static void onPlayerLogout(ServerPlayer player) {
        UUID id = player.getUUID();
        if (SUBSCRIBED.remove(id) && CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[bandsync][server] logout removed subscriber player={} (remaining={})", id, SUBSCRIBED.size());
        }
        STATE.remove(id);
    }

    /** Sends current {@link CivilConfig#chunkBandSyncEnabled} so the client Hub/cache match the server master switch. */
    public static void sendSyncPolicyToPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        String scope = bandCacheScopeForServerWorld(server);
        CivilPlatform.sendToPlayer(player, new ChunkBandSyncPolicyPayload(CivilConfig.chunkBandSyncEnabled, scope));
    }

    private static void broadcastSyncPolicy(MinecraftServer server, boolean syncEnabled) {
        String scope = bandCacheScopeForServerWorld(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CivilPlatform.sendToPlayer(p, new ChunkBandSyncPolicyPayload(syncEnabled, scope));
        }
    }

    /**
     * Stable per-save identifier for client L2 namespacing: derived from this JVM's world root path so Velocity/Bungee
     * backends (different processes / folders) naturally diverge even when the client sees one proxy endpoint.
     */
    public static String bandCacheScopeForServerWorld(MinecraftServer server) {
        try {
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(root.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void onServerTickEnd(MinecraftServer server) {
        boolean enabled = CivilConfig.chunkBandSyncEnabled;
        if (!enabled) {
            if (lastSyncEnabled) {
                if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.info("[bandsync][server] chunkBand.syncEnabled -> false; cleared {} subscriber(s)", SUBSCRIBED.size());
                }
                SUBSCRIBED.clear();
                STATE.clear();
                broadcastSyncPolicy(server, false);
            }
            lastSyncEnabled = false;
            return;
        }
        boolean wasDisabled = !lastSyncEnabled;
        lastSyncEnabled = true;
        if (wasDisabled) {
            broadcastSyncPolicy(server, true);
        }

        if (SUBSCRIBED.isEmpty()) {
            return;
        }

        int interval = Math.max(1, CivilConfig.chunkBandIntervalTicks);
        boolean waveTick = server.getTickCount() % interval == 0;
        if (waveTick) {
            dbgWavePackets = 0;
            dbgWaveEntries = 0;
        }
        int r = Math.max(0, CivilConfig.chunkBandRadiusChunks);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SUBSCRIBED.contains(player.getUUID())) {
                continue;
            }
            PlayerBandState pState = STATE.get(player.getUUID());
            if (pState == null) {
                continue;
            }
            BlockPos pos = player.blockPosition();
            String dim = player.serverLevel().dimension().location().toString();
            boolean dimChanged = pState.lastMovementDim == null || !pState.lastMovementDim.equals(dim);
            boolean skipForSpeed = blockStepTooFastForChunkBand(pState.lastTickBlockPos, pos, dimChanged);
            if (waveTick && !skipForSpeed) {
                tickPlayer(player, r);
            }
            pState.lastTickBlockPos = pos.immutable();
            pState.lastMovementDim = dim;
        }
        if (CivilMod.DEBUG && waveTick && dbgWavePackets > 0) {
            CivilMod.LOGGER.info(
                    "[bandsync][server] S2C wave tick={} interval={}t R={}ch subscribers={} packets={} chunk_entries={}",
                    server.getTickCount(),
                    interval,
                    r,
                    SUBSCRIBED.size(),
                    dbgWavePackets,
                    dbgWaveEntries);
        }
    }

    private static boolean blockStepTooFastForChunkBand(BlockPos prev, BlockPos now, boolean dimChanged) {
        if (dimChanged || prev == null) {
            return false;
        }
        int dx = Math.abs(now.getX() - prev.getX());
        int dy = Math.abs(now.getY() - prev.getY());
        int dz = Math.abs(now.getZ() - prev.getZ());
        int cheb = Math.max(dx, Math.max(dy, dz));
        return cheb > CHUNK_BAND_MAX_BLOCK_STEP;
    }

    private static void tickPlayer(ServerPlayer player, int r) {
        ServerLevel level = player.serverLevel();
        String dimId = level.dimension().location().toString();
        PlayerBandState pState = STATE.get(player.getUUID());
        if (pState == null) {
            return;
        }

        int pcx = player.chunkPosition().x;
        int pcz = player.chunkPosition().z;

        List<ChunkBandTintPayload.Entry> batch = new ArrayList<>();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                byte band = CivilMapTintLogic.computeTintForChunk(level, cx, cz, null);
                batch.add(new ChunkBandTintPayload.Entry(cx, cz, band));
                if (batch.size() >= ChunkBandTintPayload.MAX_ENTRIES_PER_PACKET) {
                    flushBatch(player, dimId, batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) {
            flushBatch(player, dimId, batch);
        }
    }

    private static void flushBatch(ServerPlayer player, String dimId, List<ChunkBandTintPayload.Entry> batch) {
        CivilPlatform.sendToPlayer(player, new ChunkBandTintPayload(dimId, List.copyOf(batch)));
        if (CivilMod.DEBUG) {
            dbgWavePackets++;
            dbgWaveEntries += batch.size();
        }
    }

    static final class PlayerBandState {
        BlockPos lastTickBlockPos;
        String lastMovementDim;
    }
}
