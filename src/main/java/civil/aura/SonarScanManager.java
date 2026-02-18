package civil.aura;

import civil.CivilMod;
import civil.CivilServices;
import civil.ModSounds;
import civil.civilization.HeadTracker;
import civil.civilization.VoxelChunkKey;
import civil.registry.HeadTypeRegistry;
import civil.config.CivilConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active sonar scans for all players.
 *
 * <p>Each player can have at most one active scan. Using the detector while a scan
 * is in progress replaces the existing scan with a new one.
 *
 * <p>Lifecycle: BFS expansion (multi-tick) → send boundary packet → remove session.
 * All visualization is handled client-side by {@link AuraWallRenderer}; the server
 * only computes boundaries and transmits them.
 */
public final class SonarScanManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-sonar");

    /** Wall extends this many blocks above and below the scan center Y. */
    private static final double WALL_HALF_HEIGHT = 48.0;

    /** Vertical padding (blocks) above and below a head's voxel chunk for the zone envelope. */
    private static final double HEAD_ZONE_Y_PADDING = 8.0;

    /**
     * Server ticks after scan start before the charge-up sound plays.
     * 3 ticks = 150ms — enough separation from the detector's click sound so both
     * are clearly audible, while still feeling like an immediate response.
     */
    private static final int CHARGE_DELAY_TICKS = 3;

    /**
     * Server ticks to delay the sonar boom sound after the boundary packet is sent.
     * 5 ticks = 0.25s at 20 TPS, matching the client-side charge-up + pause phase
     * before the ring starts expanding.
     */
    private static final int BOOM_DELAY_TICKS = 5;

    /** Active scans keyed by player UUID. */
    private static final Map<UUID, ScanSession> ACTIVE_SCANS = new ConcurrentHashMap<>();

    /** Pending boom sounds: player UUID → ticks remaining until playback. */
    private static final Map<UUID, PendingBoom> PENDING_BOOMS = new ConcurrentHashMap<>();

    private SonarScanManager() {}

    /**
     * Register the tick handler. Called once during mod initialization.
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Tick active BFS scans
            if (!ACTIVE_SCANS.isEmpty()) {
                ACTIVE_SCANS.entrySet().removeIf(entry -> {
                    ScanSession session = entry.getValue();
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

                    // Remove if player disconnected
                    if (player == null) return true;

                    // Advance scan; remove session when done
                    ServerWorld world = session.scan.getWorld();
                    return tickSession(session, world, player);
                });
            }

            // Tick pending boom sounds (delayed sonar burst)
            if (!PENDING_BOOMS.isEmpty()) {
                PENDING_BOOMS.entrySet().removeIf(entry -> {
                    PendingBoom boom = entry.getValue();
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player == null) return true; // Player disconnected

                    if (--boom.ticksRemaining <= 0) {
                        // Fire the boom sound at the player's current position
                        SoundEvent boomSound = ModSounds.getSonarBoomSound();
                        if (boomSound != null) {
                            boom.world.playSound(null,
                                    player.getX(), player.getY(), player.getZ(),
                                    boomSound, SoundCategory.PLAYERS,
                                    ModSounds.SONAR_BOOM_VOLUME, ModSounds.SONAR_BOOM_PITCH);
                        }
                        return true; // Remove after playing
                    }
                    return false; // Still counting down
                });
            }
        });
    }

    /**
     * Start a new sonar scan for the given player.
     * Replaces any existing active scan.
     *
     * @param player      the server player
     * @param serverWorld the server world (passed from use() context)
     */
    public static void startScan(ServerPlayerEntity player, ServerWorld serverWorld) {
        // Sync BFS radius with user-configurable detection range (may change at runtime)
        SonarScan.MAX_RADIUS = Math.max(CivilConfig.detectionRadiusX, CivilConfig.detectionRadiusZ);

        long worldTick = serverWorld.getTime();
        SonarScan scan = new SonarScan(serverWorld, player.getBlockPos(), worldTick);

        // Pre-compute whether the player is in a head zone (for charge-up particle type).
        // Cheap O(N) check against HeadTracker where N is typically 10–100 heads.
        boolean playerInHeadZone = isPlayerInHeadZone(player, serverWorld);

        ScanSession session = new ScanSession(scan, SonarScan.MAX_RADIUS, playerInHeadZone);
        ACTIVE_SCANS.put(player.getUuid(), session);

        if (CivilMod.DEBUG) {
            LOGGER.info("[civil-sonar] Started scan for player {} (inHigh={}, inHeadZone={}, maxRadius={})",
                    player.getName().getString(), scan.isPlayerInHigh(), playerInHeadZone, SonarScan.MAX_RADIUS);
        }
    }

    /**
     * Check if the player is within any mob head's Force Allow neighborhood.
     * Uses VC-level XZ range checks and strict same-sy Y check, consistent
     * with the detector sound's {@code getHeadTypesNear(headRangeY=0)} and
     * the shockwave ring's particle zone lookup.
     */
    private static boolean isPlayerInHeadZone(ServerPlayerEntity player, ServerWorld world) {
        HeadTracker registry = CivilServices.getHeadTracker();
        if (registry == null || !registry.isInitialized()) return false;

        String dim = world.getRegistryKey().toString();
        var allHeads = registry.getHeadsInDimension(dim);
        if (allHeads.isEmpty()) return false;

        String dimId = world.getRegistryKey().getValue().toString();

        VoxelChunkKey playerVC = VoxelChunkKey.from(player.getBlockPos());
        int pcx = playerVC.getCx();
        int pcz = playerVC.getCz();
        int playerBlockY = player.getBlockPos().getY();
        int rangeCX = CivilConfig.headRangeX;
        int rangeCZ = CivilConfig.headRangeZ;

        int psy = Math.floorDiv(playerBlockY, 16);

        for (HeadTracker.HeadEntry head : allHeads) {
            if (!HeadTypeRegistry.isEnabled(head.skullType(), dimId)) continue;

            int hcx = head.x() >> 4;
            int hcz = head.z() >> 4;
            int hsy = Math.floorDiv(head.y(), 16);
            if (Math.abs(pcx - hcx) <= rangeCX
                    && Math.abs(pcz - hcz) <= rangeCZ
                    && psy == hsy) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tick a single scan session.
     *
     * <p>The shockwave always fires at tick {@code scanRadius} (= MAX_RADIUS at scan start),
     * making the "detector click → shockwave" delay consistent for any given config.
     * Larger detection ranges naturally produce longer charge-up periods, which feels
     * intuitive — scanning a bigger area takes more time.
     *
     * <p>BFS advances one ring per tick and is mathematically guaranteed to complete
     * within {@code scanRadius} ticks (since it can expand at most that many rings).
     * No burst-completion is needed, so there are zero performance spikes.
     *
     * @return true if the session should be removed
     */
    private static boolean tickSession(ScanSession session, ServerWorld world, ServerPlayerEntity player) {
        session.ticksElapsed++;
        SonarScan scan = session.scan;

        // Play charge-up sound + send charge packet after a short delay
        // (lets the detector click breathe before the charge-up begins)
        if (!session.chargePlayed && session.ticksElapsed >= CHARGE_DELAY_TICKS) {
            session.chargePlayed = true;
            SoundEvent chargeSound = ModSounds.getSonarChargeSound();
            if (chargeSound != null) {
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        chargeSound, SoundCategory.PLAYERS,
                        ModSounds.SONAR_CHARGE_VOLUME, ModSounds.SONAR_CHARGE_PITCH);
            }
            // Send charge packet so the client starts charge-up particles in sync
            ServerPlayNetworking.send(player,
                    new SonarChargePayload(session.scan.isPlayerInHigh(), session.playerInHeadZone));
        }

        // Advance BFS one ring (if still running — small envelopes finish early)
        if (!scan.isFinished()) {
            scan.tick();
        }

        // Fire at the deadline: BFS is guaranteed done by tick scanRadius
        if (session.ticksElapsed >= session.scanRadius) {
            sendBoundaryPacket(player, scan);
            return true; // done — remove session
        }

        return false; // still charging
    }

    /**
     * Build and send the boundary payload to the player.
     *
     * <p>Head zone boundaries are computed first because the force-allow 2D footprint
     * is used to filter civilization faces: <b>gold walls must not appear inside or
     * on top of purple head zone walls</b> (purple has higher visual priority).
     */
    private static void sendBoundaryPacket(ServerPlayerEntity player, SonarScan scan) {
        // 1. Compute head zones first — we need the 2D footprint to filter civ faces.
        HeadZoneResult headResult = computeHeadZoneData(scan);

        // 2. Build civilization faces, excluding any that touch the force-allow footprint.
        //    A civ face is between two neighboring VCs; if EITHER VC's (cx, cz) falls inside
        //    the force-allow 2D footprint, the face is suppressed (purple takes priority).
        Set<Long> forceAllow2D = headResult.forceAllow2D;
        List<BoundaryFaceData> faces;
        if (forceAllow2D.isEmpty()) {
            faces = scan.getAllBoundaries().stream()
                    .map(BoundaryFaceData::fromBoundaryFace)
                    .toList();
        } else {
            faces = scan.getAllBoundaries().stream()
                    .filter(bf -> {
                        long highXZ = packXZ(bf.highSide().getCx(), bf.highSide().getCz());
                        long lowXZ  = packXZ(bf.lowSide().getCx(), bf.lowSide().getCz());
                        return !forceAllow2D.contains(highXZ) && !forceAllow2D.contains(lowXZ);
                    })
                    .map(BoundaryFaceData::fromBoundaryFace)
                    .toList();
        }

        double cx = scan.getCenter().getCx() * 16.0 + 8.0;
        double cy = scan.getCenter().getSy() * 16.0 + 8.0;
        double cz = scan.getCenter().getCz() * 16.0 + 8.0;

        double wallMinY = cy - WALL_HALF_HEIGHT;
        double wallMaxY = cy + WALL_HALF_HEIGHT;

        // Convert force-allow 2D footprint + Y ranges to parallel arrays for the payload.
        // The client uses this for sonar particle effects (FLAME in head zones with Y check).
        int headZoneSize = headResult.headZoneYRanges.size();
        long[] headZone2DArray = new long[headZoneSize];
        float[] headZoneMinYArray = new float[headZoneSize];
        float[] headZoneMaxYArray = new float[headZoneSize];
        int idx = 0;
        for (var entry : headResult.headZoneYRanges.entrySet()) {
            headZone2DArray[idx] = entry.getKey();
            headZoneMinYArray[idx] = entry.getValue()[0];
            headZoneMaxYArray[idx] = entry.getValue()[1];
            idx++;
        }

        // Build 2D (XZ) footprint of HIGH civilization VCs for position-based sonar
        // particle type selection: particles in HIGH VCs → END_ROD (gold), else → SOUL_FIRE_FLAME.
        //
        // Step 1: Collect HIGH VCs from the BFS visited map.
        Set<Long> civHigh2DSet = new HashSet<>();
        Set<Long> visitedXZ = new HashSet<>();
        for (var entry : scan.getVisited().entrySet()) {
            long packed = packXZ(entry.getKey().getCx(), entry.getKey().getCz());
            visitedXZ.add(packed);
            if (entry.getValue()) { // isHigh == true
                civHigh2DSet.add(packed);
            }
        }

        // Step 2: Fill unvisited VCs within scan range.
        // The BFS only expands on the player's side; opposite-side VCs beyond the first
        // boundary ring are unvisited. Without this fill, the sonar ring's outer edge
        // would incorrectly revert to SOUL_FIRE_FLAME when crossing into unscanned territory.
        // Heuristic: unvisited VCs within range are most likely the opposite of playerInHigh.
        VoxelChunkKey center = scan.getCenter();
        int maxR = SonarScan.MAX_RADIUS;
        for (int dx = -maxR; dx <= maxR; dx++) {
            for (int dz = -maxR; dz <= maxR; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > maxR) continue;
                long packed = packXZ(center.getCx() + dx, center.getCz() + dz);
                if (!visitedXZ.contains(packed)) {
                    // Unvisited VC within range → likely opposite of player's zone.
                    // Player in LOW → unvisited is likely HIGH civilization → add.
                    // Player in HIGH → unvisited is likely LOW/wilderness → don't add.
                    if (!scan.isPlayerInHigh()) {
                        civHigh2DSet.add(packed);
                    }
                }
            }
        }

        long[] civHighZone2DArray = new long[civHigh2DSet.size()];
        int cidx = 0;
        for (long v : civHigh2DSet) civHighZone2DArray[cidx++] = v;

        SonarBoundaryPayload payload = new SonarBoundaryPayload(
                scan.isPlayerInHigh(), cx, cy, cz, wallMinY, wallMaxY,
                faces, headResult.faces, headZone2DArray,
                headZoneMinYArray, headZoneMaxYArray, civHighZone2DArray);

        ServerPlayNetworking.send(player, payload);

        // === Boom sound (server-side, audible to all nearby players) ===
        // Delayed 5 ticks (0.25s) to match the client-side charge-up + pause phase
        // before the ring starts expanding. The charge-up sound already played at scan
        // start (in startScan), so the player has been hearing "charging" for the entire
        // BFS duration. This boom marks the dramatic release.
        PENDING_BOOMS.put(player.getUuid(), new PendingBoom(scan.getWorld(), BOOM_DELAY_TICKS));

        if (CivilMod.DEBUG) {
            LOGGER.info("[civil-sonar] Sent boundary packet to {}: {} civ faces, {} head faces, wall Y=[{}, {}]",
                    player.getName().getString(), faces.size(), headResult.faces.size(),
                    String.format("%.0f", wallMinY), String.format("%.0f", wallMaxY));
        }
    }

    // ========== Head zone boundary computation ==========

    /**
     * Lightweight voxel chunk coordinate triple for set operations.
     * Only used within head zone boundary computation.
     */
    private record VC3(int cx, int cz, int sy) {}

    /**
     * Result of head zone boundary computation.
     *
     * @param faces        boundary faces for client-side rendering
     * @param forceAllow2D 2D (XZ) footprint of all force-allow VCs, packed as
     *                     {@link #packXZ(int, int)}. Used to filter civilization faces
     *                     that overlap with head zones (purple takes priority over gold).
     * @param headZoneYRanges per (cx,cz) Y range: packed long key → float[]{minY, maxY}.
     *                        Used for sonar particle Y-aware head zone checks.
     */
    private record HeadZoneResult(List<HeadZoneFaceData> faces, Set<Long> forceAllow2D,
                                  Map<Long, float[]> headZoneYRanges) {
        static final HeadZoneResult EMPTY = new HeadZoneResult(List.of(), Set.of(), Map.of());
    }

    /**
     * Pack two ints (cx, cz) into a single long for fast set operations.
     */
    private static long packXZ(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    /**
     * Compute head zone boundary faces AND the 2D force-allow footprint.
     *
     * <p>The 2D footprint is the XZ projection of all force-allow VCs (ignoring Y),
     * used by {@link #sendBoundaryPacket} to suppress gold civilization faces that
     * fall inside or on the edge of a head zone.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Collect all heads in the current dimension from {@link HeadTracker}.</li>
     *   <li>For each head within range, expand its 3×3×1 VC force-allow zone.</li>
     *   <li>Build a set of all force-allow VCs (union of all head zones).</li>
     *   <li>Project to 2D for civilization face filtering.</li>
     *   <li>For each force-allow VC, check its 4 XZ neighbors. If a neighbor is NOT
     *       in the force-allow set, create a boundary face.</li>
     *   <li>Each face carries per-face vertical extent: head VC height (16 blocks)
     *       + {@link #HEAD_ZONE_Y_PADDING} blocks padding above and below.</li>
     * </ol>
     *
     * @return head zone faces + 2D footprint; {@link HeadZoneResult#EMPTY} if no heads in range
     */
    private static HeadZoneResult computeHeadZoneData(SonarScan scan) {
        ServerWorld world = scan.getWorld();
        String dim = world.getRegistryKey().toString();
        HeadTracker registry = CivilServices.getHeadTracker();
        if (registry == null || !registry.isInitialized()) return HeadZoneResult.EMPTY;

        var allHeads = registry.getHeadsInDimension(dim);
        if (allHeads.isEmpty()) return HeadZoneResult.EMPTY;

        String dimId = world.getRegistryKey().getValue().toString();

        VoxelChunkKey center = scan.getCenter();
        int maxRange = SonarScan.MAX_RADIUS + 2; // Include heads slightly beyond scan range

        int rangeCX = CivilConfig.headRangeX; // typically 1
        int rangeCZ = CivilConfig.headRangeZ; // typically 1

        // Build set of all force-allow VCs from nearby heads
        Set<VC3> forceAllowVCs = new HashSet<>();

        for (HeadTracker.HeadEntry head : allHeads) {
            if (!HeadTypeRegistry.isEnabled(head.skullType(), dimId)) continue;

            int hcx = head.x() >> 4;
            int hcz = head.z() >> 4;
            int hsy = Math.floorDiv(head.y(), 16);

            // Skip heads too far from scan center (Manhattan distance check)
            if (Math.abs(hcx - center.getCx()) > maxRange + rangeCX
                    || Math.abs(hcz - center.getCz()) > maxRange + rangeCZ) {
                continue;
            }

            // Expand 3×3×1 neighborhood (headRangeX × headRangeZ, headRangeY=0)
            for (int dx = -rangeCX; dx <= rangeCX; dx++) {
                for (int dz = -rangeCZ; dz <= rangeCZ; dz++) {
                    forceAllowVCs.add(new VC3(hcx + dx, hcz + dz, hsy));
                }
            }
        }

        if (forceAllowVCs.isEmpty()) return HeadZoneResult.EMPTY;

        // Build 2D (XZ) footprint for civilization face filtering,
        // and per-(cx,cz) Y ranges for sonar particle head zone checks.
        Set<Long> forceAllow2D = new HashSet<>();
        Map<Long, float[]> headZoneYRanges = new HashMap<>();
        for (VC3 vc : forceAllowVCs) {
            long packedXZ = packXZ(vc.cx, vc.cz);
            forceAllow2D.add(packedXZ);
            // Merge Y range: exact VC height [sy*16 .. (sy+1)*16), strict same-sy
            float vcMinY = vc.sy * 16.0f;
            float vcMaxY = (vc.sy + 1) * 16.0f;
            float[] existing = headZoneYRanges.get(packedXZ);
            if (existing == null) {
                headZoneYRanges.put(packedXZ, new float[]{vcMinY, vcMaxY});
            } else {
                // Multiple heads at same XZ but different Y — expand range
                existing[0] = Math.min(existing[0], vcMinY);
                existing[1] = Math.max(existing[1], vcMaxY);
            }
        }

        // XZ neighbor offsets:     +X,      -X,       +Z,      -Z
        int[][] xzNeighbors =    {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        byte[]  neighborAxes =   {0,       0,       2,       2     }; // X, X, Z, Z
        boolean[] neighborPos =  {true,    false,   true,    false  };

        int filterRange = SonarScan.MAX_RADIUS + 1;

        List<HeadZoneFaceData> faces = new ArrayList<>();
        // Dedup key — prevents duplicate faces from overlapping zones
        Set<Long> dedup = new HashSet<>();

        for (VC3 vc : forceAllowVCs) {
            // Skip VCs too far from center for rendering
            if (Math.abs(vc.cx - center.getCx()) > filterRange
                    || Math.abs(vc.cz - center.getCz()) > filterRange) {
                continue;
            }

            for (int n = 0; n < 4; n++) {
                int ncx = vc.cx + xzNeighbors[n][0];
                int ncz = vc.cz + xzNeighbors[n][1];
                VC3 neighbor = new VC3(ncx, ncz, vc.sy);

                if (!forceAllowVCs.contains(neighbor)) {
                    // Boundary found — this face is on the edge of the force-allow zone.
                    byte axis = neighborAxes[n];
                    boolean positive = neighborPos[n];

                    double planeCoord;
                    double minU;
                    if (axis == 0) { // X axis
                        planeCoord = positive ? (vc.cx + 1) * 16.0 : vc.cx * 16.0;
                        minU = vc.cz * 16.0;
                    } else { // Z axis
                        planeCoord = positive ? (vc.cz + 1) * 16.0 : vc.cz * 16.0;
                        minU = vc.cx * 16.0;
                    }

                    // Per-face vertical extent: VC height + padding
                    double faceMinY = vc.sy * 16.0 - HEAD_ZONE_Y_PADDING;
                    double faceMaxY = (vc.sy + 1) * 16.0 + HEAD_ZONE_Y_PADDING;

                    // Dedup using combined hash of all face identity fields
                    long dedupKey = dedupHash(axis, planeCoord, minU, vc.sy);
                    if (dedup.add(dedupKey)) {
                        faces.add(new HeadZoneFaceData(axis, planeCoord, minU, positive,
                                faceMinY, faceMaxY));
                    }
                }
            }
        }

        if (CivilMod.DEBUG && !faces.isEmpty()) {
            LOGGER.info("[civil-sonar] Computed {} head zone boundary faces from {} force-allow VCs (2D footprint: {} cells)",
                    faces.size(), forceAllowVCs.size(), forceAllow2D.size());
        }

        return new HeadZoneResult(faces, forceAllow2D, headZoneYRanges);
    }

    /**
     * Combine face identity fields into a single long for fast dedup.
     * Encodes axis(2 bits) + sy(16 bits) + planeCoord hash + minU hash.
     */
    private static long dedupHash(byte axis, double planeCoord, double minU, int sy) {
        long a = axis;
        long p = Double.doubleToLongBits(planeCoord);
        long u = Double.doubleToLongBits(minU);
        long s = sy;
        return a ^ (p * 31) ^ (u * 997) ^ (s * 65537);
    }

    /**
     * Check if a player currently has an active scan.
     */
    public static boolean hasActiveScan(UUID playerId) {
        return ACTIVE_SCANS.containsKey(playerId);
    }

    /**
     * Internal session state wrapper.
     * Captures the scan radius at creation time so the deadline is stable even if
     * the global MAX_RADIUS config changes mid-scan.
     */
    private static final class ScanSession {
        final SonarScan scan;
        /** BFS radius snapshot — determines when the shockwave fires. */
        final int scanRadius;
        /** Whether the player is in a head zone (for charge-up particle type). */
        final boolean playerInHeadZone;
        int ticksElapsed;
        boolean chargePlayed;

        ScanSession(SonarScan scan, int scanRadius, boolean playerInHeadZone) {
            this.scan = scan;
            this.scanRadius = scanRadius;
            this.playerInHeadZone = playerInHeadZone;
            this.ticksElapsed = 0;
            this.chargePlayed = false;
        }
    }

    /**
     * Delayed boom sound state. Counts down server ticks until the boom plays.
     * Stores the world reference since the scan session is already removed by the time the boom fires.
     */
    private static final class PendingBoom {
        final ServerWorld world;
        int ticksRemaining;

        PendingBoom(ServerWorld world, int ticks) {
            this.world = world;
            this.ticksRemaining = ticks;
        }
    }
}
