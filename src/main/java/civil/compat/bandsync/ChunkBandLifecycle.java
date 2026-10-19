package civil.compat.bandsync;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server lifecycle entry points for chunk-band sync. Platform loaders call these from tick / join / logout hooks;
 * networking is wired per loader ({@code ChunkBandFabricLifecycle}, {@code ChunkBandForgeLifecycle},
 * {@code ChunkBandNeoForgeLifecycle}).
 */
public final class ChunkBandLifecycle {

    private ChunkBandLifecycle() {}

    public static void onServerTickEnd(MinecraftServer server) {
        ChunkBandTintSync.onServerTickEnd(server);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        ChunkBandTintSync.onPlayerLogout(player);
    }

    public static void onPlayerJoin(ServerPlayer player) {
        ChunkBandTintSync.sendSyncPolicyToPlayer(player);
    }
}
