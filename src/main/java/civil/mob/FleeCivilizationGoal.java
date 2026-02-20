package civil.mob;

import civil.CivilMod;
import civil.CivilServices;
import civil.civilization.HeadTracker;
import civil.civilization.HeadTracker.HeadEntry;
import civil.config.CivilConfig;
import civil.registry.HeadTypeRegistry;
import civil.registry.HeadTypeRegistry.HeadTypeEntry;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

/**
 * Civilization-aware flee goal for hostile mobs.
 *
 * <p>Two instances are injected per mob — one for each {@link Mode}:
 * <ul>
 *   <li><b>IDLE</b> (priority 5): replaces wandering when the mob has no target
 *       and is in a civilized area (score >= greenLine). Probabilistic in Zone 2,
 *       guaranteed in Zone 3.</li>
 *   <li><b>COMBAT_PANIC</b> (priority 1): interrupts combat when civilization
 *       pressure is extreme (score >= combatFleeThreshold). Clears the mob's
 *       attack target and causes a short panic burst.</li>
 * </ul>
 *
 * <p>All thresholds derive from {@link CivilConfig#spawnThresholdMid} (greenLine),
 * which is controlled by the Spawn Suppression strength slider. No hardcoded
 * thresholds are introduced.
 *
 * <p>Flee target selection uses a two-phase approach:
 * <ol>
 *   <li>VC-based head zone search: pure integer arithmetic, finds the nearest
 *       Force Allow zone within a 1-VC-expanded ring. Zero sqrt.</li>
 *   <li>8-direction gradient descent: samples civilization scores and picks
 *       the direction with the lowest value.</li>
 * </ol>
 */
public final class FleeCivilizationGoal extends Goal {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-mob");

    public enum Mode { IDLE, COMBAT_PANIC }

    private static final int REPATH_INTERVAL_TICKS = 60;

