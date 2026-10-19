package civil.mob;

import civil.CivilMod;
import civil.CivilServices;
import civil.civilization.FarmShrineTracker;
import civil.civilization.ZonePolicyService;
import civil.config.CivilConfig;
import civil.registry.DimensionPolicyRegistry;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
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
 *   <li>Nearest activated farm shrine anchor within attract radius (when not inside a bypass box).</li>
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

    private final PathfinderMob mob;
    private final Mode mode;

    private long nextEvalTick = -1;
    private int ticksFleeing;
    private int ticksSinceRepath;
    private BlockPos fleeTarget;
    private BlockPos startPos;
    private double startScore;
    private String stopReason;

    public FleeCivilizationGoal(PathfinderMob mob, Mode mode) {
        this.mob = mob;
        this.mode = mode;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    // ────────────────────────────────────────────────────────────
    //  Goal lifecycle
    // ────────────────────────────────────────────────────────────

    @Override
    public boolean canUse() {
        if (!CivilConfig.mobFleeEnabled) return false;
        if (!(mob.level() instanceof ServerLevel world)) return false;
        if (world.getServer() == null) return false;

        long currentTick = world.getServer().getTickCount();

        if (nextEvalTick < 0) {
            nextEvalTick = currentTick + mob.getRandom().nextIntBetweenInclusive(5, 20);
            return false;
        }
        if (currentTick < nextEvalTick) return false;

        nextEvalTick = currentTick + CivilConfig.mobFleeCheckIntervalTicks
                + mob.getRandom().nextIntBetweenInclusive(0, CivilConfig.mobFleeJitterTicks);

        if (mob.isPassenger()) return false;

        boolean hasTarget = mob.getTarget() != null;
        if (mode == Mode.IDLE && hasTarget) return false;
        if (mode == Mode.COMBAT_PANIC && !hasTarget) return false;

        var civService = CivilServices.getCivilizationService();
        if (civService == null) return false;

        if (DimensionPolicyRegistry.policyFor(world.dimension().identifier().toString()).headMechanics()) {
            FarmShrineTracker shrines = CivilServices.getFarmShrineTracker();
            if (shrines != null && shrines.isInitialized()) {
                String dim = world.dimension().identifier().toString();
                if (shrines.isInsideAnyBypassBox(dim, mob.blockPosition())) return false;
            }
        }

        ZonePolicyService zonePolicyService = CivilServices.getZonePolicyService();
        if (zonePolicyService != null && zonePolicyService.allowsHostileSpawn(world, mob.blockPosition(), mob.getType())) {
            return false;
        }

        double score = civService.getScoreAt(world, mob.blockPosition());
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

    private static final int PANIC_PARTICLE_COUNT = 10;
    private static final double PANIC_PARTICLE_SPREAD = 0.4;

    @Override
    public void start() {
        if (mode == Mode.COMBAT_PANIC) {
            mob.setTarget(null);
            if (mob.level() instanceof ServerLevel sw) {
                sw.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        mob.getX(), mob.getY(0.5), mob.getZ(),
                        PANIC_PARTICLE_COUNT,
                        PANIC_PARTICLE_SPREAD, PANIC_PARTICLE_SPREAD, PANIC_PARTICLE_SPREAD,
                        0.02);
            }
        }
        ticksFleeing = 0;
        ticksSinceRepath = 0;
        startPos = mob.blockPosition();
        stopReason = "unknown";
        navigateToTarget();

        if (CivilMod.DEBUG) {
            ServerLevel world = (ServerLevel) mob.level();
            var svc = CivilServices.getCivilizationService();
            startScore = svc != null ? svc.getScoreAt(world, mob.blockPosition()) : -1;
            LOGGER.info("[civil-mob] start mode={} mob={} pos=({},{},{}) score={} target=({},{},{})",
                    mode == Mode.IDLE ? "flee" : "panic",
                    mob.getType().toShortString(),
                    mob.getBlockX(), mob.getBlockY(), mob.getBlockZ(),
                    String.format("%.3f", startScore),
                    fleeTarget.getX(), fleeTarget.getY(), fleeTarget.getZ());
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (mode == Mode.IDLE && mob.getTarget() != null) { stopReason = "aggro"; return false; }
        if (mode == Mode.COMBAT_PANIC && ticksFleeing > CivilConfig.mobFleePanicDurationTicks) { stopReason = "panic_timeout"; return false; }

        if (!(mob.level() instanceof ServerLevel world)) { stopReason = "no_world"; return false; }

        var civService = CivilServices.getCivilizationService();
        if (civService == null) { stopReason = "no_service"; return false; }

        double score = civService.getScoreAt(world, mob.blockPosition());

        if (DimensionPolicyRegistry.policyFor(world.dimension().identifier().toString()).headMechanics()) {
            FarmShrineTracker shrines = CivilServices.getFarmShrineTracker();
            if (shrines != null && shrines.isInitialized()) {
                String dim = world.dimension().identifier().toString();
                if (shrines.isInsideAnyBypassBox(dim, mob.blockPosition())) {
                    stopReason = "shrine_bypass";
                    return false;
                }
            }
        }

        if (mob.getNavigation().isDone() && ticksFleeing > 20) { stopReason = "stuck"; return false; }
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
                            mob.getType().toShortString(),
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
            if (mob.level() instanceof ServerLevel world) {
                var svc = CivilServices.getCivilizationService();
                if (svc != null) endScore = svc.getScoreAt(world, mob.blockPosition());
            }
            LOGGER.info("[civil-mob] stop mode={} mob={} pos=({},{},{}) score={} ticks={} reason={} start=({},{},{})",
                    mode == Mode.IDLE ? "flee" : "panic",
                    mob.getType().toShortString(),
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

    private BlockPos findFleeTarget(ServerLevel world) {
        BlockPos pos = mob.blockPosition();
        int mobX = pos.getX();
        int mobY = pos.getY();
        int mobZ = pos.getZ();

        BlockPos shrineTarget = findShrineAnchorTarget(world, mobX, mobY, mobZ);
        if (shrineTarget != null) return shrineTarget;

        return findGradientTarget(world, pos, mobX, mobY, mobZ);
    }

    /** Phase 1: nearest activated shrine anchor within {@link CivilConfig#farmShrineAttractMaxRadius}. */
    private BlockPos findShrineAnchorTarget(ServerLevel world, int mobX, int mobY, int mobZ) {
        if (!DimensionPolicyRegistry.policyFor(world.dimension().identifier().toString()).headMechanics()) {
            return null;
        }
        FarmShrineTracker tracker = CivilServices.getFarmShrineTracker();
        if (tracker == null || !tracker.isInitialized()) return null;
        String dim = world.dimension().identifier().toString();
        return tracker.findNearestShrineAnchorWithinAttract(dim, new BlockPos(mobX, mobY, mobZ));
    }

    /**
     * Phase 2: 8-direction gradient descent.
     * Samples civilization scores in 8 compass directions and picks the lowest.
     * Falls back to a random direction if all directions are equally civilized.
     */
    private BlockPos findGradientTarget(ServerLevel world, BlockPos pos, int mobX, int mobY, int mobZ) {
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
            int dx = mob.getRandom().nextIntBetweenInclusive(-sampleDist, sampleDist);
            int dz = mob.getRandom().nextIntBetweenInclusive(-sampleDist, sampleDist);
            if (dx == 0 && dz == 0) dx = sampleDist;
            bestTarget = new BlockPos(mobX + dx, mobY, mobZ + dz);
        }

        return bestTarget;
    }

    private void navigateToTarget() {
        if (fleeTarget != null) {
            mob.getNavigation().moveTo(
                    fleeTarget.getX() + 0.5, fleeTarget.getY(), fleeTarget.getZ() + 0.5,
                    CivilConfig.mobFleeSpeed);
        }
    }
}
