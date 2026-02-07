package civil.civilization.core;

import civil.civilization.CScore;
import civil.civilization.CivilValues;
import civil.civilization.cache.simple.LruL1Cache;
import civil.civilization.structure.NeighborhoodSampler;
import civil.civilization.structure.VoxelChunkKey;
import civil.civilization.structure.VoxelChunkRegion;
import civil.civilization.structure.VoxelRegion;
import civil.civilization.operator.CivilComputeContext;
import civil.civilization.operator.CivilizationOperator;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Simple civilization service: neighborhood sampling + operator list + LRU cache.
 * For each voxel chunk, first check cache, if miss then compute and write back;
 * chunk scores are first recorded in "voxel chunk -> score" matrix, then convex aggregation + civilization cohesion bonus.
 * Monster heads (force allow + headTypes) only take effect within "center voxel chunk range".
 */
public final class SimpleCivilizationService implements CivilizationService {

    /** Neighborhood sampling radius (voxel chunks): X/Z/Y three-direction expansion radius, total (2*radius+1) chunks. */
    public static final int RADIUS_X = 3;
    public static final int RADIUS_Z = 3;
    public static final int RADIUS_Y = 1;

    /** Core sampling area range (voxel chunks): chunks with |dx|≤CX, |dz|≤CZ, |dy|≤SY are fully computed and participate in cohesion; beyond is periphery, downsampled by excess value. */
    private static final int CORE_RADIUS_X = 2;
    private static final int CORE_RADIUS_Z = 2;
    private static final int CORE_RADIUS_Y = 1;
    /** Periphery stable hash downsampling: sampling probability p(excess) = 1/(1 + ALPHA_DOWN * excess). */
    private static final double ALPHA_DOWN = 0.5;

    /** Monster head influence range (voxel chunks): allowed ± chunk count relative to center in cx/cz/sy three directions, invalid if exceeds any direction. */
    private static final int MONSTER_HEAD_RANGE_CX = 1;
    private static final int MONSTER_HEAD_RANGE_CZ = 1;
    private static final int MONSTER_HEAD_RANGE_SY = 0;

    // ========== Aggregation Parameters ==========

    /** Sigmoid curve midpoint: when totalRaw equals this value, output is 0.5. */
    private static final double SIGMOID_MID = 0.8;
    /** Sigmoid curve steepness. */
    private static final double SIGMOID_STEEPNESS = 3.0;
    /** rawSigmoid(0), used for normalization so curve passes (0,0). */
    private static final double SIGMOID_S0 = 1.0 / (1.0 + Math.exp(SIGMOID_STEEPNESS * SIGMOID_MID));
    /** Cohesion term weight. */
    private static final double BETA = 0.8;
    /** Distance decay coefficient. */
    /** Distance squared decay coefficient (squared decay makes distant contributions quickly approach 0, nearby remains mostly unchanged). */
    private static final double ALPHA_DIST_SQ = 0.05;

    /** Face-adjacent 6 directions. */
    private static final int[][] FACE_OFFSETS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private final NeighborhoodSampler sampler;
    private final List<CivilizationOperator> operators;
    private final LruL1Cache cache;

    public SimpleCivilizationService(NeighborhoodSampler sampler, List<CivilizationOperator> operators, LruL1Cache cache) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.operators = List.copyOf(operators);
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public double getScoreAt(ServerWorld world, BlockPos pos) {
        return getCScoreAt(world, pos).score();
    }

