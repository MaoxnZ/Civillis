package civil.respawn;

import civil.CivilPlatform;
import civil.CivilServices;
import civil.civilization.ServerClock;
import civil.civilization.UndyingAnchorTracker;
import civil.civilization.UndyingAnchorTracker.AnchorEntry;
import civil.civilization.scoring.CivilizationService;
import civil.config.CivilConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side: scans activated undying anchors every second, checks civilization score,
 * and broadcasts particle tier (CHARGE/READY) to all players in each dimension.
 * When {@link civil.config.CivilConfig#undyingAnchorEnabled} is false, sends empty
 * payload so clients clear particles (same as when anchors become invalid).
 */
public final class UndyingAnchorParticleManager {

    private static final int SCAN_INTERVAL_TICKS = 20;

    private static long lastScanTick = 0;

    private UndyingAnchorParticleManager() {}

    /**
     * Reset scan throttle. Call on world unload so the next world's first scan can run
     * (otherwise lastScanTick from the old world would block scans until new tick exceeds old lastScanTick).
     */
    public static void reset() {
        lastScanTick = 0;
    }

    /**
     * Call from server tick (END_SERVER_TICK). Runs once per second.
     * When feature is disabled, sends empty payload so clients clear particles.
     */
    public static void onServerTick(MinecraftServer server) {
        long tick = server.overworld().getGameTime();
        if (tick - lastScanTick < SCAN_INTERVAL_TICKS) return;
        lastScanTick = tick;

        if (!CivilConfig.undyingAnchorEnabled) {
            broadcastEmptyPayload(server);
            return;
        }

        UndyingAnchorTracker tracker = CivilServices.getUndyingAnchorTracker();
        if (tracker == null || !tracker.isInitialized()) return;

        CivilizationService civService = CivilServices.getCivilizationService();
        if (civService == null) return;

        double civRequired = CivilConfig.getUndyingAnchorCivRequired();
        long cooldownMs = CivilConfig.undyingAnchorGlobalCooldownSeconds * 1000L;
        long now = ServerClock.now();

        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().identifier().toString();
            List<ServerPlayer> playersInDim = server.getPlayerList().getPlayers().stream()
                    .filter(p -> p.level().dimension().identifier().toString().equals(dim))
                    .toList();
            if (playersInDim.isEmpty()) continue;

            List<AnchorEntry> activated = tracker.collectActivatedAnchors(dim);
            List<UndyingAnchorParticlePayload.Entry> entries = new ArrayList<>();
            for (AnchorEntry a : activated) {
                double score = civService.getCScoreAt(level, new BlockPos(a.x(), a.y(), a.z())).score();
                if (score + UndyingAnchorTracker.CIV_EPSILON < civRequired) continue;

                byte tier;
                if (a.lastUsedGlobal() != 0 && (now - a.lastUsedGlobal()) < cooldownMs) {
                    tier = UndyingAnchorParticlePayload.Entry.TIER_CHARGE;
                } else {
                    tier = UndyingAnchorParticlePayload.Entry.TIER_READY;
                }
                entries.add(new UndyingAnchorParticlePayload.Entry(a.x(), a.y(), a.z(), tier));
            }

            // Always send (even empty) so client clears particles when anchors become invalid
            UndyingAnchorParticlePayload payload = new UndyingAnchorParticlePayload(entries);
            for (ServerPlayer player : playersInDim) {
                CivilPlatform.sendToPlayer(player, payload);
            }
        }
    }

    private static void broadcastEmptyPayload(MinecraftServer server) {
        UndyingAnchorParticlePayload empty = new UndyingAnchorParticlePayload(List.of());
        for (ServerLevel level : server.getAllLevels()) {
            String dim = level.dimension().identifier().toString();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension().identifier().toString().equals(dim)) {
                    CivilPlatform.sendToPlayer(player, empty);
                }
            }
        }
    }
}
