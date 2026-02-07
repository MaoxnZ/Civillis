package civil.civilization.core;

import civil.civilization.CScore;
import civil.civilization.CivilValues;
import civil.civilization.cache.simple.LruL2Cache;
import civil.civilization.cache.simple.LruL3Cache;
import civil.civilization.cache.simple.LruPyramidCache;
import civil.civilization.structure.*;
import civil.civilization.operator.CivilComputeContext;
import civil.civilization.operator.CivilizationOperator;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Pyramid-style multi-layer cache civilization scoring service.
 * 
 * <p>Uses imaginary layering + intersection list strategy:
 * <ul>
 *   <li>Imaginary core (3×3×1): centered on centerChunk, uses L1 data + cohesion</li>
 *   <li>Imaginary L2 area (outside brush): uses L2 cache, weighted by intersection ratio</li>
 *   <li>Imaginary L3 area (outside L2 area): uses L3 cache, weighted by intersection ratio</li>
 * </ul>
 * 
 * <p>Data within brush range directly aggregates using L1, does not access L2/L3 cache (avoids duplicate computation).
 */
public final class PyramidCivilizationService implements CivilizationService {

    /** Brush radius (voxel chunks): within brush range uses L1 computation and cascades updates to L2/L3. */
    public static final int BRUSH_RADIUS_X = 2;
    public static final int BRUSH_RADIUS_Z = 2;
    public static final int BRUSH_RADIUS_Y = 1;

    /** Detection range radius (voxel chunks): total detection range. */
    public static final int DETECTION_RADIUS_X = 7;
    public static final int DETECTION_RADIUS_Z = 7;
    public static final int DETECTION_RADIUS_Y = 1;

    /** Core radius (voxel chunks): imaginary core range, used for cohesion calculation. */
    private static final int CORE_RADIUS_X = 1;
    private static final int CORE_RADIUS_Z = 1;
    private static final int CORE_RADIUS_Y = 0;  // 3×3×1

    /** Monster head influence range (voxel chunks). */
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
    /** Distance squared decay coefficient (squared decay makes distant contributions quickly approach 0, nearby remains mostly unchanged). */
    private static final double ALPHA_DIST_SQ = 0.5;

    /** Face-adjacent 6 directions. */
    private static final int[][] FACE_OFFSETS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private final NeighborhoodSampler sampler;
    private final List<CivilizationOperator> operators;
    private final LruPyramidCache cache;

