package civil.civilization.core;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.civilization.CivilValues;
import civil.civilization.cache.scalable.TtlCacheService;
import civil.civilization.cache.scalable.TtlCacheService.QueryResult;
import civil.civilization.structure.*;
import civil.civilization.operator.CivilComputeContext;
import civil.civilization.operator.CivilizationOperator;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scalable civilization scoring service: adapts the TTL cache + H2 persistence
 * pyramid multi-layer cache architecture.
 * 
 * <p>Core features:
 * <ul>
 *   <li>Returns conservative estimate (max civilization value) when data is not loaded, preventing false spawns</li>
 *   <li>Async loading does not block the main thread</li>
 *   <li>TTL eviction for civilization decay</li>
 *   <li>Supports large-scale servers (thousands of concurrent players)</li>
 * </ul>
 * 
 * <p>Differences from {@link PyramidCivilizationService}:
 * <ul>
 *   <li>Uses {@link TtlCacheService} instead of LruPyramidCache</li>
 *   <li>L2/L3 queries use QueryResult with conservative estimates</li>
 *   <li>No capacity limit; relies on TTL expiry for eviction</li>
 * </ul>
 */
public final class ScalableCivilizationService implements CivilizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-scalable");
    
    /** Log sampling rate: output detailed log every N calls. */
    private static final int LOG_SAMPLE_RATE = 100;
    private final AtomicInteger callCounter = new AtomicInteger(0);

    /** Brush radius (voxel chunks): brush range uses L1 computation with cascade updates to L2/L3. */
    public static final int BRUSH_RADIUS_X = 2;
    public static final int BRUSH_RADIUS_Z = 2;
    public static final int BRUSH_RADIUS_Y = 1;

    /** Detection radius (voxel chunks): total detection range. */
    public static final int DETECTION_RADIUS_X = 7;
    public static final int DETECTION_RADIUS_Z = 7;
    public static final int DETECTION_RADIUS_Y = 1;

    /** Core radius (voxel chunks): hypothetical core range for cohesion calculation. */
    private static final int CORE_RADIUS_X = 1;
    private static final int CORE_RADIUS_Z = 1;
    private static final int CORE_RADIUS_Y = 0;  // 3x3x1

    /** Monster head influence range (voxel chunks). */
    private static final int MONSTER_HEAD_RANGE_CX = 1;
    private static final int MONSTER_HEAD_RANGE_CZ = 1;
    private static final int MONSTER_HEAD_RANGE_SY = 0;

    // ========== Aggregation parameters ==========

    /** Sigmoid midpoint: output is 0.5 when totalRaw equals this value. */
    private static final double SIGMOID_MID = 0.8;
    /** Sigmoid steepness. */
    private static final double SIGMOID_STEEPNESS = 3.0;
    /** rawSigmoid(0), used for normalization so the curve passes through (0,0). */
    private static final double SIGMOID_S0 = 1.0 / (1.0 + Math.exp(SIGMOID_STEEPNESS * SIGMOID_MID));
    /** Cohesion term weight. */
    private static final double BETA = 0.8;
    /** Distance squared decay coefficient (squared decay makes distant contributions approach 0 quickly while nearby remains nearly unchanged). */
    private static final double ALPHA_DIST_SQ = 0.5;

    /** Face-adjacent 6 directions. */
    private static final int[][] FACE_OFFSETS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private final NeighborhoodSampler sampler;
    private final List<CivilizationOperator> operators;
    private final TtlCacheService cacheService;

    public ScalableCivilizationService(
            NeighborhoodSampler sampler,
            List<CivilizationOperator> operators,
            TtlCacheService cacheService) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.operators = List.copyOf(operators);
        this.cacheService = Objects.requireNonNull(cacheService, "cacheService");
    }

    @Override
    public double getScoreAt(ServerWorld world, BlockPos pos) {
        return getCScoreAt(world, pos).score();
    }

    @Override
    public CScore getCScoreAt(ServerWorld world, BlockPos pos) {
        long startTimeUs = System.nanoTime() / 1000;
        int callNum = callCounter.incrementAndGet();
        boolean shouldLog = CivilMod.DEBUG && (callNum % LOG_SAMPLE_RATE == 0);
        
        if (operators.isEmpty()) {
            return new CScore(0.0, List.of());
        }

        VoxelChunkKey centerChunk = VoxelChunkKey.from(pos);
        int cx0 = centerChunk.getCx();
        int cz0 = centerChunk.getCz();
        int sy0 = centerChunk.getSy();

        // Pre-compute dimension info
        String dimName = shouldLog ? world.getRegistryKey().toString() : null;
        
        // Pre-compute dimension Y bounds to avoid repeated queries
        var dim = world.getDimension();
        int dimMinY = dim.minY();
        int dimMaxY = dimMinY + dim.height() - 1;

        // ========== Define regions ==========
        ChunkBox coreBox = ChunkBox.centered(cx0, cz0, sy0, CORE_RADIUS_X, CORE_RADIUS_Z, CORE_RADIUS_Y);
        ChunkBox brushBox = ChunkBox.centered(cx0, cz0, sy0, BRUSH_RADIUS_X, BRUSH_RADIUS_Z, BRUSH_RADIUS_Y);
        ChunkBox l2RegionBox = ChunkBox.centered(cx0, cz0, sy0, 4, 4, 1);  // 9x9x3 (hypothetical L2 region)
        ChunkBox detectionBox = ChunkBox.centered(cx0, cz0, sy0, DETECTION_RADIUS_X, DETECTION_RADIUS_Z, DETECTION_RADIUS_Y);

        // ========== Layer 1: Brush range ==========
        // The brush range is the core area for spawn decisions; L1 does not use cold storage.
        // Hot cache hits are used directly; misses are computed synchronously.
        Map<VoxelChunkKey, Double> chunkScoreMatrix = new HashMap<>();
        boolean anyForceAllow = false;
        List<EntityType<?>> allHeadTypes = new ArrayList<>();

        for (VoxelChunkKey l1 : brushBox.allChunks()) {
            // Skip chunks outside dimension bounds
            if (!l1.isValidIn(world, dimMinY, dimMaxY)) {
                continue;
            }
            
            // L1: check hot cache directly, compute synchronously on miss
            CScore cScore = cacheService.getChunkCScore(world, l1)
                    .orElseGet(() -> computeAndCacheL1(world, l1));

            boolean inMonsterHeadRange = isWithinMonsterHeadRange(l1, centerChunk);
            if (cScore.isForceAllow() && inMonsterHeadRange) {
                anyForceAllow = true;
            }
            if (inMonsterHeadRange && cScore.headTypes() != null) {
                allHeadTypes.addAll(cScore.headTypes());
            }

            chunkScoreMatrix.put(l1, cScore.score());
        }

        // If force allow detected, return immediately
        if (anyForceAllow) {
            return new CScore(CivilValues.FORCE_ALLOW_SCORE, Collections.unmodifiableList(allHeadTypes));
        }

        // Compute core region score (with cohesion)
        double coreSum = 0.0;
        for (VoxelChunkKey l1 : coreBox.allChunks()) {
            Double score = chunkScoreMatrix.get(l1);
            if (score != null) {
                coreSum += score * weightForChunk(l1, centerChunk);
            }
        }
        double cohesion = computeCohesion(chunkScoreMatrix, centerChunk, coreBox);
        coreSum += cohesion;

        // Compute brush-inner, core-outer score
        double brushOuterSum = 0.0;
        for (VoxelChunkKey l1 : brushBox.allChunks()) {
            if (!coreBox.contains(l1)) {
                Double score = chunkScoreMatrix.get(l1);
                if (score != null) {
                    brushOuterSum += score * weightForChunk(l1, centerChunk);
                }
            }
        }

        // ========== Layer 2: Enumerate real L2 blocks (clipped to hypothetical L2 region) ==========
        // coarseScore is a cumulative value (range [0, 9]), scaled by (effectiveOverlap / 9.0)
        // L2 uses conservative estimate strategy: LOADING returns conservative estimate (waiting for cold storage load)
        double l2Sum = 0.0;
        for (L2Key realL2 : findRealL2sInRegion(l2RegionBox)) {
            ChunkBox realBox = ChunkBox.fromL2(realL2);
            int overlapWithRegion = realBox.countOverlap(l2RegionBox);
            int overlapWithBrush = realBox.countOverlap(brushBox);
            int effectiveOverlap = overlapWithRegion - overlapWithBrush;
            if (effectiveOverlap <= 0) continue;

            QueryResult l2Result = cacheService.queryL2Score(world, realL2, this::computeL1Score);
            // L2 returns precise value or conservative estimate (LOADING uses MAX_CIVILIZATION * CELL_COUNT)
            double coarseScore = l2Result.score();
            
            // Sampled log
            if (shouldLog) {
                String status = l2Result.isPrecise() ? "HIT" : "LOADING";
                LOGGER.info("[civil-scalable-query] layer=L2 dim={} key={},{},{} status={} score={}",
                        dimName, realL2.getC2x(), realL2.getC2z(), realL2.getS2y(), status, coarseScore);
            }
            
            l2Sum += coarseScore * (effectiveOverlap / 9.0) * weightForChunk(realBox.center(), centerChunk);
        }

        // ========== Layer 3: Enumerate real L3 blocks (clipped to detection box, excluding L2 region) ==========
        // coarseScore is a cumulative value (range [0, 243]), scaled by (effectiveOverlap / 243.0)
        // L3 uses conservative estimate strategy: LOADING returns conservative estimate (waiting for cold storage load)
        double l3Sum = 0.0;
        for (L3Key realL3 : findRealL3sInRegion(detectionBox)) {
            ChunkBox realBox = ChunkBox.fromL3(realL3);
            int overlapWithDetection = realBox.countOverlap(detectionBox);
            int overlapWithL2Region = realBox.countOverlap(l2RegionBox);
            int effectiveOverlap = overlapWithDetection - overlapWithL2Region;
            if (effectiveOverlap <= 0) continue;

            QueryResult l3Result = cacheService.queryL3Score(world, realL3, this::computeL1Score);
            // L3 has only PRECISE and LOADING (cold storage miss creates empty entry)
            double coarseScore = l3Result.score();
            
            // Sampled log
            if (shouldLog) {
                String status = l3Result.isPrecise() ? "HIT" : "LOADING";
                LOGGER.info("[civil-scalable-query] layer=L3 dim={} key={},{},{} status={} score={}",
                        dimName, realL3.getC3x(), realL3.getC3z(), realL3.getS3y(), status, coarseScore);
            }
            
            l3Sum += coarseScore * (effectiveOverlap / 243.0) * weightForChunk(realBox.center(), centerChunk);
        }

        // ========== Merge ==========
        double totalRaw = coreSum + brushOuterSum + l2Sum + l3Sum;
        double score = sigmoid(totalRaw);
        
        // Sampled log
        if (shouldLog) {
            long elapsedUs = System.nanoTime() / 1000 - startTimeUs;
            int l1Count = chunkScoreMatrix.size();
            // Estimate L2/L3 usage count
            int l2Count = findRealL2sInRegion(l2RegionBox).size();
            int l3Count = findRealL3sInRegion(detectionBox).size();
            LOGGER.info("[civil-scalable-brush] dim={} pos={},{},{} l1_count={} l2_count={} l3_count={} total_score={} elapsed_us={}",
                    dimName, pos.getX(), pos.getY(), pos.getZ(), 
                    l1Count, l2Count, l3Count, String.format("%.3f", totalRaw), elapsedUs);
        }
        
        return new CScore(score, Collections.unmodifiableList(allHeadTypes));
    }

    // ========== Real block enumeration ==========

    /**
     * Find all real L2 blocks that intersect with the region.
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
     * Find all real L3 blocks that intersect with the region.
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

    // ========== L1 computation ==========

    /**
     * Compute L1 score and write to cache.
     * 
     * <p>L1 does not use cold storage; computed synchronously on cache miss.
     * Results are written to hot cache with cascade updates to L2/L3.
     */
    private CScore computeAndCacheL1(ServerWorld world, VoxelChunkKey key) {
        if (!key.isValidIn(world)) {
            return new CScore(0.0, List.of());
        }
        long startUs = System.nanoTime() / 1000;
        CScore cScore = computeCScoreForChunk(world, key);
        cacheService.putChunkCScore(world, key, cScore);  // Write to hot cache, cascade update L2/L3
        
        // L1 computation log (sampling rate controlled by callCounter)
        if (CivilMod.DEBUG && (callCounter.get() % LOG_SAMPLE_RATE == 0)) {
            long elapsedUs = System.nanoTime() / 1000 - startUs;
            String dim = world.getRegistryKey().toString();
            LOGGER.info("[civil-scalable-compute] layer=L1 dim={} key={},{},{} score={} elapsed_us={}",
                    dim, key.getCx(), key.getCz(), key.getSy(), 
                    String.format("%.4f", cScore.score()), elapsedUs);
        }
        
        return cScore;
    }

    /**
     * Compute a single L1 score (used for L2/L3 dirty cell repair).
     * Returns 0 if the chunk is outside dimension bounds.
     */
    private double computeL1Score(ServerWorld world, VoxelChunkKey key) {
        if (!key.isValidIn(world)) {
            return 0.0;
        }
        return computeCScoreForChunk(world, key).score();
    }

    /**
     * Compute CScore for a single voxel chunk.
     * Caller should verify key.isValidIn(world) first.
     */
    private CScore computeCScoreForChunk(ServerWorld world, VoxelChunkKey key) {
        VoxelRegion voxelRegion = sampler.sampleOneVoxelChunk(world, key);
        if (voxelRegion == null) {
            return new CScore(0.0, List.of());
        }
        return computeCScoreForRegion(voxelRegion);
    }

    /**
     * Run all operators on a single voxel region to produce a CScore.
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

    // ========== Aggregation computation ==========

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
     * Compute cohesion within the core range.
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
