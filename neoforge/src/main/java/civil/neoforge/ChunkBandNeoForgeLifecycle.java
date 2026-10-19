package civil.neoforge;

import civil.compat.bandsync.ChunkBandLifecycle;
import civil.compat.bandsync.ChunkBandSubscribePayload;
import civil.compat.bandsync.ChunkBandSyncPolicyPayload;
import civil.compat.bandsync.ChunkBandTintPayload;
import civil.compat.bandsync.ChunkBandTintSync;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** NeoForge server wiring for chunk-band sync (payload handlers + lifecycle helpers). */
public final class ChunkBandNeoForgeLifecycle {

    private ChunkBandNeoForgeLifecycle() {}

    public static void registerPayloads(PayloadRegistrar registrar) {
        registrar.playToClient(ChunkBandTintPayload.ID, ChunkBandTintPayload.CODEC,
                NeoForgeClientPayloadHandler::handleChunkBandTint);
        registrar.playToClient(ChunkBandSyncPolicyPayload.ID, ChunkBandSyncPolicyPayload.CODEC,
                NeoForgeClientPayloadHandler::handleChunkBandSyncPolicy);
        registrar.playToServer(ChunkBandSubscribePayload.ID, ChunkBandSubscribePayload.CODEC,
                (payload, context) -> ChunkBandTintSync.applySubscribe(
                        (ServerPlayer) context.player(), payload.subscribed()));
    }

    public static void onServerTickEnd(MinecraftServer server) {
        ChunkBandLifecycle.onServerTickEnd(server);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        ChunkBandLifecycle.onPlayerJoin(player);
    }

    /** Call before {@link civil.CivilMod#onPlayerLogout}. */
    public static void onPlayerLogout(ServerPlayer player) {
        ChunkBandLifecycle.onPlayerLogout(player);
    }
}
