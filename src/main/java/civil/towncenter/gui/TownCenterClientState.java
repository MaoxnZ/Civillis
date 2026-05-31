package civil.towncenter.gui;

import civil.registry.TownCenterLevelRegistry.PaymentTier;
import civil.towncenter.network.TownCenterGuiSyncPayload;
import civil.towncenter.network.TownCenterGuiSyncPayload.AppliedBuffPreview;
import civil.towncenter.network.TownCenterGuiSyncPayload.MemberEntry;
import civil.towncenter.network.TownCenterGuiSyncPayload.OfferPreview;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client-side mirror of town center profile fields from S2C sync. */
public final class TownCenterClientState {

    private static String displayName = "";
    private static final List<MemberEntry> members = new ArrayList<>();
    private static int pendingTargetLevel;
    private static final List<OfferPreview> offerPreviews = new ArrayList<>();
    private static final List<AppliedBuffPreview> appliedBuffs = new ArrayList<>();

    private TownCenterClientState() {}

    public static void apply(TownCenterGuiSyncPayload payload) {
        displayName = payload.displayName();
        members.clear();
        members.addAll(payload.members());
        pendingTargetLevel = payload.pendingTargetLevel();
        offerPreviews.clear();
        offerPreviews.addAll(payload.offerPreviews());
        appliedBuffs.clear();
        appliedBuffs.addAll(payload.appliedBuffs());
    }

    public static void clear() {
        displayName = "";
        members.clear();
        pendingTargetLevel = 0;
        offerPreviews.clear();
        appliedBuffs.clear();
    }

    public static String displayName() {
        return displayName;
    }

    public static List<MemberEntry> members() {
        return List.copyOf(members);
    }

    public static int pendingTargetLevel() {
        return pendingTargetLevel;
    }

    public static List<OfferPreview> offerPreviews() {
        return List.copyOf(offerPreviews);
    }

    public static List<Component> mergedAppliedBuffLines() {
        Map<String, AppliedBuffPreview> bestByEffect = new HashMap<>();
        for (AppliedBuffPreview preview : appliedBuffs) {
            AppliedBuffPreview prev = bestByEffect.get(preview.effectId());
            if (prev == null || preview.amplifier() > prev.amplifier()) {
                bestByEffect.put(preview.effectId(), preview);
            }
        }
        List<Component> lines = new ArrayList<>(bestByEffect.size());
        for (AppliedBuffPreview preview : bestByEffect.values()) {
            lines.add(TownCenterZoneBuffText.line(preview.translationKey(), preview.amplifier()));
        }
        lines.sort(Comparator.comparing(c -> c.getString()));
        return lines;
    }

    public static Component zoneBuffForIngot(ItemStack ingot) {
        PaymentTier tier = paymentTierForIngot(ingot);
        if (tier == PaymentTier.NONE) {
            return null;
        }
        for (OfferPreview preview : offerPreviews) {
            if (preview.paymentTier() == tier) {
                return TownCenterZoneBuffText.line(preview.translationKey(), preview.amplifier());
            }
        }
        return null;
    }

    public static PaymentTier paymentTierForIngot(ItemStack ingot) {
        if (ingot.isEmpty()) return PaymentTier.NONE;
        if (ingot.is(Items.IRON_INGOT)) return PaymentTier.IRON;
        if (ingot.is(Items.GOLD_INGOT)) return PaymentTier.GOLD;
        if (ingot.is(Items.NETHERITE_INGOT)) return PaymentTier.NETHERITE;
        return PaymentTier.NONE;
    }
}

