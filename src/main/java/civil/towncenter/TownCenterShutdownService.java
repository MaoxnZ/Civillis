package civil.towncenter;

import civil.CivilServices;
import civil.civilization.BaseScoreSourceRegistry;
import civil.civilization.TownCenterTracker;
import civil.civilization.TownCenterTracker.TownCenterEntry;
import civil.towncenter.network.TownCenterMenuViewers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Finalizes town center shutdown deadlines (deactivate + remove BSR).
 */
public final class TownCenterShutdownService {

    private TownCenterShutdownService() {}

    public static void onServerTick(MinecraftServer server) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        BaseScoreSourceRegistry registry = CivilServices.getBaseScoreSourceRegistry();
        if (tracker == null || !tracker.isInitialized()) return;

        long gameTime = server.overworld().getGameTime();
        boolean syncTick = gameTime % 20 == 0;
        for (var dimEntry : tracker.snapshotAllTownCenters()) {
            String dim = dimEntry.dim();
            TownCenterEntry e = dimEntry.entry();
            if (e.deactivateDeadlineTick() <= 0) continue;

            if (e.deactivateDeadlineTick() > gameTime) {
                if (syncTick) {
                    for (ServerLevel sl : server.getAllLevels()) {
                        if (sl.dimension().identifier().toString().equals(dim)) {
                            TownCenterMenuViewers.broadcastDataOnly(
                                    sl, new net.minecraft.core.BlockPos(e.x(), e.y(), e.z()));
                            break;
                        }
                    }
                }
                continue;
            }

            tracker.removeMaxLevelClaimIfPresent(dim, e);
            tracker.finalizeShutdown(dim, e.x(), e.y(), e.z());
            if (registry != null && registry.isInitialized()) {
                registry.remove(BaseScoreSourceRegistry.tcSourceKey(e.x(), e.y(), e.z()));
            }
            BlockPos lectern = new BlockPos(e.x(), e.y(), e.z());
            for (ServerLevel sl : server.getAllLevels()) {
                if (sl.dimension().identifier().toString().equals(dim)) {
                    TownCenterSounds.playShutdownFinalize(sl, lectern);
                    TownCenterMenuViewers.broadcast(sl, lectern);
                    break;
                }
            }
        }
    }
}
