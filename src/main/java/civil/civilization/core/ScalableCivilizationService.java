package civil.civilization.core;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.config.CivilConfig;
import civil.civilization.ServerClock;
import civil.civilization.cache.TtlCacheService;
import civil.civilization.cache.TtlCacheService.QueryResult;
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
 * Civilization scoring service: TTL cache + H2 persistence + gradual decay.
 * 
 * <p>Core features:
 * <ul>
 *   <li>Returns conservative estimate (max civilization value) when data is not loaded, preventing false spawns</li>
 *   <li>Async loading does not block the main thread</li>
 *   <li>Gradual decay via {@code presenceTime} (ServerClock-based) instead of hard TTL cutoff</li>
 *   <li>Supports large-scale servers (thousands of concurrent players)</li>
 * </ul>
 */
public final class ScalableCivilizationService implements CivilizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-scalable");
    
    /** Log sampling rate: output detailed log every N calls. */
    private static final int LOG_SAMPLE_RATE = 100;
    private final AtomicInteger callCounter = new AtomicInteger(0);

    // All tunable parameters are in CivilConfig.

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
        ChunkBox coreBox = ChunkBox.centered(cx0, cz0, sy0, CivilConfig.coreRadiusX, CivilConfig.coreRadiusZ, CivilConfig.coreRadiusY);
        ChunkBox brushBox = ChunkBox.centered(cx0, cz0, sy0, CivilConfig.brushRadiusX, CivilConfig.brushRadiusZ, CivilConfig.brushRadiusY);
        ChunkBox l2RegionBox = ChunkBox.centered(cx0, cz0, sy0, 4, 4, 1);  // 9x9x3 (hypothetical L2 region)
        ChunkBox detectionBox = ChunkBox.centered(cx0, cz0, sy0, CivilConfig.detectionRadiusX, CivilConfig.detectionRadiusZ, CivilConfig.detectionRadiusY);

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

        // If any monster head in range, skip expensive L2/L3 queries — heads override spawn blocking
        if (anyForceAllow) {
            return new CScore(0.0, Collections.unmodifiableList(allHeadTypes));
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
        // Precise results are multiplied by a gradual decay factor based on presenceTime.
        long serverNow = ServerClock.now();
        double l2Sum = 0.0;
        for (L2Key realL2 : findRealL2sInRegion(l2RegionBox)) {
            ChunkBox realBox = ChunkBox.fromL2(realL2);
            int overlapWithRegion = realBox.countOverlap(l2RegionBox);
            int overlapWithBrush = realBox.countOverlap(brushBox);
            int effectiveOverlap = overlapWithRegion - overlapWithBrush;
            if (effectiveOverlap <= 0) continue;

            QueryResult l2Result = cacheService.queryL2Score(world, realL2, this::computeL1Score);
            double coarseScore = l2Result.score();

            // Apply gradual decay factor for precise results (LOADING uses conservative estimate, no extra decay)
            double decay = l2Result.isPrecise()
                    ? L2Entry.computeDecayFactor(serverNow, l2Result.presenceTime())
                    : 1.0;
            
            // Sampled log
            if (shouldLog) {
                String status = l2Result.isPrecise() ? "HIT" : "LOADING";
                LOGGER.info("[civil-scalable-query] layer=L2 dim={} key={},{},{} status={} score={} decay={}",
                        dimName, realL2.getC2x(), realL2.getC2z(), realL2.getS2y(), status, coarseScore,
                        String.format("%.3f", decay));
            }
            
            l2Sum += coarseScore * decay * (effectiveOverlap / 9.0) * weightForChunk(realBox.center(), centerChunk);
        }

        // ========== Layer 3: Enumerate real L3 blocks (clipped to detection box, excluding L2 region) ==========
        // coarseScore is a cumulative value (range [0, 243]), scaled by (effectiveOverlap / 243.0)
        // L3 uses conservative estimate strategy: LOADING returns conservative estimate (waiting for cold storage load)
        // Precise results are multiplied by a gradual decay factor based on presenceTime.
        double l3Sum = 0.0;
        for (L3Key realL3 : findRealL3sInRegion(detectionBox)) {
            ChunkBox realBox = ChunkBox.fromL3(realL3);
            int overlapWithDetection = realBox.countOverlap(detectionBox);
            int overlapWithL2Region = realBox.countOverlap(l2RegionBox);
            int effectiveOverlap = overlapWithDetection - overlapWithL2Region;
            if (effectiveOverlap <= 0) continue;

            QueryResult l3Result = cacheService.queryL3Score(world, realL3, this::computeL1Score);
            double coarseScore = l3Result.score();

            // Apply gradual decay factor for precise results
            double decay = l3Result.isPrecise()
                    ? L3Entry.computeDecayFactor(serverNow, l3Result.presenceTime())
                    : 1.0;
            
            // Sampled log
            if (shouldLog) {
                String status = l3Result.isPrecise() ? "HIT" : "LOADING";
                LOGGER.info("[civil-scalable-query] layer=L3 dim={} key={},{},{} status={} score={} decay={}",
                        dimName, realL3.getC3x(), realL3.getC3z(), realL3.getS3y(), status, coarseScore,
                        String.format("%.3f", decay));
            }
            
            l3Sum += coarseScore * decay * (effectiveOverlap / 243.0) * weightForChunk(realBox.center(), centerChunk);
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
     *
     * <p>Score is always in [0,1]. Monster head info flows through context,
     * not through the score value.
     */
    private CScore computeCScoreForRegion(VoxelRegion region) {
        CivilComputeContext context = new CivilComputeContext();
        double maxScore = 0.0;
        for (CivilizationOperator operator : operators) {
            double s = operator.computeScore(region, context);
            if (s > maxScore) {
                maxScore = s;
            }
        }
        double score = Math.max(0.0, Math.min(1.0, maxScore));
        return new CScore(score, List.copyOf(context.getHeadTypes()));
    }

    // ========== Aggregation computation ==========

    private static double sigmoid(double totalRaw) {
        double s0 = 1.0 / (1.0 + Math.exp(CivilConfig.sigmoidSteepness * CivilConfig.sigmoidMid));
        double s = rawSigmoid(totalRaw);
        double scale = 1.0 - s0;
        if (scale <= 1e-10) return totalRaw <= 0.0 ? 0.0 : 1.0;
        return Math.max(0.0, Math.min(1.0, (s - s0) / scale));
    }

    private static double rawSigmoid(double x) {
        double t = CivilConfig.sigmoidSteepness * (x - CivilConfig.sigmoidMid);
        if (t >= 20.0) return 1.0;
        if (t <= -20.0) return 0.0;
        return 1.0 / (1.0 + Math.exp(-t));
    }

    private static double weightForChunk(VoxelChunkKey chunk, VoxelChunkKey center) {
        int d = Math.abs(chunk.getCx() - center.getCx())
                + Math.abs(chunk.getCz() - center.getCz())
                + Math.abs(chunk.getSy() - center.getSy());
        return 1.0 / (1.0 + CivilConfig.distanceAlphaSq * d * d);
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
        return CivilConfig.cohesionBeta * sum * 0.5;
    }

    private static double bondStrength(double s, double ns) {
        double denom = s + ns;
        return denom > 0.0 ? (s * s + ns * ns) / denom : 0.0;
    }

    private static boolean isWithinMonsterHeadRange(VoxelChunkKey key, VoxelChunkKey center) {
        return Math.abs(key.getCx() - center.getCx()) <= CivilConfig.headRangeCX
                && Math.abs(key.getCz() - center.getCz()) <= CivilConfig.headRangeCZ
                && Math.abs(key.getSy() - center.getSy()) <= CivilConfig.headRangeSY;
    }
}
