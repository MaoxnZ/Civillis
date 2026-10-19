package civil.spawn;

import civil.CivilMod;
import civil.CivilServices;
import civil.civilization.CScore;
import civil.civilization.FarmShrineTracker;
import civil.civilization.ZonePolicyService;
import civil.config.CivilConfig;
import civil.registry.DimensionPolicyRegistry;
import civil.registry.SpawnGateEntityRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/**
 * Fusion Architecture spawn policy — dimension policy (civilization gate first), then farm shrines,
 * datapack spawn-gate whitelist, zone policy, then civilization score.
 *
 * <p>{@link DimensionPolicyRegistry} (datapack {@code civil_dimension_policies}) can disable
 * head mechanics and/or civilization per dimension. When civilization is off for the current
 * dimension, head stages are skipped and {@link SpawnDecision#BRANCH_DIM_NEUTRAL} is returned
 * (no zone/score stages).
 *
 * <p>When civilization is on, decision priority is:
 * <ol>
 *   <li>SHRINE_NEARBY: spawn inside an activated shrine bypass box → bypass civilization suppression;
 *       conversion handled downstream by Mixin (probability from head union count).</li>
 *   <li>SHRINE_SUPPRESS: shrines within attract radius → probabilistic block (no suppression count on
 *       {@link SpawnDecision}; compact constructor when blocked).</li>
 *   <li>SPAWN_GATE_WHITELIST: datapack {@code civil_spawn_gate_entities} whitelist → allow before zone</li>
 *   <li>ZONE_POLICY: structure rules that allow hostile spawn</li>
 *   <li>LOW/MID/HIGH: civilization score thresholds (result shard O(1) query)</li>
 * </ol>
 *
 * <p>{@link SpawnGateEntityRegistry} whitelist is only applied when {@code entityType != null}
 * (e.g. {@link #shouldBlockMonsterSpawn} passes null and does not use whitelist).
 */
public final class SpawnPolicy {

    private SpawnPolicy() {
    }

    /**
     * Returns complete decision (whether to block + civilization value + branch).
     */
    public static SpawnDecision decide(ServerLevel world, BlockPos pos, EntityType<?> entityType) {

        String dim = world.dimension().identifier().toString();
        DimensionPolicyRegistry.DimensionPolicy dimPolicy = DimensionPolicyRegistry.policyFor(dim);

        if (!dimPolicy.civilization()) {
            return new SpawnDecision(false, 0.0, SpawnDecision.BRANCH_DIM_NEUTRAL);
        }

        if (dimPolicy.headMechanics()) {
            FarmShrineTracker shrineTracker = CivilServices.getFarmShrineTracker();
            if (shrineTracker != null && shrineTracker.isInitialized()) {
                FarmShrineTracker.ShrineQuery sq = shrineTracker.queryForSpawn(dim, pos);
                if (sq.insideShrine()) {
                    return new SpawnDecision(false, 0, SpawnDecision.BRANCH_SHRINE_NEARBY,
                            sq.conversionHeadCount(), sq.convertPool());
                }
                if (sq.hasNearbyShrine()) {
                    SpawnDecision suppressDecision = checkShrineSuppression(world, pos, sq);
                    if (suppressDecision != null) {
                        return suppressDecision;
                    }
                }
            }
        }

        if (entityType != null && SpawnGateEntityRegistry.isWhitelist(entityType)) {
            return new SpawnDecision(false, 0, SpawnDecision.BRANCH_SPAWN_GATE_WHITELIST);
        }

        ZonePolicyService zonePolicyService = CivilServices.getZonePolicyService();
        if (zonePolicyService != null && zonePolicyService.allowsHostileSpawn(world, pos, entityType)) {
            return new SpawnDecision(false, 0, SpawnDecision.BRANCH_ZONE_POLICY);
        }

        CScore cScore = CivilServices.getCivilizationService().getCScoreAt(world, pos);
        double score = cScore.score();
        double thresholdLow = CivilConfig.spawnThresholdLow;
        double thresholdMid = CivilConfig.spawnThresholdMid;

        if (score <= thresholdLow) {
            return new SpawnDecision(false, score, SpawnDecision.BRANCH_LOW);
        }
        if (score > thresholdLow && score < thresholdMid) {
            double t = (score - thresholdLow) / (thresholdMid - thresholdLow);
            RandomSource random = world.getRandom();
            boolean block = random.nextDouble() < t;
            return new SpawnDecision(block, score, SpawnDecision.BRANCH_MID);
        }
        return new SpawnDecision(true, score, SpawnDecision.BRANCH_HIGH);
    }

    private static SpawnDecision checkShrineSuppression(ServerLevel world, BlockPos pos,
            FarmShrineTracker.ShrineQuery q) {
        double d = q.suppressNearestDist3d();
        if (!Double.isFinite(d)) {
            return null;
        }
        int count = q.suppressionUnionHeadCount();
        double lambda = CivilConfig.farmShrineAttractLambda * (1.0 + Math.log1p(count));
        double suppressChance = 1.0 - Math.exp(-lambda * d / 16.0);

        if (world.getRandom().nextDouble() < suppressChance) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info(
                        "[civil] shrine_suppress block pos=({}, {}, {}) dist3D={} distXZ={} chance={} unionHeads={}",
                        pos.getX(), pos.getY(), pos.getZ(),
                        String.format("%.1f", d),
                        String.format("%.1f", q.suppressNearestDistXZ()),
                        String.format("%.3f", suppressChance),
                        count);
            }
            return new SpawnDecision(true, 0, SpawnDecision.BRANCH_SHRINE_SUPPRESS);
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info(
                    "[civil] shrine_suppress pass pos=({}, {}, {}) dist3D={} distXZ={} chance={} unionHeads={}",
                    pos.getX(), pos.getY(), pos.getZ(),
                    String.format("%.1f", d),
                    String.format("%.1f", q.suppressNearestDistXZ()),
                    String.format("%.3f", suppressChance),
                    count);
        }
        return null;
    }

    /**
     * Whether to block hostile mob spawn at given position.
     */
    public static boolean shouldBlockMonsterSpawn(ServerLevel world, BlockPos pos) {
        return decide(world, pos, null).block();
    }
}
