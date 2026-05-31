package civil.towncenter;



import civil.civilization.TownCenterTracker.TownCenterEntry;

import civil.config.CivilConfig;

import net.minecraft.server.level.ServerPlayer;



import java.util.ArrayList;

import java.util.List;



/** Immediate zone buff loot when a non-member destroys a town center with applied effects. */

public final class TownCenterSpoilService {



    private TownCenterSpoilService() {}



    public static void onTcRemoved(TownCenterEntry snapshot, ServerPlayer breaker, long gameTime) {

        if (breaker == null) return;

        if (snapshot.appliedEffects().isEmpty()) return;

        if (snapshot.hasBenefit(breaker.getUUID())) return;



        int spoilDuration = CivilConfig.townCenterSpoilDurationTicks;

        List<TownCenterZoneEffectService.Candidate> candidates = new ArrayList<>();

        for (var fx : snapshot.appliedEffects()) {

            candidates.add(TownCenterZoneEffectService.Candidate.fromApplied(fx, spoilDuration));

        }

        TownCenterZoneEffectService.applyMerged(breaker, candidates);

    }

}


