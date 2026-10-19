package civil.fabric;

import civil.compat.bandsync.ChunkBandLifecycle;
import civil.compat.bandsync.ChunkBandSubscribePayload;
import civil.compat.bandsync.ChunkBandSyncPolicyPayload;
import civil.compat.bandsync.ChunkBandTintPayload;
import civil.compat.bandsync.ChunkBandTintSync;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/** Fabric server wiring for chunk-band sync (payload types, networking, connection lifecycle). */
public final class ChunkBandFabricLifecycle {

    private ChunkBandFabricLifecycle() {}

    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(ChunkBandTintPayload.ID, ChunkBandTintPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChunkBandSyncPolicyPayload.ID, ChunkBandSyncPolicyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ChunkBandSubscribePayload.ID, ChunkBandSubscribePayload.CODEC);
    }

    public static void wireNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(ChunkBandSubscribePayload.ID, (payload, context) ->
                context.server().execute(() -> ChunkBandTintSync.applySubscribe(context.player(), payload.subscribed())));
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
