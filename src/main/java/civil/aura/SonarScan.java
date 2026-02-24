package civil.aura;

import civil.CivilMod;
import civil.CivilServices;
import civil.civilization.CScore;
import civil.civilization.VoxelChunkKey;
import civil.config.CivilConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Directional BFS sonar scan engine for protection aura visualization.
 *
 * <p>The scan expands outward from the player's position one Manhattan-distance ring
 * per tick, detecting boundaries between HIGH and LOW civilization zones.
 *
 * <p>Two BFS modes determined by the player's current score:
 * <ul>
 *   <li><b>HIGH BFS</b> (player in protection zone): expands through HIGH chunks,
 *       finds the outer boundary. Answers "where does my protection end?"</li>
 *   <li><b>LOW BFS</b> (player in wilderness): expands through LOW chunks,
 *       finds the inner boundary of nearby zones. Answers "where is the nearest safe zone?"</li>
 * </ul>
 *
 * <p>Both modes terminate early — they only scan chunks on "their side" of the boundary,
 * improving performance over a full ring scan.
 */
public final class SonarScan {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-sonar");

    /**
     * Maximum BFS radius in voxel chunks (2D XZ Manhattan distance).
     * Dynamically linked to the user-configurable detection range so that the
     * protection aura visually matches the player's "detection distance" setting.
     */
    public static int MAX_RADIUS = 7;