    @Override
    public CScore getCScoreAt(ServerWorld world, BlockPos pos) {
        if (operators.isEmpty()) {
            return new CScore(0.0, List.of());
        }
        VoxelChunkRegion chunkRegion = sampler.sample(world, pos);
        if (chunkRegion.size() == 0) {
            return new CScore(0.0, List.of());
        }
        VoxelChunkKey centerChunk = VoxelChunkKey.from(pos);
        Map<VoxelChunkKey, Double> chunkScoreMatrix = new HashMap<>();
        boolean anyForceAllow = false;
        List<EntityType<?>> allHeadTypes = new ArrayList<>();

        for (var entry : chunkRegion.entries()) {
            VoxelChunkKey key = entry.getKey();
            int dx = key.getCx() - centerChunk.getCx();
            int dy = key.getSy() - centerChunk.getSy();
            int dz = key.getCz() - centerChunk.getCz();

            boolean inCore = Math.abs(dx) <= CORE_RADIUS_X && Math.abs(dz) <= CORE_RADIUS_Z && Math.abs(dy) <= CORE_RADIUS_Y;
            if (!inCore) {
                int excess = excessForDownsampling(dx, dy, dz);
                double p = 1.0 / (1.0 + ALPHA_DOWN * excess);
                double u = stableHashU(dx, dy, dz);
                if (u >= p) continue;
            }

            VoxelRegion region = entry.getValue();
            CScore cScore = cache.getChunkCScore(world, key).orElseGet(() -> {
                CScore c = computeCScoreForRegion(region);
                cache.putChunkCScore(world, key, c);
                return c;
            });
            boolean inMonsterHeadRange = isWithinMonsterHeadRange(key, centerChunk);
            if (cScore.isForceAllow() && inMonsterHeadRange) {
                anyForceAllow = true;
            }
            chunkScoreMatrix.put(key, cScore.score());
            if (inMonsterHeadRange && cScore.headTypes() != null) {
                allHeadTypes.addAll(cScore.headTypes());
            }
        }

        double score = anyForceAllow
                ? CivilValues.FORCE_ALLOW_SCORE
                : aggregate(chunkScoreMatrix, centerChunk);
        return new CScore(score, Collections.unmodifiableList(allHeadTypes));
    }

    /**
     * Excess value relative to core area: parts exceeding CORE_RADIUS_* in three directions, combined into scalar for periphery downsampling probability.
     * excessX = max(0, |dx| - CORE_RADIUS_X), same for excessZ, excessY; returns excessX + excessZ + excessY² (vertical squared to quickly suppress vertical sampling).
     */
    private static int excessForDownsampling(int dx, int dy, int dz) {
        int ex = Math.max(0, Math.abs(dx) - CORE_RADIUS_X);
        int ez = Math.max(0, Math.abs(dz) - CORE_RADIUS_Z);
        int ey = Math.max(0, Math.abs(dy) - CORE_RADIUS_Y);
        return ex + ez + ey * ey;
    }

    /** Stable hash: (dx, dy, dz) → [0, 1), same offset always produces same result. */
    private static double stableHashU(int dx, int dy, int dz) {
        int h = Objects.hash(dx, dy, dz);
        return ((h & 0x7FFF_FFFF) + 0.5) / (Integer.MAX_VALUE + 1.0);
    }

    /** Whether this voxel chunk is within "center voxel chunk range" monster head influence (|dcx|≤CX, |dcz|≤CZ, |dsy|≤SY). */
    private static boolean isWithinMonsterHeadRange(VoxelChunkKey key, VoxelChunkKey center) {
        return Math.abs(key.getCx() - center.getCx()) <= MONSTER_HEAD_RANGE_CX
                && Math.abs(key.getCz() - center.getCz()) <= MONSTER_HEAD_RANGE_CZ
                && Math.abs(key.getSy() - center.getSy()) <= MONSTER_HEAD_RANGE_SY;
    }

    /** Run all operators on a single voxel region, get CScore (score + headTypes). */
    private CScore computeCScoreForRegion(VoxelRegion region) {
        CivilComputeContext context = new CivilComputeContext();
        double maxScore = 0.0;
        for (CivilizationOperator operator : operators) {
            double s = operator.computeScore(region, context);
            if (s >= CivilValues.FORCE_ALLOW_SCORE) {
                return new CScore(s, List.copyOf(context.getHeadTypes()));
            }
            if (s > maxScore) {
                maxScore = s;
            }
        }
        double score = Math.max(0.0, Math.min(1.0, maxScore));
        return new CScore(score, List.copyOf(context.getHeadTypes()));
    }

