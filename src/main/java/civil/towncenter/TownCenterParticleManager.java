package civil.towncenter;

import civil.CivilPlatform;
import civil.CivilServices;
import civil.civilization.TownCenterTracker;
import civil.civilization.TownCenterTracker.TownCenterEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server: periodically broadcasts gameplay-active town center particle tiers to clients.
 */
public final class TownCenterParticleManager {

    private static final int SCAN_INTERVAL_TICKS = 20;

    private static long lastScanTick = 0;

    private TownCenterParticleManager() {
    }

    public static void reset() {
        lastScanTick = 0;
    }

    public static void onServerTick(MinecraftServer server) {
        long tick = server.overworld().getGameTime();
        if (tick - lastScanTick < SCAN_INTERVAL_TICKS) {
            return;
        }
        lastScanTick = tick;

        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
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

            List<TownCenterParticlePayload.Entry> entries = new ArrayList<>();
            tracker.forEachGameplayActive(dim, tick, e -> {
                int cx = e.x() >> 4;
                int cz = e.z() >> 4;
                if (!level.hasChunk(cx, cz)) {
                    return;
                }
                byte tier = tierFor(e, tick);
                entries.add(new TownCenterParticlePayload.Entry(e.x(), e.y(), e.z(), tier));
            });

            TownCenterParticlePayload payload = new TownCenterParticlePayload(entries);
            for (ServerPlayer player : playersInDim) {
                CivilPlatform.sendToPlayer(player, payload);
            }
        }
    }

    private static byte tierFor(TownCenterEntry e, long gameTime) {
        if (e.hasShutdownDeadline(gameTime)) {
            return TownCenterParticlePayload.Entry.TIER_SHUTDOWN;
        }
        int level = e.level();
        if (level < 1) {
            return TownCenterParticlePayload.Entry.TIER_ACTIVE_1;
        }
        if (level > TownCenterParticlePayload.Entry.TIER_ACTIVE_5) {
            return TownCenterParticlePayload.Entry.TIER_ACTIVE_5;
        }
        return (byte) level;
    }
}