    public PyramidCivilizationService(
            NeighborhoodSampler sampler,
            List<CivilizationOperator> operators,
            LruPyramidCache cache) {
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

        VoxelChunkKey centerChunk = VoxelChunkKey.from(pos);
        int cx0 = centerChunk.getCx();
        int cz0 = centerChunk.getCz();
        int sy0 = centerChunk.getSy();

        // Precompute dimension Y bounds to avoid repeated queries
        var dim = world.getDimension();
        int dimMinY = dim.minY();
        int dimMaxY = dimMinY + dim.height() - 1;

        // ========== Define Regions ==========
        ChunkBox coreBox = ChunkBox.centered(cx0, cz0, sy0, CORE_RADIUS_X, CORE_RADIUS_Z, CORE_RADIUS_Y);
        ChunkBox brushBox = ChunkBox.centered(cx0, cz0, sy0, BRUSH_RADIUS_X, BRUSH_RADIUS_Z, BRUSH_RADIUS_Y);
        ChunkBox l2RegionBox = ChunkBox.centered(cx0, cz0, sy0, 4, 4, 1);  // 9×9×3 (imaginary L2 area)
        ChunkBox detectionBox = ChunkBox.centered(cx0, cz0, sy0, DETECTION_RADIUS_X, DETECTION_RADIUS_Z, DETECTION_RADIUS_Y);

        // ========== First Layer: Brush Range ==========
        Map<VoxelChunkKey, Double> chunkScoreMatrix = new HashMap<>();
        boolean anyForceAllow = false;
        List<EntityType<?>> allHeadTypes = new ArrayList<>();

        for (VoxelChunkKey l1 : brushBox.allChunks()) {
            // Skip chunks outside dimension bounds
            if (!l1.isValidIn(world, dimMinY, dimMaxY)) {
                continue;
            }
            CScore cScore = getOrComputeL1(world, l1);

            boolean inMonsterHeadRange = isWithinMonsterHeadRange(l1, centerChunk);
            if (cScore.isForceAllow() && inMonsterHeadRange) {
                anyForceAllow = true;
            }
            if (inMonsterHeadRange && cScore.headTypes() != null) {
                allHeadTypes.addAll(cScore.headTypes());
            }

            chunkScoreMatrix.put(l1, cScore.score());
        }

        // If force allow exists, return directly
        if (anyForceAllow) {
            return new CScore(CivilValues.FORCE_ALLOW_SCORE, Collections.unmodifiableList(allHeadTypes));
        }

        // Calculate core area score (with cohesion)
        double coreSum = 0.0;
        for (VoxelChunkKey l1 : coreBox.allChunks()) {
            Double score = chunkScoreMatrix.get(l1);
            if (score != null) {
                coreSum += score * weightForChunk(l1, centerChunk);
            }
        }
        double cohesion = computeCohesion(chunkScoreMatrix, centerChunk, coreBox);
        coreSum += cohesion;

        // Calculate score within brush, outside core
        double brushOuterSum = 0.0;
        for (VoxelChunkKey l1 : brushBox.allChunks()) {
            if (!coreBox.contains(l1)) {
                Double score = chunkScoreMatrix.get(l1);
                if (score != null) {
                    brushOuterSum += score * weightForChunk(l1, centerChunk);
                }
            }
        }

        // ========== Second Layer: Directly enumerate real L2 (clipped by imaginary L2 area) ==========
        // coarseScore is accumulated value (range [0, 9]), multiplied by (effectiveOverlap / 9.0) for proportional scaling
        double l2Sum = 0.0;
        LruL2Cache l2Cache = cache.getL2Cache();
        for (L2Key realL2 : findRealL2sInRegion(l2RegionBox)) {
            ChunkBox realBox = ChunkBox.fromL2(realL2);
            int overlapWithRegion = realBox.countOverlap(l2RegionBox);
            int overlapWithBrush = realBox.countOverlap(brushBox);
            int effectiveOverlap = overlapWithRegion - overlapWithBrush;
            if (effectiveOverlap <= 0) continue;

            Optional<L2Entry> entryOpt = l2Cache.get(world, realL2, this::computeL1Score);
            if (entryOpt.isPresent()) {
                double coarseScore = entryOpt.get().getCoarseScore(); // Accumulated value [0, 9]
                l2Sum += coarseScore * (effectiveOverlap / 9.0) * weightForChunk(realBox.center(), centerChunk);
            }
        }

        // ========== Third Layer: Directly enumerate real L3 (clipped by detection area, excluding L2 area) ==========
        // coarseScore is accumulated value (range [0, 243]), multiplied by (effectiveOverlap / 243.0) for proportional scaling
        double l3Sum = 0.0;
        LruL3Cache l3Cache = cache.getL3Cache();
        for (L3Key realL3 : findRealL3sInRegion(detectionBox)) {
            ChunkBox realBox = ChunkBox.fromL3(realL3);
            int overlapWithDetection = realBox.countOverlap(detectionBox);
            int overlapWithL2Region = realBox.countOverlap(l2RegionBox);
            int effectiveOverlap = overlapWithDetection - overlapWithL2Region;
            if (effectiveOverlap <= 0) continue;

            Optional<L3Entry> entryOpt = l3Cache.get(world, realL3, this::computeL1Score);
            if (entryOpt.isPresent()) {
                double coarseScore = entryOpt.get().getCoarseScore(); // Accumulated value [0, 243]
                l3Sum += coarseScore * (effectiveOverlap / 243.0) * weightForChunk(realBox.center(), centerChunk);
            }
        }

        // ========== Merge ==========
        double totalRaw = coreSum + brushOuterSum + l2Sum + l3Sum;
        double score = sigmoid(totalRaw);
        return new CScore(score, Collections.unmodifiableList(allHeadTypes));
    }

    // ========== Real Block Enumeration ==========

    /**
     * Find all real L2 blocks intersecting with the region (by locating two corner points then directly filling).
     */
    private List<L2Key> findRealL2sInRegion(ChunkBox region) {
        L2Key min = L2Key.from(region.getMinCx(), region.getMinCz(), region.getMinSy());
        L2Key max = L2Key.from(region.getMaxCx(), region.getMaxCz(), region.getMaxSy());
        List<L2Key> result = new ArrayList<>();
        for (int s2y = min.getS2y(); s2y <= max.getS2y(); s2y++) {
            for (int c2z = min.getC2z(); c2z <= max.getC2z(); c2z++) {
                for (int c2x = min.getC2x(); c2x <= max.getC2x(); c2x++) {
                    result.add(new L2Key(c2x, c2z, s2y));
                }
            }
        }
        return result;
    }

