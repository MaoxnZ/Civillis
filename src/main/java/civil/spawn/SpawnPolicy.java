package civil.spawn;

import civil.CivilMod;
import civil.CivilServices;
import civil.civilization.CScore;
import civil.civilization.HeadTracker;
import civil.config.CivilConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * Fusion Architecture spawn policy — head-first decision flow.
 *
 * <p>Decision priority:
 * <ol>
 *   <li>HEAD_NEARBY: enabled heads within VC box → bypass civilization suppression;
 *       conversion handled downstream by Mixin (3+ heads threshold).</li>
 *   <li>HEAD_SUPPRESS: enabled heads exist in wider area but spawn is far → probabilistic block</li>
 *   <li>LOW/MID/HIGH: civilization score thresholds (result shard O(1) query)</li>
 * </ol>
 *
 * <p>Head logic queries HeadTracker directly (decoupled from civilization scoring).
 * Civilization score queries the ResultCache via ScalableCivilizationService (O(1)).
 */
public final class SpawnPolicy {

    private SpawnPolicy() {
    }

    /**
     * Returns complete decision (whether to block + civilization value + branch).
     */
    public static SpawnDecision decide(ServerWorld world, BlockPos pos, EntityType<?> entityType) {

        // ===== Stage 1: Head Detection (single O(N) pass via HeadTracker) =====
        HeadTracker tracker = CivilServices.getHeadTracker();
        if (tracker != null && tracker.isInitialized()) {
            String dim = world.getRegistryKey().toString();

            HeadTracker.HeadQuery hq = tracker.queryHeads(
                    dim, pos,
                    CivilConfig.headRangeX,
                    CivilConfig.headRangeZ,
                    CivilConfig.headRangeY);

            // ① HEAD_NEARBY: any enabled heads in VC box → allow spawn (bypass civilization)
            if (hq.hasNearbyHeads()) {
                return new SpawnDecision(false, 0, SpawnDecision.BRANCH_HEAD_NEARBY,
                        hq.nearbyHeadCount(), hq.convertPool());
            }

            // ② HEAD_SUPPRESS: enabled heads exist in wider area but spawn is far
            if (CivilConfig.headAttractEnabled && hq.proximity().hasHeads()) {
                SpawnDecision suppressDecision = checkHeadSuppression(world, pos, hq.proximity());
                if (suppressDecision != null) {
                    return suppressDecision;
                }
            }
        }

        // ===== Stage 2: Civilization Score (Result Shard O(1), only if no nearby heads) =====
        CScore cScore = CivilServices.getCivilizationService().getCScoreAt(world, pos);
        double score = cScore.score();
        double thresholdLow = CivilConfig.spawnThresholdLow;
        double thresholdMid = CivilConfig.spawnThresholdMid;

        if (score <= thresholdLow) {
            return new SpawnDecision(false, score, SpawnDecision.BRANCH_LOW);
        }
        if (score > thresholdLow && score < thresholdMid) {
            double t = (score - thresholdLow) / (thresholdMid - thresholdLow);
            Random random = world.getRandom();
            boolean block = random.nextDouble() < t;
            return new SpawnDecision(block, score, SpawnDecision.BRANCH_MID);
        }
        return new SpawnDecision(true, score, SpawnDecision.BRANCH_HIGH);
    }

    /**
     * Head suppression check using pre-computed proximity from the combined query.
     * No additional registry traversal needed.
     */
    private static SpawnDecision checkHeadSuppression(ServerWorld world, BlockPos pos,
                                                       HeadTracker.HeadProximity proximity) {
        double nearThreshold = CivilConfig.headAttractNearBlocks;
        double maxRadius = CivilConfig.headAttractMaxRadius;

        if (proximity.nearestDistXZ() > maxRadius) return null;
        if (proximity.nearestDist3D() <= nearThreshold) return null;

        double d = proximity.nearestDist3D() - nearThreshold;
        double lambda = CivilConfig.headAttractLambda * (1.0 + Math.log1p(proximity.totalCount()));
        double suppressChance = 1.0 - Math.exp(-lambda * d / 16.0);

        if (world.getRandom().nextDouble() < suppressChance) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info(
                        "[civil] head_attract block pos=({}, {}, {}) dist3D={} distXZ={} chance={} heads={}",
                        pos.getX(), pos.getY(), pos.getZ(),
                        String.format("%.1f", proximity.nearestDist3D()),
                        String.format("%.1f", proximity.nearestDistXZ()),
                        String.format("%.3f", suppressChance),
                        proximity.totalCount());
            }
            return new SpawnDecision(true, 0, SpawnDecision.BRANCH_HEAD_SUPPRESS);
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info(
                    "[civil] head_attract pass pos=({}, {}, {}) dist3D={} distXZ={} chance={} heads={}",
                    pos.getX(), pos.getY(), pos.getZ(),
                    String.format("%.1f", proximity.nearestDist3D()),
                    String.format("%.1f", proximity.nearestDistXZ()),
                    String.format("%.3f", suppressChance),
                    proximity.totalCount());
        }
        return null;
    }

    /**
     * Whether to block hostile mob spawn at given position.
     */
    public static boolean shouldBlockMonsterSpawn(ServerWorld world, BlockPos pos) {
        return decide(world, pos, null).block();
    }
}
