package civil.spawn;

import civil.CivilServices;
import civil.civilization.CScore;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Spawn policy:
 * - Reads civilization value (0~1) and monster head types in neighborhood;
 * - Any monster head → force allow spawn; matching type head → that type always allowed (equivalent to 2x spawn rate);
 * - No heads: LOW definitely spawns, MID probability spawns, HIGH definitely blocks.
 */
public final class SpawnPolicy {

    private SpawnPolicy() {
    }

    /**
     * Returns complete decision (whether to block + civilization value + branch), for Mixin logging and debugging.
     * entityType parameter used for "matching type head" judgment (e.g., zombie head → zombie always allowed).
     */
    public static SpawnDecision decide(ServerWorld world, BlockPos pos, EntityType<?> entityType) {
        CScore cScore = CivilServices.getCivilizationService().getCScoreAt(world, pos);
        double score = cScore.score();
        double thresholdLOW = 0.1;
        double thresholdMID = 0.3;

        List<EntityType<?>> headTypes = cScore.headTypes() != null ? cScore.headTypes() : List.of();

        if (cScore.hasHeadFor(entityType)) {
            return new SpawnDecision(false, score, "HEAD_MATCH", headTypes);
        }
        if (cScore.isForceAllow()) {
            return new SpawnDecision(false, score, "HEAD_ANY", headTypes);
        }

        if (score <= thresholdLOW) {
            return new SpawnDecision(false, score, SpawnDecision.BRANCH_LOW);
        }
        if (score > thresholdLOW && score < thresholdMID) {
            double t = (score - thresholdLOW) / (thresholdMID - thresholdLOW);
            double blockProbability = t;
            Random random = world.getRandom();
            boolean block = random.nextDouble() < blockProbability;
            return new SpawnDecision(block, score, SpawnDecision.BRANCH_MID);
        }
        return new SpawnDecision(true, score, SpawnDecision.BRANCH_HIGH);
    }

    /**
     * Whether to block hostile mob spawn at given position (compatible with old calls, does not distinguish entity types).
     */
    public static boolean shouldBlockMonsterSpawn(ServerWorld world, BlockPos pos) {
        return decide(world, pos, null).block();
    }
}
