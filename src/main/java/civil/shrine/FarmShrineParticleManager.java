package civil.shrine;

import civil.CivilPlatform;
import civil.CivilServices;
import civil.civilization.FarmShrineTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server: periodically broadcasts activated farm shrine positions for client ring particles.
 */
public final class FarmShrineParticleManager {

    private static final int SCAN_INTERVAL_TICKS = 20;

    private static long lastScanTick = 0;

    private FarmShrineParticleManager() {
    }

    /** Call on overworld unload so the next session is not throttled by an old tick. */
    public static void reset() {
        lastScanTick = 0;
    }

    /** Call from end-of server tick (same cadence as {@link civil.respawn.UndyingAnchorParticleManager}). */
    public static void onServerTick(MinecraftServer server) {
        long tick = server.overworld().getGameTime();
        if (tick - lastScanTick < SCAN_INTERVAL_TICKS) {
            return;
        }
        lastScanTick = tick;

        FarmShrineTracker tracker = CivilServices.getFarmShrineTracker();
        if (tracker == null || !tracker.isInitialized()) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().identifier().toString();
            List<ServerPlayer> playersInDim = server.getPlayerList().getPlayers().stream()
                    .filter(p -> p.level().dimension().identifier().toString().equals(dim))
                    .toList();
            if (playersInDim.isEmpty()) {
                continue;
            }

            List<FarmShrineParticlePayload.Entry> entries = new ArrayList<>();
            tracker.forEachActivatedShrine(dim, se -> {
                int cx = se.x() >> 4;
                int cz = se.z() >> 4;
                if (!level.hasChunk(cx, cz)) {
                    return;
                }
                entries.add(new FarmShrineParticlePayload.Entry(se.x(), se.y(), se.z()));
            });

            FarmShrineParticlePayload payload = new FarmShrineParticlePayload(entries);
            for (ServerPlayer player : playersInDim) {
                CivilPlatform.sendToPlayer(player, payload);
            }
        }
    }
}
