package civil.towncenter;

import civil.CivilServices;
import civil.civilization.CivilRegionClassifier;
import civil.civilization.CivilRegionKind;
import civil.civilization.TownCenterTracker;
import civil.civilization.TownCenterTracker.AppliedEffect;
import civil.civilization.TownCenterTracker.TownCenterEntry;
import civil.civilization.VoxelChunkKey;
import civil.config.CivilConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies merged zone buffs from gameplay-active town centers in civilized regions.
 */
public final class TownCenterZoneEffectService {

    private static final ConcurrentHashMap<UUID, Long> NEXT_REFRESH = new ConcurrentHashMap<>();

    private TownCenterZoneEffectService() {}

    public static void onServerTick(MinecraftServer server) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) return;

        long tick = server.overworld().getGameTime();
        int interval = CivilConfig.townCenterZoneBuffRefreshTicks;
        int duration = CivilConfig.townCenterZoneBuffDurationTicks;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) continue;

            UUID id = player.getUUID();
            long next = NEXT_REFRESH.getOrDefault(id, 0L);
            if (tick < next) continue;
            NEXT_REFRESH.put(id, tick + interval);

            VoxelChunkKey vc = VoxelChunkKey.from(player.blockPosition());
            if (CivilRegionClassifier.classify(level, vc).kind() != CivilRegionKind.HIGH) continue;

            String dim = level.dimension().identifier().toString();
            long gameTime = level.getGameTime();
            List<Candidate> candidates = new ArrayList<>();
            for (TownCenterEntry tc : tracker.entriesCoveringVc(dim, vc)) {
                if (!tc.isGameplayActive(gameTime)) continue;
                if (!tc.hasBenefit(id)) continue;
                for (AppliedEffect fx : tc.appliedEffects()) {
                    candidates.add(Candidate.fromApplied(fx, duration));
                }
            }
            applyMerged(player, candidates);
        }
    }

    public static void clearPlayer(UUID id) {
        NEXT_REFRESH.remove(id);
    }

    static void applyMerged(ServerPlayer player, List<Candidate> candidates) {
        Map<Identifier, Candidate> merged = new HashMap<>();
        for (Candidate c : candidates) {
            Candidate prev = merged.get(c.effectId);
            if (prev == null || c.beats(prev)) {
                merged.put(c.effectId, c);
            }
        }
        for (Candidate c : merged.values()) {
            var effect = BuiltInRegistries.MOB_EFFECT.get(c.effectId);
            if (effect.isEmpty()) continue;
            player.addEffect(new MobEffectInstance(
                    effect.get(), c.duration(), c.amplifier(), c.ambient(), c.showParticles(), c.showIcon()));
        }
    }

    public record Candidate(
            Identifier effectId,
            int amplifier,
            int duration,
            boolean ambient,
            boolean showParticles,
            boolean showIcon) {

        public static Candidate fromApplied(AppliedEffect fx, int duration) {
            return new Candidate(
                    Identifier.parse(fx.effectId()),
                    fx.amplifier(),
                    duration,
                    fx.ambient(),
                    fx.showParticles(),
                    fx.showIcon());
        }

        boolean beats(Candidate other) {
            if (amplifier != other.amplifier) return amplifier > other.amplifier;
            return duration >= other.duration;
        }
    }
}
