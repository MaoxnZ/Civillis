package civil.towncenter;

import civil.CivilServices;
import civil.civilization.BaseScoreSourceRegistry;
import civil.civilization.TownCenterAabb;
import civil.civilization.TownCenterTracker;
import civil.civilization.TownCenterTracker.AppliedEffect;
import civil.civilization.TownCenterTracker.LevelEffectChoice;
import civil.civilization.TownCenterTracker.TownCenterEntry;
import civil.registry.TownCenterLevelRegistry;
import civil.registry.TownCenterLevelRegistry.PaymentTier;
import civil.registry.TownCenterLevelRegistry.ZoneBuffOffer;
import civil.towncenter.network.TownCenterMenuViewers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * Confirms town center upgrade from Options panel (emerald blocks + optional ingot tier).
 */
public final class TownCenterUpgradeService {

    private TownCenterUpgradeService() {}

    public static boolean confirmUpgrade(
            ServerPlayer player,
            ServerLevel level,
            BlockPos lecternPos,
            int targetLevel,
            PaymentTier paymentTier,
            ItemStack donation,
            ItemStack ingotSlot) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        BaseScoreSourceRegistry registry = CivilServices.getBaseScoreSourceRegistry();
        if (tracker == null || registry == null || !tracker.isInitialized() || !registry.isInitialized()) {
            return false;
        }

        String dim = level.dimension().identifier().toString();
        int x = lecternPos.getX();
        int y = lecternPos.getY();
        int z = lecternPos.getZ();

        TownCenterEntry entry = tracker.getEntry(dim, x, y, z);
        if (entry == null) return false;
        long gameTime = level.getGameTime();
        if (!entry.isGameplayActive(gameTime)) return false;
        if (!entry.hasBenefit(player.getUUID())) return false;
        if (entry.level() >= TownCenterLevelTable.maxLevel()) return false;
        if (entry.level() + 1 != targetLevel) return false;
        if (entry.levelEffectState().containsKey(targetLevel)) return false;

        Optional<TownCenterLevelRegistry.LevelStep> stepOpt = TownCenterLevelRegistry.getStep(targetLevel);
        if (stepOpt.isEmpty()) return false;
        TownCenterLevelRegistry.LevelStep step = stepOpt.get();
        int cost = step.upgrade().emeraldBlockCost();
        if (cost <= 0) return false;
        if (donation.isEmpty() || !donation.is(Items.EMERALD_BLOCK) || donation.getCount() < cost) {
            return false;
        }

        ZoneBuffOffer chosenOffer = null;
        if (paymentTier != PaymentTier.NONE) {
            for (ZoneBuffOffer offer : step.zoneBuffOffers()) {
                if (offer.paymentTier() == paymentTier) {
                    chosenOffer = offer;
                    break;
                }
            }
            if (chosenOffer == null) return false;
            if (!ingotMatches(ingotSlot, paymentTier)) return false;
        }

        donation.shrink(cost);
        if (chosenOffer != null && !ingotSlot.isEmpty()) {
            ingotSlot.shrink(1);
        }

        int nextAmplifier = 0;
        if (chosenOffer != null) {
            nextAmplifier = resolveNextAmplifier(entry, effectIdString(chosenOffer), chosenOffer.increment());
        }

        tracker.setLevel(dim, x, y, z, targetLevel);
        TownCenterEntry upgradedEntry = tracker.getEntry(dim, x, y, z);
        tracker.refreshMaxLevelClaim(dim, entry, upgradedEntry, gameTime);
        if (chosenOffer != null) {
            tracker.setLevelEffectChoice(dim, x, y, z, targetLevel, LevelEffectChoice.APPLIED);
            tracker.addAppliedEffect(dim, x, y, z, new AppliedEffect(
                    chosenOffer.id(),
                    effectIdString(chosenOffer),
                    nextAmplifier,
                    targetLevel,
                    chosenOffer.ambient(),
                    chosenOffer.showParticles(),
                    chosenOffer.showIcon()));
        } else {
            tracker.setLevelEffectChoice(dim, x, y, z, targetLevel, LevelEffectChoice.SKIPPED);
        }

        TownCenterAabb oldAabb = TownCenterAabb.atLectern(lecternPos, entry.level());
        TownCenterAabb newAabb = TownCenterAabb.atLectern(lecternPos, targetLevel);
        TownCenterAabb union = TownCenterAabb.union(oldAabb, newAabb);
        String sourceKey = BaseScoreSourceRegistry.tcSourceKey(x, y, z);
        registry.update(sourceKey, dim, union.minVc(), union.maxVc(), step.upgrade().baseScoreRaw());

        TownCenterSounds.playUpgradeSuccess(level, lecternPos);
        TownCenterMenuViewers.broadcast(level, lecternPos);
        return true;
    }

    /** Next stored amplifier for {@code effectId} after applying an offer with {@code increment}. */
    public static int resolveNextAmplifier(TownCenterEntry entry, String effectId, int increment) {
        int max = -1;
        for (AppliedEffect fx : entry.appliedEffects()) {
            if (effectId.equals(fx.effectId())) {
                max = Math.max(max, fx.amplifier());
            }
        }
        int next = max + increment;
        if (next < 0) return 0;
        return Math.min(next, 127);
    }

    private static String effectIdString(ZoneBuffOffer offer) {
        return net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(offer.effect()).toString();
    }

    private static boolean ingotMatches(ItemStack stack, PaymentTier tier) {
        if (stack.isEmpty()) return false;
        return switch (tier) {
            case IRON -> stack.is(Items.IRON_INGOT) && stack.getCount() >= 1;
            case GOLD -> stack.is(Items.GOLD_INGOT) && stack.getCount() >= 1;
            case NETHERITE -> stack.is(Items.NETHERITE_INGOT) && stack.getCount() >= 1;
            case NONE -> true;
        };
    }
}
