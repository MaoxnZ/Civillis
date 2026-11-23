package civil.civilization.scoring;

import civil.CivilMod;
import civil.CivilServices;
import civil.civilization.CScore;
import civil.civilization.BaseScoreSourceRegistry;
import civil.config.CivilConfig;
import civil.civilization.ServerClock;
import civil.civilization.cache.ResultCache;
import civil.civilization.cache.ResultEntry;
import civil.civilization.cache.TtlCacheService;
import civil.civilization.VoxelChunkKey;
import civil.civilization.BlockScanner;
import civil.registry.DimensionPolicyRegistry;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * Fusion Architecture civilization scoring service.
 *
 * <p>Two-layer cache system:
 * <ul>
 *   <li><b>L1 Info Shards</b>: per-VC raw score, palette-accelerated, NBT-persisted</li>
 *   <li><b>Result Shards</b>: pre-aggregated coreSum/outerSum, O(1) spawn-check query</li>
 * </ul>
 *
 * <p>Spawn checks hit the result cache (O(1), ~50ns). Block changes trigger
 * palette L1 recompute + delta propagation to result shards (~13μs total).
 *
 * <p>L1 computation is inlined: a single 4096-block pass reads world block states
 * directly and accumulates weights via {@link BlockScanner#getBlockWeight}.
 * The old VoxelRegion / Sampler / Operator abstraction layers are removed.
 */
public final class ScalableCivilizationService implements CivilizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-scalable");
    private record L1Lookup(boolean known, double score) {}

    // (callCounter / LOG_SAMPLE_RATE removed — debug logging is now gated entirely by CivilMod.DEBUG)

    private final TtlCacheService cacheService;
    private final ResultCache resultCache;
    private static long debugL1WindowSec = -1L;
    private static int debugL1LookupCalls = 0;
    private static int debugL1CachedHit = 0;
    private static int debugL1ComputeAttempt = 0;
    private static int debugL1ComputeKnown = 0;
    private static int debugL1ComputeEmpty = 0;
    private static int debugL1EmptyInvalid = 0;
    private static int debugL1EmptyNoChunkNow = 0;

    public ScalableCivilizationService(TtlCacheService cacheService) {
        this.cacheService = Objects.requireNonNull(cacheService, "cacheService");
        this.resultCache = new ResultCache();
    }

    /** Access the result cache (for delta propagation, TTL cleanup, etc.). */
    public ResultCache getResultCache() {
        return resultCache;
    }

    // ========== CivilizationService interface ==========

    @Override
    public double getScoreAt(ServerLevel world, BlockPos pos) {
        return getCScoreAt(world, pos).score();
    }

    /**
     * Fusion Architecture: O(1) spawn-check via result shard cache.
     *
     * <p>Flow:
     * <ol>
     *   <li>Result cache hit + config valid → O(1) read (~50ns)</li>
     *   <li>Result cache hit + config invalid → recompute from L1 shards (~34μs)</li>
     *   <li>Result cache miss → compute from L1 shards (~34μs), cache result</li>
     * </ol>
     *
     * <p>Monster head detection is handled separately by HeadTracker
     * (queried directly in SpawnPolicy and CivilDetectorItem).
     */
    @Override
    public CScore getCScoreAt(ServerLevel world, BlockPos pos) {
        // long startTimeUs = CivilMod.DEBUG ? System.nanoTime() / 1000 : 0;

        if (!DimensionPolicyRegistry.policyFor(world).civilization()) {
            return new CScore(0.0);
        }

        VoxelChunkKey centerVC = VoxelChunkKey.from(pos);

        // Center chunk not available now: return conservative 0 and do not cache.
        if (getChunkNow(world, centerVC.getCx(), centerVC.getCz()) == null) {
            // if (CivilMod.DEBUG) {
            //     long elapsedUs = System.nanoTime() / 1000 - startTimeUs;
            //     String dimName = world.dimension().identifier().toString();
            //     LOGGER.info(
            //             "[civil-fusion-score] dim={} cx={} cz={} sy={} score=0.0000 raw=0.0000 freshness=1.0000 elapsed_us={} status=UNAVAILABLE",
            //             dimName, centerVC.getCx(), centerVC.getCz(), centerVC.getSy(), elapsedUs);
            // }
            return new CScore(0.0);
        }

        // O(1) result shard lookup (or lazy compute on miss)
        ResultEntry entry = resultCache.getOrCompute(world, centerVC, this::computeResultEntry);
        double score = entry.getEffectiveScore(ServerClock.now());

        // if (CivilMod.DEBUG) {
        //     long elapsedUs = System.nanoTime() / 1000 - startTimeUs;
        //     String dimName = world.dimension().identifier().toString();
        //     long serverNow = ServerClock.now();
        //     double freshness = ResultEntry.computeDecayFactor(serverNow, entry.getPresenceTime());
        //     LOGGER.info(
        //             "[civil-fusion-score] dim={} cx={} cz={} sy={} score={} raw={} freshness={} elapsed_us={}",
        //             dimName,
        //             centerVC.getCx(),
        //             centerVC.getCz(),
        //             centerVC.getSy(),
        //             String.format("%.4f", score),
        //             String.format("%.4f", entry.getRawScore(serverNow)),
        //             String.format("%.4f", freshness),
        //             elapsedUs);
        // }

        return new CScore(score);
    }

    // ========== Result shard computation ==========

    /**
     * Compute a fresh result entry by aggregating all L1 shards within detection range.
     *
     * <p>For each L1 shard in [-rx, rx] × [-rz, rz] × [-ry, ry]:
     * <ul>
     *   <li>Retrieve L1 score from hot cache (or compute synchronously on miss)</li>
     *   <li>Apply distance-based weight</li>
     *   <li>Accumulate into coreSum (within coreRadius) or outerSum (outside)</li>
     * </ul>
     *
     * <p>Cost: ~675 HashMap lookups × ~50ns = ~34μs (all L1 pre-filled by chunk load)
     */
    private ResultCache.ComputeResult computeResultEntry(ServerLevel world, VoxelChunkKey centerVC) {
        double coreSum = 0.0;
        double outerSum = 0.0;
        int unknownCount = 0;

        int rx = CivilConfig.detectionRadiusX;
        int rz = CivilConfig.detectionRadiusZ;
        int ry = CivilConfig.detectionRadiusY;

        var dim = world.dimensionType();
        int dimMinY = dim.minY();
        int dimMaxY = dimMinY + dim.height() - 1;

        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                for (int dy = -ry; dy <= ry; dy++) {
                    VoxelChunkKey shard = centerVC.offset(dx, dz, dy);

                    // Skip out-of-dimension shards
                    if (!shard.isValidIn(world, dimMinY, dimMaxY)) continue;

                    // Get L1 score: hot cache hit or synchronous compute
                    L1Lookup lookup = getL1Lookup(world, shard);
                    if (!lookup.known()) {
                        unknownCount++;
                        continue;
                    }
                    double l1Score = lookup.score();
                    if (l1Score <= 0.0) continue;

                    // Euclidean distance squared — no sqrt needed since weight uses d²
                    double distSq = dx * dx + dz * dz + dy * dy;
                    double weight = 1.0 / (1.0 + CivilConfig.distanceAlphaSq * distSq);
                    double contribution = l1Score * weight;

                    // Core/outer split: axis-aligned box check (each axis independent)
                    boolean inCore = Math.abs(dx) <= CivilConfig.coreRadiusX
                                  && Math.abs(dz) <= CivilConfig.coreRadiusZ
                                  && Math.abs(dy) <= CivilConfig.coreRadiusY;
                    if (inCore) {
                        coreSum += contribution;
                    } else {
                        outerSum += contribution;
                    }
                }
            }
        }

        // Restore persisted presenceTime / lastRecoveryTime (preload from bulk load or cold).
        String dimKey = world.dimension().identifier().toString();
        long[] persisted = cacheService.getPresenceForCompute(world, dimKey, centerVC);
        boolean cacheable = unknownCount == 0;
        ResultEntry entry = persisted != null
                ? new ResultEntry(coreSum, outerSum, persisted[0], persisted[1])
                : new ResultEntry(coreSum, outerSum, ServerClock.now());
        BaseScoreSourceRegistry baseScoreRegistry = CivilServices.getBaseScoreSourceRegistry();
        if (baseScoreRegistry != null && baseScoreRegistry.isInitialized()) {
            entry.setBaseScore(baseScoreRegistry.queryBaseScore(dimKey, centerVC));
        }
        return new ResultCache.ComputeResult(entry, cacheable);
    }

    // ========== L1 score access ==========

    /**
     * Get L1 score for a single shard.
     *
     * <p>Lookup chain: hot cache → region bulk load (if not activated) → palette recompute.
     * TtlCacheService.getChunkCScore handles bulk load on miss.
     */
    private L1Lookup getL1Lookup(ServerLevel world, VoxelChunkKey key) {
        Optional<CScore> cached = cacheService.getChunkCScore(world, key);
        if (cached.isPresent()) {
            if (CivilMod.DEBUG) {
                debugRecordL1Lookup(true);
            }
            return new L1Lookup(true, cached.get().score());
        }
        if (CivilMod.DEBUG) {
            debugRecordL1Lookup(false);
        }
        Optional<CScore> computed = computeAndCacheL1(world, key);
        if (computed.isPresent()) {
            return new L1Lookup(true, computed.get().score());
        }
        return new L1Lookup(false, 0.0);
    }

    // ========== L1 computation ==========

    /**
     * Compute L1 score and write to cache.
     * Uses palette pre-filter (ChunkSection.hasAny) to skip empty sections.
     */
    Optional<CScore> computeAndCacheL1(ServerLevel world, VoxelChunkKey key) {
        if (!key.isValidIn(world)) {
            if (CivilMod.DEBUG) {
                debugRecordL1Availability("invalid");
            }
            return Optional.empty();
        }
        // Guard: if the chunk isn't available immediately, treat as unknown.
        if (getChunkNow(world, key.getCx(), key.getCz()) == null) {
            if (CivilMod.DEBUG) {
                debugL1ComputeAttempt++;
                debugRecordL1Availability("no_chunk_now");
            }
            return Optional.empty();
        }
        long startUs = CivilMod.DEBUG ? System.nanoTime() / 1000 : 0;
        if (CivilMod.DEBUG) {
            debugL1ComputeAttempt++;
        }
        Optional<CScore> computed = computeCScoreForChunk(world, key);
        if (computed.isEmpty()) {
            if (CivilMod.DEBUG) {
                debugRecordL1Availability("no_chunk_now");
            }
            return Optional.empty();
        }
        CScore cScore = computed.get();
        cacheService.putChunkCScore(world, key, cScore);
        if (CivilMod.DEBUG) {
            debugRecordL1Availability("known");
        }

        if (CivilMod.DEBUG) {
            long elapsedUs = System.nanoTime() / 1000 - startUs;
            String dimLog = world.dimension().identifier().toString();
            LOGGER.info("[civil-fusion-compute] L1 dim={} key={},{},{} score={} elapsed_us={}",
                    dimLog, key.getCx(), key.getCz(), key.getSy(),
                    String.format("%.4f", cScore.score()), elapsedUs);
        }

        return computed;
    }

    /**
     * Compute CScore for a single voxel chunk (inlined single-pass).
     *
     * <ol>
     *   <li>Palette pre-filter: {@code ChunkSection.maybeHas(isTargetBlock)} (~1μs).
     *       Skips sections containing zero target blocks.</li>
     *   <li>Single 4096-block iteration: reads world block states directly and
     *       accumulates weights via {@link BlockScanner#getBlockWeight} (~100μs).</li>
     * </ol>
     *
     * <p>This replaces the old two-pass pipeline (VoxelRegion fill + operator scan).
     */
    private Optional<CScore> computeCScoreForChunk(ServerLevel world, VoxelChunkKey key) {
        ChunkAccess chunk = getChunkNow(world, key.getCx(), key.getCz());
        if (chunk == null) {
            return Optional.empty();
        }

        // Palette pre-filter: check if the section contains any target blocks.
        int sectionIdx = chunk.getSectionIndex(key.getSy() * 16);
        if (sectionIdx >= 0 && sectionIdx < chunk.getSections().length) {
            LevelChunkSection section = chunk.getSection(sectionIdx);
            if (!section.maybeHas(BlockScanner::isTargetBlock)) {
                return Optional.of(new CScore(0.0));
            }
        }

        // Single-pass: iterate world blocks directly, accumulate weight
        VoxelChunkKey.WorldBounds bounds = key.getWorldBounds(world);
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();
        if (min.getY() > max.getY()) {
            return Optional.of(new CScore(0.0));
        }

        double totalWeight = 0.0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    totalWeight += BlockScanner.getBlockWeight(state.getBlock());
                }
            }
        }

        if (totalWeight <= 0.0) {
            return Optional.of(new CScore(0.0));
        }
        double score = Math.min(1.0, totalWeight / CivilConfig.normalizationFactor);
        return Optional.of(new CScore(score));
    }

    // ========== Delta propagation (called from block change mixin) ==========

    /**
     * Handle a civilization block change: recompute L1, calculate delta, propagate.
     *
     * <p>Called from CivilLevelBlockChangeMixin when a special block is placed/removed.
     *
     * @param world the server world
     * @param pos   the block position that changed
     */
    public void onCivilBlockChanged(ServerLevel world, BlockPos pos) {
        long startNs = CivilMod.DEBUG ? System.nanoTime() : 0;
        BlockPos safePos = Objects.requireNonNull(pos, "pos");
        VoxelChunkKey shardKey = VoxelChunkKey.from(safePos);
        String dim = world.dimension().identifier().toString();

        // 1. Get old L1 score (hot + bulk load on miss via getChunkCScore, else 0.0)
        double oldScore = cacheService.getChunkCScore(world, shardKey)
                .map(CScore::score)
                .orElse(0.0);

        // 2. Recompute L1 via unified path (single write point in computeAndCacheL1)
        Optional<CScore> recomputed = computeAndCacheL1(world, shardKey);
        if (recomputed.isEmpty()) {
            if (CivilMod.DEBUG) {
                long elapsedUs = (System.nanoTime() - startNs) / 1000;
                String dimLog = world.dimension().identifier().toString();
                LOGGER.info("[civil-block-change] dim={} x={} y={} z={} cx={} cz={} sy={} status=SKIP_UNAVAILABLE elapsed_us={}",
                        dimLog, safePos.getX(), safePos.getY(), safePos.getZ(),
                        shardKey.getCx(), shardKey.getCz(), shardKey.getSy(),
                        elapsedUs);
            }
            return;
        }
        double newScore = recomputed.get().score();

        // 3. Calculate delta and propagate
        double delta = newScore - oldScore;
        if (Math.abs(delta) > 1e-10) {
            resultCache.propagateDelta(dim, shardKey, delta);
        }

        if (CivilMod.DEBUG) {
            long elapsedUs = (System.nanoTime() - startNs) / 1000;
            String dimLog = world.dimension().identifier().toString();
            LOGGER.info("[civil-block-change] dim={} x={} y={} z={} cx={} cz={} sy={} old={} new={} delta={} elapsed_us={}",
                    dimLog, safePos.getX(), safePos.getY(), safePos.getZ(),
                    shardKey.getCx(), shardKey.getCz(), shardKey.getSy(),
                    String.format("%.4f", oldScore), String.format("%.4f", newScore),
                    String.format("%.4f", delta), elapsedUs);
        }
    }

    // ========== Weight function (also used by ResultCache) ==========

    /**
     * Distance-based weight for L1 contribution.
     * Takes Euclidean distance squared (dx²+dz²+dy²) — no sqrt needed.
     * Shared with delta propagation in ResultCache.
     */
    static double weightForDistSq(double distSq) {
        return 1.0 / (1.0 + CivilConfig.distanceAlphaSq * distSq);
    }

    private static LevelChunk getChunkNow(ServerLevel world, int cx, int cz) {
        ServerChunkCache source = world.getChunkSource();
        return source.getChunkNow(cx, cz);
    }

    /**
     * DEBUG-only lightweight L1 availability counters.
     *
     * <p>All counting and logging are guarded by {@link CivilMod#DEBUG}, so release builds
     * with DEBUG=false do not execute this path.
     */
    private static void debugRecordL1Availability(String status) {
        if (!CivilMod.DEBUG) return;
        debugEnsureWindow();
        if ("known".equals(status)) {
            debugL1ComputeKnown++;
            return;
        }
        debugL1ComputeEmpty++;
        if ("invalid".equals(status)) {
            debugL1EmptyInvalid++;
        } else if ("no_chunk_now".equals(status)) {
            debugL1EmptyNoChunkNow++;
        }
    }

    private static void debugRecordL1Lookup(boolean cachedHit) {
        if (!CivilMod.DEBUG) return;
        debugEnsureWindow();
        debugL1LookupCalls++;
        if (cachedHit) {
            debugL1CachedHit++;
        }
    }

    private static void debugEnsureWindow() {
        long nowSec = ServerClock.now() / 1000L;
        if (debugL1WindowSec == -1L) {
            debugL1WindowSec = nowSec;
            return;
        }
        if (nowSec == debugL1WindowSec) {
            return;
        }
        debugFlushWindow();
        debugL1WindowSec = nowSec;
    }

    private static void debugFlushWindow() {
        double computeEmptyRatio = debugL1ComputeAttempt <= 0 ? 0.0
                : (double) debugL1ComputeEmpty / (double) debugL1ComputeAttempt;
        int effectiveDenominator = debugL1CachedHit + debugL1ComputeAttempt;
        double effectiveEmptyRatio = effectiveDenominator <= 0 ? 0.0
                : (double) debugL1ComputeEmpty / (double) effectiveDenominator;
        LOGGER.info(
                "[civil-l1-availability] sec={} lookup_calls={} cached_hit={} compute_attempt={} compute_known={} compute_empty={} empty_invalid={} empty_no_chunk_now={} compute_empty_ratio={} effective_empty_ratio={}",
                debugL1WindowSec,
                debugL1LookupCalls,
                debugL1CachedHit,
                debugL1ComputeAttempt,
                debugL1ComputeKnown,
                debugL1ComputeEmpty,
                debugL1EmptyInvalid,
                debugL1EmptyNoChunkNow,
                String.format("%.4f", computeEmptyRatio),
                String.format("%.4f", effectiveEmptyRatio)
        );
        debugL1LookupCalls = 0;
        debugL1CachedHit = 0;
        debugL1ComputeAttempt = 0;
        debugL1ComputeKnown = 0;
        debugL1ComputeEmpty = 0;
        debugL1EmptyInvalid = 0;
        debugL1EmptyNoChunkNow = 0;
    }
}
