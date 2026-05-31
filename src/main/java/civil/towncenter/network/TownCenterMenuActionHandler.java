package civil.towncenter.network;

import civil.CivilServices;
import civil.civilization.TownCenterTracker;
import civil.civilization.TownCenterTracker.TownCenterEntry;
import civil.towncenter.TownCenterUpgradeService;
import civil.towncenter.gui.TownCenterGuiPage;
import civil.towncenter.gui.TownCenterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles {@link TownCenterC2SPayload} on the server (containerId -> lectern authority).
 */
public final class TownCenterMenuActionHandler {

    private static final int MAX_NAME_LEN = 32;

    private TownCenterMenuActionHandler() {}

    public static void handle(ServerPlayer player, TownCenterC2SPayload payload) {
        if (!(player.containerMenu instanceof TownCenterMenu menu)) return;
        if (menu.containerId != payload.containerId()) return;
        if (!menu.stillValid(player)) return;

        BlockPos lectern = menu.getLecternPos();
        String dim = player.level().dimension().identifier().toString();
        int x = lectern.getX();
        int y = lectern.getY();
        int z = lectern.getZ();

        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) return;

        TownCenterEntry entry = tracker.getEntry(dim, x, y, z);
        if (entry == null) return;

        boolean ok = switch (payload.action()) {
            case SET_PAGE -> {
                menu.setPage(TownCenterGuiPage.fromOrdinal(payload.intArg()));
                yield true;
            }
            case CONFIRM_UPGRADE -> menu.getCurrentPage() == TownCenterGuiPage.OPTIONS
                    && TownCenterUpgradeService.confirmUpgrade(
                    player,
                    (ServerLevel) player.level(),
                    lectern,
                    payload.intArg(),
                    payload.paymentTier(),
                    menu.getDonationStack(),
                    menu.getIngotStack());
            case SET_NAME -> handleSetName(tracker, dim, x, y, z, entry, player, payload.stringArg());
            case SET_OPEN_REGISTRATION -> handleOpenRegistration(tracker, dim, x, y, z, entry, player, payload.boolArg());
            case REGISTER_MEMBER -> handleRegister(tracker, dim, x, y, z, entry, player);
            case UNREGISTER_MEMBER -> handleUnregister(tracker, dim, x, y, z, entry, player);
            case KICK_MEMBER -> handleKick(tracker, dim, x, y, z, entry, player, payload.uuidArg());
        };

        if (ok) {
            menu.syncFromTracker(player);
            menu.broadcastChanges();
            if (payload.action() != TownCenterC2SPayload.Action.SET_PAGE) {
                TownCenterMenuViewers.broadcast((ServerLevel) player.level(), lectern);
            }
        }
    }

    private static boolean handleSetName(TownCenterTracker tracker, String dim, int x, int y, int z,
                                         TownCenterEntry entry, ServerPlayer player, String raw) {
        if (!entry.isCreator(player.getUUID())) return false;
        String name = clampName(raw);
        tracker.setDisplayName(dim, x, y, z, name);
        return true;
    }

    private static boolean handleOpenRegistration(TownCenterTracker tracker, String dim, int x, int y, int z,
                                                  TownCenterEntry entry, ServerPlayer player, boolean open) {
        if (!entry.isCreator(player.getUUID())) return false;
        tracker.setOpenRegistration(dim, x, y, z, open);
        return true;
    }

    private static boolean handleRegister(TownCenterTracker tracker, String dim, int x, int y, int z,
                                          TownCenterEntry entry, ServerPlayer player) {
        if (!entry.openRegistration()) return false;
        if (entry.isCreator(player.getUUID()) || entry.isMember(player.getUUID())) return false;
        tracker.addMember(dim, x, y, z, player.getUUID(), player.getName().getString());
        return true;
    }

    private static boolean handleUnregister(TownCenterTracker tracker, String dim, int x, int y, int z,
                                            TownCenterEntry entry, ServerPlayer player) {
        if (!entry.openRegistration()) return false;
        if (!entry.isMember(player.getUUID())) return false;
        tracker.removeMember(dim, x, y, z, player.getUUID());
        return true;
    }

    private static boolean handleKick(TownCenterTracker tracker, String dim, int x, int y, int z,
                                      TownCenterEntry entry, ServerPlayer player, java.util.UUID target) {
        if (!entry.isCreator(player.getUUID())) return false;
        if (target == null || entry.isCreator(target)) return false;
        if (!entry.isMember(target)) return false;
        tracker.removeMember(dim, x, y, z, target);
        return true;
    }

    private static String clampName(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_NAME_LEN) trimmed = trimmed.substring(0, MAX_NAME_LEN);
        return trimmed;
    }
}