    private static final int[][] DIRECTIONS = {
            {0, -1}, {1, -1}, {1, 0}, {1, 1},
            {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };

    private final PathAwareEntity mob;
    private final Mode mode;

    private long nextEvalTick = -1;
    private int ticksFleeing;
    private int ticksSinceRepath;
    private BlockPos fleeTarget;
    private BlockPos startPos;
    private double startScore;
    private String stopReason;

    public FleeCivilizationGoal(PathAwareEntity mob, Mode mode) {
        this.mob = mob;
        this.mode = mode;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    // ────────────────────────────────────────────────────────────
    //  Goal lifecycle
    // ────────────────────────────────────────────────────────────

    @Override
    public boolean canStart() {
        if (!CivilConfig.mobFleeEnabled) return false;
        if (!(mob.getEntityWorld() instanceof ServerWorld world)) return false;
        if (world.getServer() == null) return false;

        long currentTick = world.getServer().getTicks();

        if (nextEvalTick < 0) {
            nextEvalTick = currentTick + mob.getRandom().nextBetween(5, 20);
            return false;
        }
        if (currentTick < nextEvalTick) return false;

        nextEvalTick = currentTick + CivilConfig.mobFleeCheckIntervalTicks
                + mob.getRandom().nextBetween(0, CivilConfig.mobFleeJitterTicks);

        if (mob.hasVehicle()) return false;

        boolean hasTarget = mob.getTarget() != null;
        if (mode == Mode.IDLE && hasTarget) return false;
        if (mode == Mode.COMBAT_PANIC && !hasTarget) return false;

        var civService = CivilServices.getCivilizationService();
        if (civService == null) return false;

        HeadTracker tracker = CivilServices.getHeadTracker();
        if (tracker != null && tracker.isInitialized()) {
            HeadTracker.HeadQuery hq = tracker.queryHeads(
                    world.getRegistryKey().toString(), mob.getBlockPos(),
                    CivilConfig.headRangeX, CivilConfig.headRangeZ, CivilConfig.headRangeY);
            if (hq.hasNearbyHeads()) return false;
        }

        double score = civService.getScoreAt(world, mob.getBlockPos());
        double greenLine = CivilConfig.spawnThresholdMid;
        if (score < greenLine) return false;

        double combatFleeThreshold = greenLine + (1.0 - greenLine) * CivilConfig.mobFleeCombatFleeRatio;

        if (mode == Mode.IDLE) {
            if (score < combatFleeThreshold) {
                double prob = (score - greenLine) / (combatFleeThreshold - greenLine);
                if (mob.getRandom().nextDouble() >= prob) return false;
            }
        } else {
            if (score < combatFleeThreshold) return false;
            double maxProb = 1.0 - greenLine;
            double denom = 1.0 - combatFleeThreshold;
            double prob = denom > 0 ? (score - combatFleeThreshold) / denom * maxProb : maxProb;
            if (mob.getRandom().nextDouble() >= prob) return false;
        }

        fleeTarget = findFleeTarget(world);
        return fleeTarget != null;
    }

    @Override
    public void start() {
        if (mode == Mode.COMBAT_PANIC) {
            mob.setTarget(null);
        }
        ticksFleeing = 0;
        ticksSinceRepath = 0;
        startPos = mob.getBlockPos();
        stopReason = "unknown";
        navigateToTarget();

        if (CivilMod.DEBUG) {
            ServerWorld world = (ServerWorld) mob.getEntityWorld();
            var svc = CivilServices.getCivilizationService();
            startScore = svc != null ? svc.getScoreAt(world, mob.getBlockPos()) : -1;
            LOGGER.info("[civil-mob] start mode={} mob={} pos=({},{},{}) score={} target=({},{},{})",
                    mode == Mode.IDLE ? "flee" : "panic",
                    mob.getType().getUntranslatedName(),
                    mob.getBlockX(), mob.getBlockY(), mob.getBlockZ(),
                    String.format("%.3f", startScore),
                    fleeTarget.getX(), fleeTarget.getY(), fleeTarget.getZ());
        }
    }

    @Override
    public boolean shouldContinue() {
        if (mode == Mode.IDLE && mob.getTarget() != null) { stopReason = "aggro"; return false; }
        if (mode == Mode.COMBAT_PANIC && ticksFleeing > CivilConfig.mobFleePanicDurationTicks) { stopReason = "panic_timeout"; return false; }

        if (!(mob.getEntityWorld() instanceof ServerWorld world)) { stopReason = "no_world"; return false; }

        var civService = CivilServices.getCivilizationService();
        if (civService == null) { stopReason = "no_service"; return false; }

        double score = civService.getScoreAt(world, mob.getBlockPos());
        if (score < CivilConfig.spawnThresholdMid) { stopReason = "safe"; return false; }

        HeadTracker tracker = CivilServices.getHeadTracker();
        if (tracker != null && tracker.isInitialized()) {
            HeadTracker.HeadQuery hq = tracker.queryHeads(
                    world.getRegistryKey().toString(), mob.getBlockPos(),
                    CivilConfig.headRangeX, CivilConfig.headRangeZ, CivilConfig.headRangeY);
            if (hq.hasNearbyHeads()) { stopReason = "head_zone"; return false; }
        }

        if (mob.getNavigation().isIdle() && ticksFleeing > 20) { stopReason = "stuck"; return false; }
        if (mode == Mode.IDLE && ticksFleeing > CivilConfig.mobFleeMaxDurationTicks) { stopReason = "max_duration"; return false; }

        ticksSinceRepath++;
        if (ticksSinceRepath >= REPATH_INTERVAL_TICKS) {
            BlockPos newTarget = findFleeTarget(world);
            if (newTarget != null) {
                fleeTarget = newTarget;
                navigateToTarget();
                if (CivilMod.DEBUG) {
                    LOGGER.info("[civil-mob] repath mode={} mob={} pos=({},{},{}) score={} target=({},{},{})",
                            mode == Mode.IDLE ? "flee" : "panic",
                            mob.getType().getUntranslatedName(),
                            mob.getBlockX(), mob.getBlockY(), mob.getBlockZ(),
                            String.format("%.3f", score),
                            fleeTarget.getX(), fleeTarget.getY(), fleeTarget.getZ());
                }
            }
            ticksSinceRepath = 0;
        }

        ticksFleeing++;
        return true;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        if (CivilMod.DEBUG) {
            double endScore = -1;
            if (mob.getEntityWorld() instanceof ServerWorld world) {
                var svc = CivilServices.getCivilizationService();
                if (svc != null) endScore = svc.getScoreAt(world, mob.getBlockPos());
            }
            LOGGER.info("[civil-mob] stop mode={} mob={} pos=({},{},{}) score={} ticks={} reason={} start=({},{},{})",
                    mode == Mode.IDLE ? "flee" : "panic",
                    mob.getType().getUntranslatedName(),
                    mob.getBlockX(), mob.getBlockY(), mob.getBlockZ(),
                    String.format("%.3f", endScore),
                    ticksFleeing, stopReason,
                    startPos != null ? startPos.getX() : 0,
                    startPos != null ? startPos.getY() : 0,
                    startPos != null ? startPos.getZ() : 0);
        }
        fleeTarget = null;
        startPos = null;
    }

    // ────────────────────────────────────────────────────────────
    //  Target selection
    // ────────────────────────────────────────────────────────────

    private BlockPos findFleeTarget(ServerWorld world) {
        BlockPos pos = mob.getBlockPos();
        int mobX = pos.getX();
        int mobY = pos.getY();
        int mobZ = pos.getZ();

        BlockPos headTarget = findHeadZoneTarget(world, mobX, mobY, mobZ);
        if (headTarget != null) return headTarget;

        return findGradientTarget(world, pos, mobX, mobY, mobZ);
    }

    /**
     * Phase 1: VC-based head zone search.
     * Finds the nearest Force Allow zone within the search ring (ForceAllow + 1 VC).
     * Pure integer arithmetic, zero sqrt.
     */
    private BlockPos findHeadZoneTarget(ServerWorld world, int mobX, int mobY, int mobZ) {
        HeadTracker tracker = CivilServices.getHeadTracker();
        if (tracker == null || !tracker.isInitialized()) return null;

        String dim = world.getRegistryKey().toString();
        String dimId = world.getRegistryKey().getValue().toString();

        int mobCx = mobX >> 4;
        int mobCz = mobZ >> 4;
        int mobSy = Math.floorDiv(mobY, 16);

        int searchRangeX = CivilConfig.headRangeX + 1;
        int searchRangeZ = CivilConfig.headRangeZ + 1;
        int searchRangeY = CivilConfig.headRangeY + 1;

        BlockPos bestTarget = null;
        int bestVcDist = Integer.MAX_VALUE;

        for (HeadEntry head : tracker.getHeadsInDimension(dim)) {
            HeadTypeEntry entry = HeadTypeRegistry.get(head.skullType());
            if (entry == null || !entry.enabled() || !entry.isActiveIn(dimId)) continue;

            int headCx = head.x() >> 4;
            int headCz = head.z() >> 4;
            int headSy = Math.floorDiv(head.y(), 16);

            int dcx = Math.abs(mobCx - headCx);
            int dcz = Math.abs(mobCz - headCz);
            int dsy = Math.abs(mobSy - headSy);

            if (dcx <= searchRangeX && dcz <= searchRangeZ && dsy <= searchRangeY) {
                int vcDist = dcx + dcz + dsy;
                if (vcDist < bestVcDist) {
                    bestVcDist = vcDist;

                    int zoneMinX = (headCx - CivilConfig.headRangeX) * 16;
                    int zoneMaxX = (headCx + CivilConfig.headRangeX) * 16 + 15;
                    int zoneMinZ = (headCz - CivilConfig.headRangeZ) * 16;
                    int zoneMaxZ = (headCz + CivilConfig.headRangeZ) * 16 + 15;
                    int zoneMinY = (headSy - CivilConfig.headRangeY) * 16;
                    int zoneMaxY = (headSy + CivilConfig.headRangeY) * 16 + 15;

                    int tx = Math.max(zoneMinX, Math.min(zoneMaxX, mobX));
                    int tz = Math.max(zoneMinZ, Math.min(zoneMaxZ, mobZ));
                    int ty = Math.max(zoneMinY, Math.min(zoneMaxY, mobY));

                    bestTarget = new BlockPos(tx, ty, tz);
                }
            }
        }

        return bestTarget;
    }

    /**
     * Phase 2: 8-direction gradient descent.
     * Samples civilization scores in 8 compass directions and picks the lowest.
     * Falls back to a random direction if all directions are equally civilized.
     */
    private BlockPos findGradientTarget(ServerWorld world, BlockPos pos, int mobX, int mobY, int mobZ) {
        var civService = CivilServices.getCivilizationService();
        if (civService == null) return null;

        int sampleDist = CivilConfig.mobFleeSampleDistance;
        double currentScore = civService.getScoreAt(world, pos);
        BlockPos bestTarget = null;
        double bestScore = currentScore;

        for (int[] d : DIRECTIONS) {
            int cx = mobX + d[0] * sampleDist;
            int cz = mobZ + d[1] * sampleDist;
            BlockPos candidate = new BlockPos(cx, mobY, cz);
            double candidateScore = civService.getScoreAt(world, candidate);
            if (candidateScore < bestScore) {
                bestScore = candidateScore;
                bestTarget = candidate;
            }
        }

        if (bestTarget == null) {
            int dx = mob.getRandom().nextBetween(-sampleDist, sampleDist);
            int dz = mob.getRandom().nextBetween(-sampleDist, sampleDist);
            if (dx == 0 && dz == 0) dx = sampleDist;
            bestTarget = new BlockPos(mobX + dx, mobY, mobZ + dz);
        }

        return bestTarget;
    }

    private void navigateToTarget() {
        if (fleeTarget != null) {
            mob.getNavigation().startMovingTo(
                    fleeTarget.getX() + 0.5, fleeTarget.getY(), fleeTarget.getZ() + 0.5,
                    CivilConfig.mobFleeSpeed);
        }
    }
}
