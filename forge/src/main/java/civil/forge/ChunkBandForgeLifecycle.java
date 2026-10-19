package civil.forge;

import civil.compat.bandsync.ChunkBandLifecycle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Forge server lifecycle helpers for chunk-band sync (networking stays in {@code CivilModForge}). */
public final class ChunkBandForgeLifecycle {

    private ChunkBandForgeLifecycle() {}

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
