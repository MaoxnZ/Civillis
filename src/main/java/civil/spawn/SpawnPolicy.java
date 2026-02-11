package civil.spawn;

import civil.CivilMod;
import civil.CivilServices;
import civil.civilization.CScore;
import civil.civilization.MobHeadRegistry;
import civil.config.CivilConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Fusion Architecture spawn policy — head-first decision flow.
 *
 * <p>Decision priority:
 * <ol>
 *   <li>HEAD_MATCH: current mob matches neighborhood head → always allow</li>
 *   <li>HEAD_ANY: any monster head in neighborhood → allow + may convert</li>
 *   <li>HEAD_SUPPRESS: heads exist in wider area but spawn is far → probabilistic block</li>
 *   <li>LOW/MID/HIGH: civilization score thresholds (result shard O(1) query)</li>
 * </ol>
 *
 * <p>Head logic queries MobHeadRegistry directly (decoupled from civilization scoring).
 * Civilization score queries the ResultCache via ScalableCivilizationService (O(1)).
 */
public final class SpawnPolicy {

    private SpawnPolicy() {
    }

    /**
     * Returns complete decision (whether to block + civilization value + branch).
     */
    public static SpawnDecision decide(ServerWorld world, BlockPos pos, EntityType<?> entityType) {

        // ===== Stage 1: Head Detection (single O(N) pass via MobHeadRegistry) =====
        MobHeadRegistry registry = CivilServices.getMobHeadRegistry();
        if (registry != null && registry.isInitialized()) {
            String dim = world.getRegistryKey().toString();

            // Combined query: nearby types (HEAD_MATCH/HEAD_ANY) + proximity (HEAD_SUPPRESS)
            MobHeadRegistry.HeadQuery hq = registry.queryHeads(
                    dim, pos,
                    CivilConfig.headRangeX,
                    CivilConfig.headRangeZ,
                    CivilConfig.headRangeY);

            // ① HEAD_MATCH / HEAD_ANY
            if (hq.hasNearbyHeads()) {
                List<EntityType<?>> headTypes = hq.nearbyTypes();
                if (entityType != null && headTypes.contains(entityType)) {
                    return new SpawnDecision(false, 0, "HEAD_MATCH", headTypes);
                }
                return new SpawnDecision(false, 0, "HEAD_ANY", headTypes);
            }

            // ② HEAD_SUPPRESS: heads exist in wider area but spawn is far
            if (CivilConfig.headAttractEnabled && hq.proximity().hasHeads()) {
                SpawnDecision suppressDecision = checkHeadSuppression(world, pos, hq.proximity());
                if (suppressDecision != null) {
                    return suppressDecision;
                }
            }
        }

        // ===== Stage 2: Civilization Score (Result Shard O(1), only if no heads) =====
        CScore cScore = CivilServices.getCivilizationService().getCScoreAt(world, pos);
        double score = cScore.score();
        double thresholdLow = CivilConfig.spawnThresholdLow;
        double thresholdMid = CivilConfig.spawnThresholdMid;

        // ④ Civilization score thresholds
        if (score <= thresholdLow) {
            return new SpawnDecision(false, score, SpawnDecision.BRANCH_LOW);
        }
        if (score > thresholdLow && score < thresholdMid) {
            double t = (score - thresholdLow) / (thresholdMid - thresholdLow);
            double blockProbability = t;
            Random random = world.getRandom();
            boolean block = random.nextDouble() < blockProbability;
            return new SpawnDecision(block, score, SpawnDecision.BRANCH_MID);
        }
        return new SpawnDecision(true, score, SpawnDecision.BRANCH_HIGH);
    }

    /**
     * Head suppression check using pre-computed proximity from the combined query.
     * No additional registry traversal needed.
     */
    private static SpawnDecision checkHeadSuppression(ServerWorld world, BlockPos pos,
                                                       MobHeadRegistry.HeadProximity proximity) {
        double nearThreshold = CivilConfig.headAttractNearBlocks;
        double maxRadius = CivilConfig.headAttractMaxRadius;

        // Outside max horizontal radius: attraction has no effect
        if (proximity.nearestDistXZ() > maxRadius) return null;

        // Inside near threshold (3D): close to a head, no suppression needed
        if (proximity.nearestDist3D() <= nearThreshold) return null;

        // Suppression curve uses full 3D distance
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
