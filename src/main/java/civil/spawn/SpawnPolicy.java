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
 * Spawn policy:
 * <ol>
 *   <li>HEAD_MATCH: current mob matches neighborhood head → always allow</li>
 *   <li>HEAD_ANY: any monster head in neighborhood → allow + may convert</li>
 *   <li>HEAD_SUPPRESS: heads exist in wider area but spawn is far from them → probabilistic block
 *       (head attraction system — redirects spawns toward totem locations)</li>
 *   <li>LOW/MID/HIGH: civilization score thresholds (existing behavior)</li>
 * </ol>
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
        double thresholdLow = CivilConfig.spawnThresholdLow;
        double thresholdMid = CivilConfig.spawnThresholdMid;

        List<EntityType<?>> headTypes = cScore.headTypes() != null ? cScore.headTypes() : List.of();

        // ① HEAD_MATCH: current mob type matches a head in the neighborhood — always allow
        if (cScore.hasHeadFor(entityType)) {
            return new SpawnDecision(false, score, "HEAD_MATCH", headTypes);
        }
        // ② HEAD_ANY: any monster head in 3×3×1 neighborhood — allow (may convert in mixin)
        if (cScore.isForceAllow()) {
            return new SpawnDecision(false, score, "HEAD_ANY", headTypes);
        }

        // ③ HEAD_SUPPRESS: heads exist in wider area but spawn is far — probabilistic block
        if (CivilConfig.headAttractEnabled) {
            SpawnDecision suppressDecision = checkHeadAttraction(world, pos, score);
            if (suppressDecision != null) {
                return suppressDecision;
            }
        }

        // ④ Civilization score thresholds (existing behavior)
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
     * Head attraction suppression check.
     *
     * <p>If mob heads exist in the dimension and the spawn point is far from
     * all of them in 3D space, returns a blocking decision with probability
     * determined by distance and total head count.
     *
     * @return blocking SpawnDecision if suppressed, or null to continue normal flow
     */
    private static SpawnDecision checkHeadAttraction(ServerWorld world, BlockPos pos, double score) {
        MobHeadRegistry registry = CivilServices.getMobHeadRegistry();
        if (registry == null || !registry.isInitialized()) return null;

        String dim = world.getRegistryKey().toString();
        MobHeadRegistry.HeadProximity proximity = registry.queryNearest(
                dim, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        if (!proximity.hasHeads()) return null;

        double nearThreshold = CivilConfig.headAttractNearBlocks;
        double maxRadius = CivilConfig.headAttractMaxRadius;

        // Outside max horizontal radius: attraction has no effect, fall through to normal flow.
        // Uses XZ distance — players move horizontally, vertical caves shouldn't extend the range.
        if (proximity.nearestDistXZ() > maxRadius) return null;

        // Inside near threshold (3D): close to a head, no suppression needed
        if (proximity.nearestDist3D() <= nearThreshold) return null;

        // Suppression curve uses full 3D distance for accuracy
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
            return new SpawnDecision(true, score, SpawnDecision.BRANCH_HEAD_SUPPRESS);
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
     * Whether to block hostile mob spawn at given position (compatible with old calls, does not distinguish entity types).
     */
    public static boolean shouldBlockMonsterSpawn(ServerWorld world, BlockPos pos) {
        return decide(world, pos, null).block();
    }
}