    // ========== Aggregation Calculation ==========

    /**
     * Calculate final civilization score [0, 1] with cohesion and distance decay based on "voxel chunk -> raw score" matrix and center voxel chunk.
     */
    private static double aggregate(Map<VoxelChunkKey, Double> chunkScores, VoxelChunkKey centerChunk) {
        if (chunkScores == null || chunkScores.isEmpty()) {
            return 0.0;
        }
        double rawSum = 0.0;
        for (Map.Entry<VoxelChunkKey, Double> e : chunkScores.entrySet()) {
            double s = e.getValue();
            double w = weightForChunk(e.getKey(), centerChunk);
            rawSum += s * w;
        }
        double cohesion = computeCohesion(chunkScores, centerChunk);
        double totalRaw = rawSum + BETA * cohesion;
        return sigmoid(totalRaw);
    }

    /** Sigmoid curve: normalized to always pass (0,0). */
    private static double sigmoid(double totalRaw) {
        double s = rawSigmoid(totalRaw);
        double scale = 1.0 - SIGMOID_S0;
        if (scale <= 1e-10) return totalRaw <= 0.0 ? 0.0 : 1.0;
        return Math.max(0.0, Math.min(1.0, (s - SIGMOID_S0) / scale));
    }

    /** Raw sigmoid: 1 / (1 + exp(-k*(x - mid))). */
    private static double rawSigmoid(double x) {
        double t = SIGMOID_STEEPNESS * (x - SIGMOID_MID);
        if (t >= 20.0) return 1.0;
        if (t <= -20.0) return 0.0;
        return 1.0 / (1.0 + Math.exp(-t));
    }

    /** Weight of voxel chunk relative to center: center is 1, decreases with distance. */
    private static double weightForChunk(VoxelChunkKey chunk, VoxelChunkKey center) {
        if (center == null) {
            return 1.0;
        }
        int d = Math.abs(chunk.getCx() - center.getCx())
                + Math.abs(chunk.getCz() - center.getCz())
                + Math.abs(chunk.getSy() - center.getSy());
        return 1.0 / (1.0 + ALPHA_DIST_SQ * d * d);  // Squared decay
    }

    /** Cohesion amount: all "face-adjacent" edges with both ends score > 0, filtered by core area. */
    private static double computeCohesion(Map<VoxelChunkKey, Double> chunkScores, VoxelChunkKey center) {
        double sum = 0.0;
        for (Map.Entry<VoxelChunkKey, Double> e : chunkScores.entrySet()) {
            VoxelChunkKey key = e.getKey();
            if (!isInCore(key, center)) continue;
            double s = e.getValue();
            if (s <= 0.0) continue;
            double w = weightForChunk(key, center);
            int cx = key.getCx(), cz = key.getCz(), sy = key.getSy();
            for (int[] d : FACE_OFFSETS) {
                VoxelChunkKey neighbor = new VoxelChunkKey(cx + d[0], cz + d[1], sy + d[2]);
                if (!isInCore(neighbor, center)) continue;
                Double ns = chunkScores.get(neighbor);
                if (ns != null && ns > 0.0) {
                    sum += bondStrength(s, ns) * w;
                }
            }
        }
        return sum * 0.5;
    }

    /** Whether in core area. */
    private static boolean isInCore(VoxelChunkKey key, VoxelChunkKey center) {
        return Math.abs(key.getCx() - center.getCx()) <= CORE_RADIUS_X
                && Math.abs(key.getCz() - center.getCz()) <= CORE_RADIUS_Z
                && Math.abs(key.getSy() - center.getSy()) <= CORE_RADIUS_Y;
    }

    /** Cohesion strength of an edge: centroid formula. */
    private static double bondStrength(double s, double ns) {
        double denom = s + ns;
        return denom > 0.0 ? (s * s + ns * ns) / denom : 0.0;
    }
}