    // 6 face-adjacent neighbor offsets: +X, -X, +Z, -Z, +Y, -Y
    private static final int[][] FACE_NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 0, 1}, {0, 0, -1},
            {0, 1, 0}, {0, -1, 0}
    };

    // Must match FACE_NEIGHBORS order: +X,-X (X axis), +sy,-sy (Y axis), +cz,-cz (Z axis)
    private static final Direction.Axis[] NEIGHBOR_AXES = {
            Direction.Axis.X, Direction.Axis.X,
            Direction.Axis.Y, Direction.Axis.Y,
            Direction.Axis.Z, Direction.Axis.Z
    };

    private static final boolean[] NEIGHBOR_POSITIVE = {
            true, false, true, false, true, false
    };

    // ========== Scan state ==========

    private final ServerLevel world;
    private final VoxelChunkKey center;
    private final boolean playerInHigh;
    private final double threshold;

    /** Current BFS ring distance (0 = center chunk, incremented each tick). */
    private int currentRing;

    /** Chunks at the current frontier, to be expanded next tick. */
    private List<VoxelChunkKey> frontier;

    /** All visited chunks with their HIGH/LOW classification. */
    private final Map<VoxelChunkKey, Boolean> visited;

    /** Discovered boundary faces (accumulated over the scan lifetime). */
    private final List<BoundaryFace> boundaries;

    /** Chunks that were scanned (expanded into) this tick — for scan wave particles. */
    private List<VoxelChunkKey> lastTickScanned;

    /** Boundary faces discovered this tick — for boundary particle spawning. */
    private List<BoundaryFace> lastTickBoundaries;

    /** Whether the scan has completed (frontier exhausted or max radius reached). */
    private boolean finished;

    /** Tick when the scan started (for boundary linger timing). */
    private final long startTick;

    // ========== Construction ==========

    /**
     * Create a new sonar scan centered on the given position.
     *
     * @param world     the server world
     * @param playerPos the player's block position at scan start
     * @param worldTick the current world tick (for linger timing)
     */
    public SonarScan(ServerLevel world, BlockPos playerPos, long worldTick) {
        this.world = world;
        this.center = VoxelChunkKey.from(playerPos);
        this.threshold = CivilConfig.spawnThresholdMid;
        this.startTick = worldTick;

        // Determine player's current score to decide BFS direction
        double playerScore = 0.0;
        try {
            CScore cScore = CivilServices.getCivilizationService().getCScoreAt(world, playerPos);
            playerScore = cScore.score();
        } catch (Exception e) {
            if (CivilMod.DEBUG) {
                LOGGER.warn("[civil-sonar] Failed to get player score: {}", e.getMessage());
            }
        }
        this.playerInHigh = playerScore >= threshold;

        // Initialize BFS
        this.visited = new HashMap<>();
        this.boundaries = new ArrayList<>();
        this.frontier = new ArrayList<>();
        this.lastTickScanned = new ArrayList<>();
        this.lastTickBoundaries = new ArrayList<>();
        this.finished = false;
        this.currentRing = 0;

        // Seed: center chunk
        visited.put(center, playerInHigh);
        frontier.add(center);
        lastTickScanned.add(center);

        if (CivilMod.DEBUG) {
            LOGGER.info("[civil-sonar] Scan started: center={} playerInHigh={} threshold={}",
                    center, playerInHigh, String.format("%.4f", threshold));
        }
    }

    // ========== Tick processing ==========

    /**
     * Advance the scan by one ring. Call once per server tick.
     *
     * <p>Processes all chunks at the current frontier distance, discovers neighbors,
     * and identifies boundary faces.
     *
     * @return true if the scan is still active, false if finished
     */
    public boolean tick() {
        if (finished) return false;

        List<VoxelChunkKey> nextFrontier = new ArrayList<>();
        List<VoxelChunkKey> scannedThisTick = new ArrayList<>();
        List<BoundaryFace> boundariesThisTick = new ArrayList<>();

        for (VoxelChunkKey current : frontier) {
            for (int i = 0; i < FACE_NEIGHBORS.length; i++) {
                // 2D scan: skip Y-axis expansion to keep uniform boundary across heights
                if (NEIGHBOR_AXES[i] == Direction.Axis.Y) continue;

                int dx = FACE_NEIGHBORS[i][0];
                int dz = FACE_NEIGHBORS[i][1];
                int dy = FACE_NEIGHBORS[i][2];
                VoxelChunkKey neighbor = current.offset(dx, dz, dy);

                // Skip already visited
                if (visited.containsKey(neighbor)) {
                    // Check if we missed a boundary between current and an already-visited
                    // chunk of the opposite type
                    Boolean neighborIsHigh = visited.get(neighbor);
                    Boolean currentIsHigh = visited.get(current);
                    if (currentIsHigh != null && neighborIsHigh != null
                            && !currentIsHigh.equals(neighborIsHigh)) {
                        // Boundary between current and neighbor — but only record if not
                        // already captured (deduplicate by checking both orderings)
                        BoundaryFace face = makeBoundaryFace(
                                currentIsHigh ? current : neighbor,
                                currentIsHigh ? neighbor : current,
                                NEIGHBOR_AXES[i], NEIGHBOR_POSITIVE[i], currentIsHigh);
                        if (!boundaries.contains(face)) {
                            boundaries.add(face);
                            boundariesThisTick.add(face);
                        }
                    }
                    continue;
                }

                // Check Manhattan distance to center (simple axis-sum)
                int dist = Math.abs(neighbor.getCx() - center.getCx())
                         + Math.abs(neighbor.getCz() - center.getCz())
                         + Math.abs(neighbor.getSy() - center.getSy());
                if (dist > MAX_RADIUS) continue;

                // Skip out-of-dimension
                if (!neighbor.isValidIn(world)) continue;

                // Compute score for neighbor chunk
                boolean neighborIsHigh = isChunkHigh(neighbor);
                visited.put(neighbor, neighborIsHigh);

                if (neighborIsHigh == playerInHigh) {
                    // Same side as player → continue BFS expansion
                    nextFrontier.add(neighbor);
                    scannedThisTick.add(neighbor);
                } else {
                    // Opposite side → boundary detected!
                    VoxelChunkKey high = playerInHigh ? current : neighbor;
                    VoxelChunkKey low = playerInHigh ? neighbor : current;
                    BoundaryFace face = makeBoundaryFace(high, low,
                            NEIGHBOR_AXES[i], NEIGHBOR_POSITIVE[i], playerInHigh);
                    boundaries.add(face);
                    boundariesThisTick.add(face);
                }
            }
        }

        this.lastTickScanned = scannedThisTick;
        this.lastTickBoundaries = boundariesThisTick;
        this.frontier = nextFrontier;
        this.currentRing++;

        if (nextFrontier.isEmpty() || currentRing > MAX_RADIUS) {
            finished = true;
            if (CivilMod.DEBUG) {
                LOGGER.info("[civil-sonar] Scan finished: ring={} visited={} boundaries={}",
                        currentRing, visited.size(), boundaries.size());
            }
        }

        return !finished;
    }

    // ========== Helpers ==========

    /**
     * Check if a chunk's aggregated score qualifies as HIGH (≥ threshold).
     */
    private boolean isChunkHigh(VoxelChunkKey key) {
        try {
            // Use center block of the voxel chunk for score query
            BlockPos centerBlock = new BlockPos(
                    key.getCx() * 16 + 8,
                    key.getSy() * 16 + 8,
                    key.getCz() * 16 + 8);
            CScore score = CivilServices.getCivilizationService().getCScoreAt(world, centerBlock);
            return score.score() >= threshold;
        } catch (Exception e) {
            if (CivilMod.DEBUG) {
                LOGGER.warn("[civil-sonar] Score check failed for {}: {}", key, e.getMessage());
            }
            // Conservative: treat failures as same-side to avoid false boundaries
            return playerInHigh;
        }
    }

    /**
     * Create a BoundaryFace record with correct orientation.
     */
    private static BoundaryFace makeBoundaryFace(
            VoxelChunkKey high, VoxelChunkKey low,
            Direction.Axis axis, boolean positiveNeighbor,
            boolean currentIsPlayerSide) {
        // positiveDirection means: lowSide is in the positive direction from highSide
        boolean positiveDirection;
        if (currentIsPlayerSide) {
            // current is playerSide, neighbor is opposite
            // if playerInHigh: current=high, neighbor=low
            // positiveDirection = is low in the positive direction? = positiveNeighbor
            positiveDirection = positiveNeighbor;
        } else {
            // current is opposite, neighbor is playerSide — flip
            positiveDirection = !positiveNeighbor;
        }
        return new BoundaryFace(high, low, axis, positiveDirection);
    }

    // ========== Accessors ==========

    public ServerLevel getWorld() {
        return world;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean isPlayerInHigh() {
        return playerInHigh;
    }

    public VoxelChunkKey getCenter() {
        return center;
    }

    public int getCurrentRing() {
        return currentRing;
    }

    public long getStartTick() {
        return startTick;
    }

    /** Chunks scanned (expanded into) during the last tick — for scan wave particles. */
    public List<VoxelChunkKey> getLastTickScanned() {
        return lastTickScanned;
    }

    /** Boundary faces discovered during the last tick — for boundary particle spawning. */
    public List<BoundaryFace> getLastTickBoundaries() {
        return lastTickBoundaries;
    }

    /** All boundary faces discovered so far — for lingering boundary particles. */
    public List<BoundaryFace> getAllBoundaries() {
        return boundaries;
    }

    /** Visited chunk classification map (VC → isHigh). Used for zone-aware sonar particles. */
    public Map<VoxelChunkKey, Boolean> getVisited() {
        return visited;
    }
}
