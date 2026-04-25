package civil.aura;

import civil.CivilMod;
import civil.CivilServices;
import civil.ModSounds;
import civil.civilization.FarmShrineTracker;
import civil.civilization.VoxelChunkKey;
import civil.config.CivilConfig;
import civil.CivilPlatform;
import civil.registry.DimensionPolicyRegistry;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

    /** Vertical padding (blocks) above and below a shrine bypass voxel chunk for the zone envelope. */
    private static final double SHRINE_ZONE_Y_PADDING = 8.0;

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

    /** Per-player bell sonar cooldown: UUID → world tick when cooldown expires. */
    private static final Map<UUID, Long> BELL_COOLDOWNS = new ConcurrentHashMap<>();

    private SonarScanManager() {}

    /**
     * Shutdown: clear all in-memory state. Call when overworld unloads to avoid
     * orphaned scans/booms referencing the old ServerLevel.
     */
    public static void shutdown() {
        ACTIVE_SCANS.clear();
        PENDING_BOOMS.clear();
        BELL_COOLDOWNS.clear();
    }

    /**
     * Replicates vanilla {@code BellBlock.isProperHit()} so we only trigger the sonar
     * when the bell would actually ring. Prevents sonar from firing when the bell
     * is hit from a direction that can't produce a ring (e.g. vertical hit on a
     * floor bell, or axially aligned hit on a wall bell).
     *
     * @param state          the bell's BlockState
     * @param hitDirection   the face that was clicked
     * @param hitRelativeY   hit Y coordinate relative to the block position (0.0–1.0)
     * @return true if the bell would ring from this hit
     */
    public static boolean isBellProperHit(BlockState state, Direction hitDirection, double hitRelativeY) {
        if (hitDirection.getAxis() == Direction.Axis.Y) return false;
        if (hitRelativeY > 0.8125) return false;

        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BellAttachType attachment = state.getValue(BlockStateProperties.BELL_ATTACHMENT);

        return switch (attachment) {
            case FLOOR -> facing.getAxis() == hitDirection.getAxis();
            case SINGLE_WALL, DOUBLE_WALL -> facing.getAxis() != hitDirection.getAxis();
            case CEILING -> true;
        };
    }

    /**
     * Server tick handler — call from platform entry point's END_SERVER_TICK event.
     */
    public static void onServerTick(MinecraftServer server) {
        if (!ACTIVE_SCANS.isEmpty()) {
            ACTIVE_SCANS.entrySet().removeIf(entry -> {
                ScanSession session = entry.getValue();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

                if (player == null) return true;

                ServerLevel world = session.scan.getWorld();
                return tickSession(session, world, player);
            });
        }

        if (!PENDING_BOOMS.isEmpty()) {
            PENDING_BOOMS.entrySet().removeIf(entry -> {
                PendingBoom boom = entry.getValue();
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) return true;

                if (--boom.ticksRemaining <= 0) {
                    SoundEvent boomSound = ModSounds.getSonarBoomSound();
                    if (boomSound != null) {
                        boom.world.playSound(null,
                                boom.x, boom.y, boom.z,
                                boomSound, SoundSource.PLAYERS,
                                boom.type.boomVolume(), boom.type.boomPitch());
                    }
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Start a new sonar scan for the given player.
     * Replaces any existing active scan.
     *
     * @param player      the server player
     * @param serverWorld the server world (passed from use() context)
     * @param type        the sonar type (DETECTOR or STATIC)
     */
    public static void startScan(ServerPlayer player, ServerLevel serverWorld, SonarType type) {
        startScan(player, serverWorld, player.blockPosition(), type);
    }

    public static void startScan(ServerPlayer player, ServerLevel serverWorld,
                                 net.minecraft.core.BlockPos origin, SonarType type) {
        int scanRadius = type.getRadius();
        SonarScan.MAX_RADIUS = scanRadius;

        long worldTick = serverWorld.getGameTime();
        SonarScan scan = new SonarScan(serverWorld, origin, worldTick);

        boolean playerInShrineZone = isPositionInShrineZone(serverWorld, origin);

        double originX = origin.getX() + 0.5;
        double originY = origin.getY() + 0.5;
        double originZ = origin.getZ() + 0.5;

        ScanSession session = new ScanSession(scan, scanRadius, playerInShrineZone,
                originX, originY, originZ, type);
        ACTIVE_SCANS.put(player.getUUID(), session);

        if (CivilMod.DEBUG) {
            LOGGER.info("[civil-sonar] Started {} scan for player {} (inHigh={}, inShrineZone={}, radius={})",
                    type.name(), player.getName().getString(), scan.isPlayerInHigh(), playerInShrineZone, scanRadius);
        }
    }

    /**
     * Check if the bell sonar cooldown has expired for the given player.
     * If available, starts the cooldown timer.
     *
     * @return true if the bell sonar is available (cooldown expired)
     */
    public static boolean tryBellCooldown(ServerPlayer player, ServerLevel world) {
        long now = world.getGameTime();
        Long expiry = BELL_COOLDOWNS.get(player.getUUID());
        if (expiry != null && now < expiry) return false;
        BELL_COOLDOWNS.put(player.getUUID(), now + CivilConfig.sonarStaticCooldownTicks);
        return true;
    }

    private static boolean isPositionInShrineZone(ServerLevel world, net.minecraft.core.BlockPos pos) {
        if (!DimensionPolicyRegistry.policyFor(world).headMechanics()) {
            return false;
        }
        FarmShrineTracker tracker = CivilServices.getFarmShrineTracker();
        if (tracker == null || !tracker.isInitialized()) {
            return false;
        }
        String dim = world.dimension().identifier().toString();
        return tracker.isInsideAnyBypassBox(dim, pos);
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
    private static boolean tickSession(ScanSession session, ServerLevel world, ServerPlayer player) {
        session.ticksElapsed++;
        SonarScan scan = session.scan;

        // Play charge-up sound + send charge packet after a short delay
        // (lets the detector click breathe before the charge-up begins)
        if (!session.chargePlayed && session.ticksElapsed >= CHARGE_DELAY_TICKS) {
            session.chargePlayed = true;
            SonarType type = session.type;
            SoundEvent chargeSound = ModSounds.getSonarChargeSound();
            if (chargeSound != null) {
                world.playSound(null, session.originX, session.originY, session.originZ,
                        chargeSound, SoundSource.PLAYERS,
                        type.chargeVolume(), type.chargePitch());
            }
            CivilPlatform.sendToPlayer(player,
                    new SonarChargePayload(session.originX, session.originY, session.originZ,
                            session.scan.isPlayerInHigh(), session.playerInShrineZone, type.id()));
        }

        // Advance BFS one ring (if still running — small envelopes finish early)
        if (!scan.isFinished()) {
            scan.tick();
        }

        // Fire at the deadline: BFS is guaranteed done by tick scanRadius
        if (session.ticksElapsed >= session.scanRadius) {
            sendBoundaryPacket(player, scan, session.originX, session.originY, session.originZ, session.type);
            return true; // done — remove session
        }

        return false; // still charging
    }

    /**
     * Build and send the boundary payload to the player.
     *
     * <p>Shrine bypass boundaries are computed first because the bypass 2D footprint
     * is used to filter civilization faces: <b>gold walls must not appear inside or
     * on top of purple shrine bypass walls</b> (purple has higher visual priority).
     */
    private static void sendBoundaryPacket(ServerPlayer player, SonarScan scan,
                                           double originX, double originY, double originZ,
                                           SonarType sonarType) {
        ShrineZoneResult shrineResult = computeShrineZoneData(scan);

        Set<Long> shrineBypass2D = shrineResult.shrineBypass2D;
        List<BoundaryFaceData> faces;
        if (shrineBypass2D.isEmpty()) {
            faces = scan.getAllBoundaries().stream()
                    .map(BoundaryFaceData::fromBoundaryFace)
                    .toList();
        } else {
            faces = scan.getAllBoundaries().stream()
                    .filter(bf -> {
                        long highXZ = packXZ(bf.highSide().getCx(), bf.highSide().getCz());
                        long lowXZ  = packXZ(bf.lowSide().getCx(), bf.lowSide().getCz());
                        return !shrineBypass2D.contains(highXZ) && !shrineBypass2D.contains(lowXZ);
                    })
                    .map(BoundaryFaceData::fromBoundaryFace)
                    .toList();
        }

        double cx = scan.getCenter().getCx() * 16.0 + 8.0;
        double cy = scan.getCenter().getSy() * 16.0 + 8.0;
        double cz = scan.getCenter().getCz() * 16.0 + 8.0;

        double wallMinY = cy - WALL_HALF_HEIGHT;
        double wallMaxY = cy + WALL_HALF_HEIGHT;

        int shrineZoneSize = shrineResult.shrineZoneYRanges.size();
        long[] shrineZone2DArray = new long[shrineZoneSize];
        float[] shrineZoneMinYArray = new float[shrineZoneSize];
        float[] shrineZoneMaxYArray = new float[shrineZoneSize];
        int idx = 0;
        for (var entry : shrineResult.shrineZoneYRanges.entrySet()) {
            shrineZone2DArray[idx] = entry.getKey();
            shrineZoneMinYArray[idx] = entry.getValue()[0];
            shrineZoneMaxYArray[idx] = entry.getValue()[1];
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
                faces, shrineResult.faces, shrineZone2DArray,
                shrineZoneMinYArray, shrineZoneMaxYArray, civHighZone2DArray, sonarType.id());

        CivilPlatform.sendToPlayer(player, payload);

        PENDING_BOOMS.put(player.getUUID(), new PendingBoom(scan.getWorld(),
                originX, originY, originZ, BOOM_DELAY_TICKS, sonarType));

        if (CivilMod.DEBUG) {
            LOGGER.info("[civil-sonar] Sent boundary packet to {}: {} civ faces, {} shrine faces, wall Y=[{}, {}]",
                    player.getName().getString(), faces.size(), shrineResult.faces.size(),
                    String.format("%.0f", wallMinY), String.format("%.0f", wallMaxY));
        }
    }

    // ========== Shrine bypass boundary computation ==========

    private record VC3(int cx, int cz, int sy) {}

    private record ShrineZoneResult(List<ShrineFaceData> faces, Set<Long> shrineBypass2D,
                                    Map<Long, float[]> shrineZoneYRanges) {
        static final ShrineZoneResult EMPTY = new ShrineZoneResult(List.of(), Set.of(), Map.of());
    }

    private static long packXZ(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static ShrineZoneResult computeShrineZoneData(SonarScan scan) {
        ServerLevel world = scan.getWorld();
        if (!DimensionPolicyRegistry.policyFor(world).headMechanics()) {
            return ShrineZoneResult.EMPTY;
        }
        FarmShrineTracker tracker = CivilServices.getFarmShrineTracker();
        if (tracker == null || !tracker.isInitialized()) {
            return ShrineZoneResult.EMPTY;
        }

        String dim = world.dimension().identifier().toString();
        VoxelChunkKey center = scan.getCenter();
        int maxRange = SonarScan.MAX_RADIUS + 2;

        int rx = CivilConfig.farmShrineRangeX;
        int rz = CivilConfig.farmShrineRangeZ;
        int ry = CivilConfig.farmShrineRangeY;

        Set<VC3> bypassVCs = new HashSet<>();
        tracker.forEachActivatedShrine(dim, shrine -> {
            int avcx = shrine.x() >> 4;
            int avcz = shrine.z() >> 4;
            int avcy = Math.floorDiv(shrine.y(), 16);
            if (Math.abs(avcx - center.getCx()) > maxRange + rx
                    || Math.abs(avcz - center.getCz()) > maxRange + rz) {
                return;
            }
            for (int dx = -rx; dx <= rx; dx++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    for (int dy = -ry; dy <= ry; dy++) {
                        bypassVCs.add(new VC3(avcx + dx, avcz + dz, avcy + dy));
                    }
                }
            }
        });

        if (bypassVCs.isEmpty()) {
            return ShrineZoneResult.EMPTY;
        }

        Set<Long> shrineBypass2D = new HashSet<>();
        Map<Long, float[]> shrineZoneYRanges = new HashMap<>();
        for (VC3 vc : bypassVCs) {
            long packedXZ = packXZ(vc.cx, vc.cz);
            shrineBypass2D.add(packedXZ);
            float vcMinY = vc.sy * 16.0f;
            float vcMaxY = (vc.sy + 1) * 16.0f;
            float[] existing = shrineZoneYRanges.get(packedXZ);
            if (existing == null) {
                shrineZoneYRanges.put(packedXZ, new float[]{vcMinY, vcMaxY});
            } else {
                existing[0] = Math.min(existing[0], vcMinY);
                existing[1] = Math.max(existing[1], vcMaxY);
            }
        }

        int[][] xzNeighbors = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        byte[] neighborAxes = {0, 0, 2, 2};
        boolean[] neighborPos = {true, false, true, false};

        int filterRange = SonarScan.MAX_RADIUS + 1;

        List<ShrineFaceData> faces = new ArrayList<>();
        Set<Long> dedup = new HashSet<>();

        for (VC3 vc : bypassVCs) {
            if (Math.abs(vc.cx - center.getCx()) > filterRange
                    || Math.abs(vc.cz - center.getCz()) > filterRange) {
                continue;
            }

            for (int n = 0; n < 4; n++) {
                int ncx = vc.cx + xzNeighbors[n][0];
                int ncz = vc.cz + xzNeighbors[n][1];
                VC3 neighbor = new VC3(ncx, ncz, vc.sy);

                if (!bypassVCs.contains(neighbor)) {
                    byte axis = neighborAxes[n];
                    boolean positive = neighborPos[n];

                    double planeCoord;
                    double minU;
                    if (axis == 0) {
                        planeCoord = positive ? (vc.cx + 1) * 16.0 : vc.cx * 16.0;
                        minU = vc.cz * 16.0;
                    } else {
                        planeCoord = positive ? (vc.cz + 1) * 16.0 : vc.cz * 16.0;
                        minU = vc.cx * 16.0;
                    }

                    double faceMinY = vc.sy * 16.0 - SHRINE_ZONE_Y_PADDING;
                    double faceMaxY = (vc.sy + 1) * 16.0 + SHRINE_ZONE_Y_PADDING;

                    long dedupKey = dedupHash(axis, planeCoord, minU, vc.sy);
                    if (dedup.add(dedupKey)) {
                        faces.add(new ShrineFaceData(axis, planeCoord, minU, positive, faceMinY, faceMaxY));
                    }
                }
            }
        }

        if (CivilMod.DEBUG && !faces.isEmpty()) {
            LOGGER.info("[civil-sonar] Computed {} shrine bypass boundary faces from {} VCs (2D footprint: {} cells)",
                    faces.size(), bypassVCs.size(), shrineBypass2D.size());
        }

        return new ShrineZoneResult(faces, shrineBypass2D, shrineZoneYRanges);
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
        final int scanRadius;
        final boolean playerInShrineZone;
        final double originX;
        final double originY;
        final double originZ;
        final SonarType type;
        int ticksElapsed;
        boolean chargePlayed;

        ScanSession(SonarScan scan, int scanRadius, boolean playerInShrineZone,
                    double originX, double originY, double originZ, SonarType type) {
            this.scan = scan;
            this.scanRadius = scanRadius;
            this.playerInShrineZone = playerInShrineZone;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.type = type;
            this.ticksElapsed = 0;
            this.chargePlayed = false;
        }
    }

    /**
     * Delayed boom sound state. Counts down server ticks until the boom plays.
     * Stores the world reference since the scan session is already removed by the time the boom fires.
     */
    private static final class PendingBoom {
        final ServerLevel world;
        final double x;
        final double y;
        final double z;
        final SonarType type;
        int ticksRemaining;

        PendingBoom(ServerLevel world, double x, double y, double z, int ticks, SonarType type) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.ticksRemaining = ticks;
        }
    }
}
