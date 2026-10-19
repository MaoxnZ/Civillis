package civil.fabric;

import civil.compat.bandsync.ChunkBandLifecycle;
import civil.compat.bandsync.ChunkBandSubscribePayload;
import civil.compat.bandsync.ChunkBandTintSync;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/** Fabric server wiring for chunk-band sync (networking + connection lifecycle). */
public final class ChunkBandFabricLifecycle {

    private ChunkBandFabricLifecycle() {}

    /** C2S subscribe receiver (Fabric 1.20.x buf API). Call once from mod initializer. */
    public static void wireNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(ChunkBandSubscribePayload.ID, (server, player, handler, buf, responseSender) -> {
            ChunkBandSubscribePayload payload = ChunkBandSubscribePayload.read(buf);
            server.execute(() -> ChunkBandTintSync.applySubscribe(player, payload.subscribed()));
        });
    }

    /**
     * Join / disconnect hooks: {@code onJoin} runs first, then band policy; band logout runs before
     * {@code onCivilLogout}.
     */
    public static void wireConnectionEvents(Consumer<ServerPlayer> onJoin, Consumer<ServerPlayer> onCivilLogout) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            onJoin.accept(handler.player);
            ChunkBandLifecycle.onPlayerJoin(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ChunkBandLifecycle.onPlayerLogout(handler.player);
            onCivilLogout.accept(handler.player);
        });
    }
}