    /**
     * Find all real L3 blocks intersecting with the region (by locating two corner points then directly filling).
     */
    private List<L3Key> findRealL3sInRegion(ChunkBox region) {
        L3Key min = L3Key.from(region.getMinCx(), region.getMinCz(), region.getMinSy());
        L3Key max = L3Key.from(region.getMaxCx(), region.getMaxCz(), region.getMaxSy());
        List<L3Key> result = new ArrayList<>();
        for (int s3y = min.getS3y(); s3y <= max.getS3y(); s3y++) {
            for (int c3z = min.getC3z(); c3z <= max.getC3z(); c3z++) {
                for (int c3x = min.getC3x(); c3x <= max.getC3x(); c3x++) {
                    result.add(new L3Key(c3x, c3z, s3y));
                }
            }
        }
        return result;
    }

    // ========== L1 Calculation ==========

    /**
     * Get or compute L1 score (for brush range).
     * If chunk is not within dimension bounds, returns 0 score and does not write to cache.
     */
    private CScore getOrComputeL1(ServerWorld world, VoxelChunkKey key) {
        // First check dimension bounds
        if (!key.isValidIn(world)) {
            return new CScore(0.0, List.of());
        }
        Optional<CScore> cached = cache.getChunkCScore(world, key);
        if (cached.isPresent()) {
            return cached.get();
        }
        CScore cScore = computeCScoreForChunk(world, key);
        cache.putChunkCScore(world, key, cScore);  // Cascade update L2/L3
        return cScore;
    }

    /**
     * Calculate single L1 score (for L2/L3 repairing dirty cells).
     * If chunk is not within dimension bounds, returns 0.
     */
    private double computeL1Score(ServerWorld world, VoxelChunkKey key) {
        if (!key.isValidIn(world)) {
            return 0.0;
        }
        return computeCScoreForChunk(world, key).score();
    }

    /**
     * Calculate CScore for a single voxel chunk.
     * Caller should first check key.isValidIn(world).
     */
    private CScore computeCScoreForChunk(ServerWorld world, VoxelChunkKey key) {
        VoxelRegion voxelRegion = sampler.sampleOneVoxelChunk(world, key);
        if (voxelRegion == null) {
            return new CScore(0.0, List.of());
        }
        return computeCScoreForRegion(voxelRegion);
    }

    /**
     * Run all operators on a single voxel region, get CScore.
     */
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

    private static double sigmoid(double totalRaw) {
        double s = rawSigmoid(totalRaw);
        double scale = 1.0 - SIGMOID_S0;
        if (scale <= 1e-10) return totalRaw <= 0.0 ? 0.0 : 1.0;
        return Math.max(0.0, Math.min(1.0, (s - SIGMOID_S0) / scale));
    }

    private static double rawSigmoid(double x) {
        double t = SIGMOID_STEEPNESS * (x - SIGMOID_MID);
        if (t >= 20.0) return 1.0;
        if (t <= -20.0) return 0.0;
        return 1.0 / (1.0 + Math.exp(-t));
    }

    private static double weightForChunk(VoxelChunkKey chunk, VoxelChunkKey center) {
        int d = Math.abs(chunk.getCx() - center.getCx())
                + Math.abs(chunk.getCz() - center.getCz())
                + Math.abs(chunk.getSy() - center.getSy());
        return 1.0 / (1.0 + ALPHA_DIST_SQ * d * d);  // Squared decay
    }

    /**
     * Calculate cohesion within core range.
     */
    private static double computeCohesion(Map<VoxelChunkKey, Double> chunkScores, VoxelChunkKey center, ChunkBox coreBox) {
        double sum = 0.0;
        for (Map.Entry<VoxelChunkKey, Double> e : chunkScores.entrySet()) {
            VoxelChunkKey key = e.getKey();
            if (!coreBox.contains(key)) continue;
            double s = e.getValue();
            if (s <= 0.0) continue;
            double w = weightForChunk(key, center);
            int cx = key.getCx(), cz = key.getCz(), sy = key.getSy();
            for (int[] d : FACE_OFFSETS) {
                VoxelChunkKey neighbor = new VoxelChunkKey(cx + d[0], cz + d[1], sy + d[2]);
                if (!coreBox.contains(neighbor)) continue;
                Double ns = chunkScores.get(neighbor);
                if (ns != null && ns > 0.0) {
                    sum += bondStrength(s, ns) * w;
                }
            }
        }
        return BETA * sum * 0.5;
    }

    private static double bondStrength(double s, double ns) {
        double denom = s + ns;
        return denom > 0.0 ? (s * s + ns * ns) / denom : 0.0;
    }

    private static boolean isWithinMonsterHeadRange(VoxelChunkKey key, VoxelChunkKey center) {
        return Math.abs(key.getCx() - center.getCx()) <= MONSTER_HEAD_RANGE_CX
                && Math.abs(key.getCz() - center.getCz()) <= MONSTER_HEAD_RANGE_CZ
                && Math.abs(key.getSy() - center.getSy()) <= MONSTER_HEAD_RANGE_SY;
    }
}
