package civil.civilization.scoring;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.config.CivilConfig;
import civil.civilization.ServerClock;
import civil.civilization.cache.ResultCache;
import civil.civilization.cache.ResultEntry;
import civil.civilization.cache.TtlCacheService;
import civil.civilization.VoxelChunkKey;
import civil.civilization.BlockScanner;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * Fusion Architecture civilization scoring service.
 *
 * <p>Two-layer cache system:
 * <ul>
 *   <li><b>L1 Info Shards</b>: per-VC raw score, palette-accelerated, H2-persisted</li>
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

    // (callCounter / LOG_SAMPLE_RATE removed — debug logging is now gated entirely by CivilMod.DEBUG)

    private final TtlCacheService cacheService;
    private final ResultCache resultCache;

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
    public double getScoreAt(ServerWorld world, BlockPos pos) {
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
    public CScore getCScoreAt(ServerWorld world, BlockPos pos) {
        long startTimeUs = CivilMod.DEBUG ? System.nanoTime() / 1000 : 0;

        VoxelChunkKey centerVC = VoxelChunkKey.from(pos);

        // O(1) result shard lookup (or lazy compute on miss)
        ResultEntry entry = resultCache.getOrCompute(world, centerVC, this::computeResultEntry);
        double score = entry.getEffectiveScore(ServerClock.now());

        if (CivilMod.DEBUG) {
            long elapsedUs = System.nanoTime() / 1000 - startTimeUs;
            String dimName = world.getRegistryKey().getValue().toString();
            long serverNow = ServerClock.now();
            double freshness = ResultEntry.computeDecayFactor(serverNow, entry.getPresenceTime());

            LOGGER.info("[civil-fusion-score] dim={} cx={} cz={} sy={} score={} raw={} freshness={} elapsed_us={}",
                    dimName, centerVC.getCx(), centerVC.getCz(), centerVC.getSy(),
                    String.format("%.4f", score),
                    String.format("%.4f", entry.getRawScore(serverNow)),
                    String.format("%.4f", freshness),
                    elapsedUs);
        }

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
    private ResultEntry computeResultEntry(ServerWorld world, VoxelChunkKey centerVC) {
        double coreSum = 0.0;
        double outerSum = 0.0;

        int rx = CivilConfig.detectionRadiusX;
        int rz = CivilConfig.detectionRadiusZ;
        int ry = CivilConfig.detectionRadiusY;

        var dim = world.getDimension();
        int dimMinY = dim.minY();
        int dimMaxY = dimMinY + dim.height() - 1;

        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                for (int dy = -ry; dy <= ry; dy++) {
                    VoxelChunkKey shard = centerVC.offset(dx, dz, dy);

                    // Skip out-of-dimension shards
                    if (!shard.isValidIn(world, dimMinY, dimMaxY)) continue;

                    // Get L1 score: hot cache hit or synchronous compute
                    double l1Score = getL1Score(world, shard);
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

        // Restore persisted presenceTime / lastRecoveryTime from H2 if available.
        // Without this, TTL eviction would permanently lose decay state.
        String dimKey = world.getRegistryKey().toString();
        long[] persisted = cacheService.getStorage().loadPresenceSync(dimKey, centerVC);
        if (persisted != null) {
            return new ResultEntry(coreSum, outerSum, persisted[0], persisted[1]);
        }
        return new ResultEntry(coreSum, outerSum, ServerClock.now());
    }

    // ========== L1 score access ==========

    /**
     * Get L1 score for a single shard.
     *
     * <p>Lookup chain: hot cache → H2 cold storage → palette recompute.
     * The cold storage check is a safety net for the rare case where a shard
     * has been TTL-evicted but the prefetcher hasn't refreshed it yet (~0.1ms).
     */
    private double getL1Score(ServerWorld world, VoxelChunkKey key) {
        // 1. Hot cache hit (typical case — prefetcher keeps entries alive)
        Optional<CScore> cached = cacheService.getChunkCScore(world, key);
        if (cached.isPresent()) {
            return cached.get().score();
        }

        // 2. H2 cold storage (TTL-evicted but persisted — sync single-row lookup)
        Double coldScore = cacheService.getStorage().loadL1Sync(
                world.getRegistryKey().toString(), key);
        if (coldScore != null) {
            CScore restored = new CScore(coldScore);
            cacheService.putChunkCScore(world, key, restored); // re-fill hot cache
            return coldScore;
        }

        // 3. Both miss — full palette + operator recompute
        return computeAndCacheL1(world, key).score();
    }

    // ========== L1 computation ==========

    /**
     * Compute L1 score and write to cache.
     * Uses palette pre-filter (ChunkSection.hasAny) to skip empty sections.
     */
    CScore computeAndCacheL1(ServerWorld world, VoxelChunkKey key) {
        if (!key.isValidIn(world)) {
            return new CScore(0.0);
        }
        // Guard: if the chunk isn't loaded, return 0.0 instead of force-loading it.
        // Any previously computed non-zero L1 would already be in H2 (caught by
        // getL1Score's cold-storage check), so reaching here means the chunk is
        // either brand-new or was empty — returning 0.0 is safe.
        if (!world.isChunkLoaded(key.getCx(), key.getCz())) {
            return new CScore(0.0);
        }
        long startUs = CivilMod.DEBUG ? System.nanoTime() / 1000 : 0;
        CScore cScore = computeCScoreForChunk(world, key);
        cacheService.putChunkCScore(world, key, cScore);

        if (CivilMod.DEBUG) {
            long elapsedUs = System.nanoTime() / 1000 - startUs;
            String dimLog = world.getRegistryKey().getValue().toString();
            LOGGER.info("[civil-fusion-compute] L1 dim={} key={},{},{} score={} elapsed_us={}",
                    dimLog, key.getCx(), key.getCz(), key.getSy(),
                    String.format("%.4f", cScore.score()), elapsedUs);
        }

        return cScore;
    }

    /**
     * Compute CScore for a single voxel chunk (inlined single-pass).
     *
     * <ol>
     *   <li>Palette pre-filter: {@code ChunkSection.hasAny(isTargetBlock)} (~1μs).
     *       Skips sections containing zero target blocks.</li>
     *   <li>Single 4096-block iteration: reads world block states directly and
     *       accumulates weights via {@link BlockScanner#getBlockWeight} (~100μs).</li>
     * </ol>
     *
     * <p>This replaces the old two-pass pipeline (VoxelRegion fill + operator scan).
     */
    private CScore computeCScoreForChunk(ServerWorld world, VoxelChunkKey key) {
        // Palette pre-filter: check if the section contains any target blocks
        try {
            Chunk chunk = world.getChunk(key.getCx(), key.getCz());
            int sectionIdx = chunk.getSectionIndex(key.getSy() * 16);
            if (sectionIdx >= 0 && sectionIdx < chunk.getSectionArray().length) {
                ChunkSection section = chunk.getSection(sectionIdx);
                if (!section.hasAny(BlockScanner::isTargetBlock)) {
                    return new CScore(0.0);
                }
            }
        } catch (Exception e) {
            // Fallback: if palette check fails, proceed with full scan
        }

        // Single-pass: iterate world blocks directly, accumulate weight
        VoxelChunkKey.WorldBounds bounds = key.getWorldBounds(world);
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();
        if (min.getY() > max.getY()) {
            return new CScore(0.0);
        }

        double totalWeight = 0.0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
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
            return new CScore(0.0);
        }
        double score = Math.min(1.0, totalWeight / CivilConfig.normalizationFactor);
        return new CScore(score);
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
    public void onCivilBlockChanged(ServerWorld world, BlockPos pos) {
        long startNs = CivilMod.DEBUG ? System.nanoTime() : 0;
        VoxelChunkKey shardKey = VoxelChunkKey.from(pos);
        String dim = world.getRegistryKey().toString();

        // 1. Get old L1 score (hot → cold → 0.0)
        // Must check H2 cold storage to avoid incorrect delta when L1 was TTL-evicted.
        double oldScore = cacheService.getChunkCScore(world, shardKey)
                .map(CScore::score)
                .orElseGet(() -> {
                    Double cold = cacheService.getStorage().loadL1Sync(dim, shardKey);
                    return cold != null ? cold : 0.0;
                });

        // 2. Palette recompute new L1 score
        CScore newCScore = computeCScoreForChunk(world, shardKey);
        double newScore = newCScore.score();

        // 3. Update L1 cache + H2
        cacheService.putChunkCScore(world, shardKey, newCScore);

        // 4. Calculate delta and propagate
        double delta = newScore - oldScore;
        if (Math.abs(delta) > 1e-10) {
            resultCache.propagateDelta(dim, shardKey, delta);
        }

        if (CivilMod.DEBUG) {
            long elapsedUs = (System.nanoTime() - startNs) / 1000;
            String dimLog = world.getRegistryKey().getValue().toString();
            LOGGER.info("[civil-block-change] dim={} x={} y={} z={} cx={} cz={} sy={} old={} new={} delta={} elapsed_us={}",
                    dimLog, pos.getX(), pos.getY(), pos.getZ(),
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
}
