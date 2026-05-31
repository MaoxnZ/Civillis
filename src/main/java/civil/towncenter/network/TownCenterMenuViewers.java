package civil.towncenter.network;

import civil.CivilPlatform;
import civil.CivilServices;
import civil.civilization.TownCenterTracker;
import civil.civilization.TownCenterTracker.TownCenterEntry;
import civil.registry.TownCenterLevelRegistry;
import civil.registry.TownCenterLevelRegistry.ZoneBuffOffer;
import civil.towncenter.TownCenterLevelTable;
import civil.towncenter.TownCenterUpgradeService;
import civil.towncenter.gui.TownCenterMenu;
import civil.towncenter.network.TownCenterGuiSyncPayload.AppliedBuffPreview;
import civil.towncenter.network.TownCenterGuiSyncPayload.MemberEntry;
import civil.towncenter.network.TownCenterGuiSyncPayload.OfferPreview;
import civil.civilization.TownCenterTracker.AppliedEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks players viewing a town center GUI for profile broadcast. */
public final class TownCenterMenuViewers {

    private static final ConcurrentHashMap<Long, Set<UUID>> VIEWERS = new ConcurrentHashMap<>();

    private TownCenterMenuViewers() {}

    public static void register(String dim, BlockPos lectern, UUID playerId) {
        VIEWERS.computeIfAbsent(key(dim, lectern), k -> ConcurrentHashMap.newKeySet()).add(playerId);
    }

    public static void unregister(String dim, BlockPos lectern, UUID playerId) {
        Set<UUID> set = VIEWERS.get(key(dim, lectern));
        if (set != null) {
            set.remove(playerId);
            if (set.isEmpty()) VIEWERS.remove(key(dim, lectern));
        }
    }

    public static void closeAll(String dim, BlockPos lectern) {
        VIEWERS.remove(key(dim, lectern));
    }

    /** Syncs ContainerData only (countdown, activation, radii) without resending profile payload. */
    public static void broadcastDataOnly(ServerLevel level, BlockPos lectern) {
        String dim = level.dimension().identifier().toString();
        Set<UUID> set = VIEWERS.get(key(dim, lectern));
        if (set == null || set.isEmpty()) return;

        Iterator<UUID> it = set.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                it.remove();
                continue;
            }
            if (!(player.containerMenu instanceof TownCenterMenu menu)) {
                it.remove();
                continue;
            }
            if (!menu.getLecternPos().equals(lectern) || !menu.stillValid(player)) {
                it.remove();
                continue;
            }
            menu.syncFromTracker(player);
            menu.broadcastChanges();
        }
    }

    public static void broadcast(ServerLevel level, BlockPos lectern) {
        String dim = level.dimension().identifier().toString();
        Set<UUID> set = VIEWERS.get(key(dim, lectern));
        if (set == null || set.isEmpty()) return;

        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null) return;

        TownCenterEntry entry = tracker.getEntry(dim, lectern.getX(), lectern.getY(), lectern.getZ());
        if (entry == null) return;

        TownCenterGuiSyncPayload payload = buildPayload(entry);
        Iterator<UUID> it = set.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                it.remove();
                continue;
            }
            if (!(player.containerMenu instanceof TownCenterMenu menu)) {
                it.remove();
                continue;
            }
            if (!menu.getLecternPos().equals(lectern) || !menu.stillValid(player)) {
                it.remove();
                continue;
            }
            menu.syncFromTracker(player);
            menu.broadcastChanges();
            CivilPlatform.sendToPlayer(player, payload);
        }
    }

    public static void sendInitial(ServerPlayer player, TownCenterMenu menu) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null) return;
        String dim = player.level().dimension().identifier().toString();
        BlockPos lectern = menu.getLecternPos();
        TownCenterEntry entry = tracker.getEntry(dim, lectern.getX(), lectern.getY(), lectern.getZ());
        if (entry == null) return;
        register(dim, lectern, player.getUUID());
        CivilPlatform.sendToPlayer(player, buildPayload(entry));
    }

    private static TownCenterGuiSyncPayload buildPayload(TownCenterEntry entry) {
        int target = entry.level() < TownCenterLevelTable.maxLevel() ? entry.level() + 1 : 0;
        int pending = entry.levelEffectState().containsKey(target) ? 0 : target;
        var members = new java.util.ArrayList<MemberEntry>();
        if (entry.creatorUuid() != null) {
            members.add(new MemberEntry(entry.creatorUuid(), entry.creatorName()));
        }
        for (var m : entry.members()) {
            if (entry.creatorUuid() == null || !m.uuid().equals(entry.creatorUuid())) {
                members.add(new MemberEntry(m.uuid(), m.name()));
            }
        }
        List<OfferPreview> previews = List.of();
        if (pending > 0) {
            previews = TownCenterLevelRegistry.getStep(pending)
                    .map(step -> step.zoneBuffOffers().stream()
                            .map(offer -> toPreview(offer, entry))
                            .toList())
                    .orElse(List.of());
        }
        var appliedBuffs = new java.util.ArrayList<AppliedBuffPreview>();
        for (AppliedEffect fx : entry.appliedEffects()) {
            String translationKey = TownCenterLevelRegistry.getOffer(fx.zoneBuffOfferId())
                    .map(ZoneBuffOffer::translationKey)
                    .orElse(fx.effectId());
            appliedBuffs.add(new AppliedBuffPreview(fx.effectId(), fx.amplifier(), translationKey));
        }
        return new TownCenterGuiSyncPayload(
                entry.creatorName(),
                entry.displayName(),
                members,
                pending,
                previews,
                appliedBuffs);
    }


    private static OfferPreview toPreview(ZoneBuffOffer offer, TownCenterEntry entry) {
        String effectId = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                .getKey(offer.effect())
                .toString();
        int amplifier = TownCenterUpgradeService.resolveNextAmplifier(entry, effectId, offer.increment());
        return new OfferPreview(
                offer.paymentTier(),
                offer.translationKey(),
                effectId,
                amplifier);
    }

    private static long key(String dim, BlockPos pos) {
        return ((long) dim.hashCode() << 32) ^ pos.asLong();
    }
}
