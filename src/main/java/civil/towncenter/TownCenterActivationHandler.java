package civil.towncenter;

import civil.CivilServices;
import civil.civilization.BaseScoreSourceRegistry;
import civil.civilization.TownCenterStructureValidator;
import civil.civilization.TownCenterAabb;
import civil.civilization.TownCenterTracker;
import civil.civilization.TownCenterTracker.TownCenterEntry;
import civil.config.CivilConfig;
import civil.towncenter.network.TownCenterMenuViewers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Town center player interactions: right-click activate/deactivate and block protection queries.
 */
public final class TownCenterActivationHandler {

    private TownCenterActivationHandler() {}

    /**
     * Right-click activation: validates structure, spacing, and cap before consuming one emerald.
     * First activation also creates the tracker entry. Returns false with no side effects on failure.
     */
    public static boolean tryActivateFromInteract(ServerPlayer player, ServerLevel level, BlockPos lecternPos) {
        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (mainHand.isEmpty() || !mainHand.is(Items.EMERALD) || mainHand.getCount() < 1) {
            return false;
        }

        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        BaseScoreSourceRegistry registry = CivilServices.getBaseScoreSourceRegistry();
        if (tracker == null || registry == null || !tracker.isInitialized() || !registry.isInitialized()) {
            return failActivate(level, lecternPos);
        }

        if (!TownCenterStructureValidator.isEmeraldBelow(level, lecternPos)) return failActivate(level, lecternPos);
        if (!TownCenterStructureValidator.lecternHasWrittenBook(level, lecternPos)) return failActivate(level, lecternPos);

        String dim = level.dimension().identifier().toString();
        int x = lecternPos.getX();
        int y = lecternPos.getY();
        int z = lecternPos.getZ();

        TownCenterEntry entry = tracker.getEntry(dim, x, y, z);
        if (entry != null && entry.activated()) {
            return failActivate(level, lecternPos);
        }

        boolean firstActivation = entry == null;
        if (firstActivation) {
            if (tracker.countAll() >= CivilConfig.townCenterMaxCount) return failActivate(level, lecternPos);
            if (!tracker.passesSpacing(dim, x, y, z)) return failActivate(level, lecternPos);
        }

        mainHand.shrink(1);

        if (firstActivation) {
            if (!tracker.add(dim, x, y, z, 1, true, player.getUUID(), player.getName().getString())) {
                return failActivate(level, lecternPos);
            }
        } else {
            tracker.setActivated(dim, x, y, z, true);
            tracker.setShutdownDeadline(dim, x, y, z, 0L);
            tracker.refreshMaxLevelClaim(dim, entry, tracker.getEntry(dim, x, y, z), level.getGameTime());
        }

        applyBaseScoreSource(level, lecternPos, firstActivation ? 1 : entry.level());
        if (firstActivation) {
            TownCenterSounds.playFirstEmeraldActivate(level, lecternPos);
        } else {
            TownCenterSounds.playAscendChimeDouble(level, lecternPos);
        }
        return true;
    }

    /** Begin or cancel 60s shutdown countdown; does not remove BSR until deadline. */
    public static boolean tryDeactivate(ServerLevel level, BlockPos lecternPos) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) return false;

        String dim = level.dimension().identifier().toString();
        int x = lecternPos.getX();
        int y = lecternPos.getY();
        int z = lecternPos.getZ();

        TownCenterEntry entry = tracker.getEntry(dim, x, y, z);
        if (entry == null || !entry.activated()) return false;

        long gameTime = level.getGameTime();
        boolean hadDeadline = entry.hasShutdownDeadline(gameTime);
        if (hadDeadline) {
            tracker.setShutdownDeadline(dim, x, y, z, 0L);
            TownCenterSounds.playAscendChimeDouble(level, lecternPos);
        } else {
            tracker.setShutdownDeadline(dim, x, y, z, gameTime + CivilConfig.townCenterShutdownTicks);
            TownCenterSounds.playShutdownStartChimeTriple(level, lecternPos);
        }
        TownCenterMenuViewers.broadcast(level, lecternPos);
        return true;
    }

    public static boolean tryReactivate(ServerLevel level, BlockPos lecternPos) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        BaseScoreSourceRegistry registry = CivilServices.getBaseScoreSourceRegistry();
        if (tracker == null || registry == null || !tracker.isInitialized() || !registry.isInitialized()) {
            return false;
        }
        if (!TownCenterStructureValidator.isEmeraldBelow(level, lecternPos)) return false;
        if (!TownCenterStructureValidator.lecternHasWrittenBook(level, lecternPos)) return false;

        String dim = level.dimension().identifier().toString();
        int x = lecternPos.getX();
        int y = lecternPos.getY();
        int z = lecternPos.getZ();

        TownCenterEntry entry = tracker.getEntry(dim, x, y, z);
        if (entry == null) return false;
        if (entry.activated() && !entry.hasShutdownDeadline(level.getGameTime())) return false;

        tracker.setActivated(dim, x, y, z, true);
        tracker.setShutdownDeadline(dim, x, y, z, 0L);
        tracker.refreshMaxLevelClaim(dim, entry, tracker.getEntry(dim, x, y, z), level.getGameTime());
        applyBaseScoreSource(level, lecternPos, entry.level());
        TownCenterSounds.playAscendChimeDouble(level, lecternPos);
        TownCenterMenuViewers.broadcast(level, lecternPos);
        return true;
    }

    public static void onStructureRemoved(ServerLevel level, String dim, int x, int y, int z) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        BaseScoreSourceRegistry registry = CivilServices.getBaseScoreSourceRegistry();
        if (tracker == null || !tracker.isInitialized()) return;

        TownCenterEntry entry = tracker.getEntry(dim, x, y, z);
        if (entry == null) return;
        if (entry.activated() && registry != null && registry.isInitialized()) {
            registry.remove(BaseScoreSourceRegistry.tcSourceKey(x, y, z));
        }
        tracker.removeMaxLevelClaimIfPresent(dim, entry);
        tracker.remove(dim, x, y, z);
        TownCenterMenuViewers.closeAll(dim, new BlockPos(x, y, z));
        TownCenterSounds.playBeaconDeactivate(level, new BlockPos(x, y, z));
    }

    private static boolean failActivate(ServerLevel level, BlockPos lecternPos) {
        TownCenterSounds.playActivationFail(level, lecternPos);
        return false;
    }

    public static boolean isProtectedBlock(String dim, BlockPos pos) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) return false;

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        TownCenterEntry atLectern = tracker.getEntry(dim, x, y, z);
        if (atLectern != null && atLectern.activated()) return true;

        TownCenterEntry above = tracker.getEntry(dim, x, y + 1, z);
        return above != null && above.activated();
    }

    public static boolean shouldOpenGui(ServerLevel level, BlockPos lecternPos) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) return false;
        if (!TownCenterStructureValidator.lecternHasWrittenBook(level, lecternPos)) return false;

        String dim = level.dimension().identifier().toString();
        TownCenterEntry entry = tracker.getEntry(dim, lecternPos.getX(), lecternPos.getY(), lecternPos.getZ());
        return entry != null && entry.isGameplayActive(level.getGameTime());
    }

    public static boolean shouldOpenRegisteredGui(ServerLevel level, BlockPos lecternPos) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) return false;
        if (!TownCenterStructureValidator.isEmeraldBelow(level, lecternPos)) return false;
        if (!TownCenterStructureValidator.lecternHasWrittenBook(level, lecternPos)) return false;

        String dim = level.dimension().identifier().toString();
        TownCenterEntry entry = tracker.getEntry(dim, lecternPos.getX(), lecternPos.getY(), lecternPos.getZ());
        return entry != null;
    }

    public static boolean isRegisteredTownCenterLectern(ServerLevel level, BlockPos lecternPos) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) return false;
        if (!TownCenterStructureValidator.isEmeraldBelow(level, lecternPos)) return false;

        String dim = level.dimension().identifier().toString();
        TownCenterEntry entry = tracker.getEntry(dim, lecternPos.getX(), lecternPos.getY(), lecternPos.getZ());
        return entry != null;
    }

    private static void applyBaseScoreSource(ServerLevel world, BlockPos lecternPos, int tcLevel) {
        BaseScoreSourceRegistry registry = CivilServices.getBaseScoreSourceRegistry();
        if (registry == null) return;

        String dim = world.dimension().identifier().toString();
        int x = lecternPos.getX();
        int y = lecternPos.getY();
        int z = lecternPos.getZ();
        TownCenterAabb aabb = TownCenterAabb.atLectern(lecternPos, tcLevel);
        registry.add(new BaseScoreSourceRegistry.SourceEntry(
                BaseScoreSourceRegistry.tcSourceKey(x, y, z),
                dim,
                aabb.minVc(),
                aabb.maxVc(),
                TownCenterLevelTable.rawValue(tcLevel),
                BaseScoreSourceRegistry.TYPE_TC));
    }
}
