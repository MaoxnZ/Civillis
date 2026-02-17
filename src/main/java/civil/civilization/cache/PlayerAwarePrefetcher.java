package civil.civilization.cache;

import civil.CivilServices;
import civil.config.CivilConfig;
import civil.civilization.storage.H2Storage;
import civil.civilization.VoxelChunkKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fusion Architecture: player-aware cache keepalive + decay recovery.
 *
 * <p>Called once per second. Two responsibilities:
 * <ol>
 *   <li><b>L1 TTL refresh</b>: touch() L1 entries within
 *       (detectionRadius + SPAWN_RANGE_VC) to keep all shards that could
 *       contribute to any spawn-check result shard alive.</li>
 *   <li><b>Result shard visit</b>: advance presenceTime + refresh TTL
 *       on result entries within the player's patrol influence zone.</li>
 * </ol>
 *
 * <p>Radii are derived from config at call time so they adapt to
 * detection/patrol range changes without restart.
 *
 * <p>Smart move detection: if a player hasn't moved (same voxel chunk),
 * skip the full L1 touch sweep and only refresh result entries.
 */
public final class PlayerAwarePrefetcher {

    /**
     * Maximum mob spawn distance from player in voxel chunks.
     *
     * <p>Minecraft mobs spawn up to 128 blocks from the player (beyond which
     * they instantly despawn). 128 / 16 = 8 voxel chunks.
     *
     * <p>This constant drives two derived radii:
     * <ul>
     *   <li><b>L1 touch radius</b> = detectionRadius + SPAWN_RANGE_VC:
     *       the farthest L1 shard that any spawn-check result shard could need.
     *       A result shard at spawn position P reads L1 shards within detectionRadius of P,
     *       and P itself can be up to SPAWN_RANGE_VC from the player.
     *       Example: detectionRadiusX=7, so L1 touch = 7+8 = 15 VCs.</li>
     *   <li><b>Result visit radius</b> = SPAWN_RANGE_VC:
     *       covers all positions where mobs could spawn. Within this radius,
     *       presenceTime is advanced (preventing decay) and TTL is refreshed.
     *       This is the player's effective "patrol influence" zone.</li>
     * </ul>
     */
    private static final int SPAWN_RANGE_VC = 8;  // 128 blocks / 16

    private final TtlVoxelCache cache;

    /** Last known player position (for smart move detection). */
    private final Map<UUID, VoxelChunkKey> lastPlayerVC = new HashMap<>();

    public PlayerAwarePrefetcher(TtlVoxelCache cache, H2Storage storage) {
        this.cache = cache;
    }

    /**
     * Called once per second: keep L1 + result entries alive near players.
     *
     * <p>Performance per player (default config, detectionRadiusX/Z=7, SPAWN_RANGE_VC=8, patrolRadiusX/Z=4, Y=1):
     * <ul>
     *   <li>L1 touch: 31×31×19 = 18,259 HashMap lookups ≈ 0.9ms (only on VC change)</li>
     *   <li>Result visit: 9×9×3 = 243 HashMap lookups ≈ 0.01ms (every second)</li>
     * </ul>
     */
    public void prefetchTick(MinecraftServer server) {
        ResultCache resultCache = CivilServices.getResultCache();

        // L1 radius: detectionRadius + SPAWN_RANGE_VC
        // A mob spawns up to SPAWN_RANGE_VC from the player; its ResultEntry reads L1
        // within detectionRadius → farthest L1 from player = SPAWN_RANGE_VC + detection.
        // Keeping all these L1 entries alive ensures spawn checks never trigger cold reads.
        int l1RadiusX = CivilConfig.detectionRadiusX + SPAWN_RANGE_VC;
        int l1RadiusZ = CivilConfig.detectionRadiusZ + SPAWN_RANGE_VC;
        int l1RadiusY = CivilConfig.detectionRadiusY + SPAWN_RANGE_VC;

        // Result radius: player's patrol influence zone.
        // Within this range, presenceTime advances → civilization doesn't decay.
        // Outside this range, result shards eventually decay, encouraging exploration.
        int resultRadiusX = CivilConfig.patrolRadiusX;
        int resultRadiusZ = CivilConfig.patrolRadiusZ;
        int resultRadiusY = CivilConfig.patrolRadiusY;

        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                BlockPos pos = player.getBlockPos();
                VoxelChunkKey center = VoxelChunkKey.from(pos);
                UUID playerId = player.getUuid();

                VoxelChunkKey prev = lastPlayerVC.get(playerId);
                boolean moved = prev == null || !prev.equals(center);

                // 1. Touch L1 shards: full sweep only when player moved VC
                if (moved) {
                    touchL1Around(world, center, l1RadiusX, l1RadiusZ, l1RadiusY);
                }

                // 2. Visit result shards: always (for presenceTime advancement)
                if (resultCache != null) {
                    resultCache.visitAround(world, center, resultRadiusX, resultRadiusZ, resultRadiusY);
                }

                lastPlayerVC.put(playerId, center);
            }
        }
    }

    /**
     * Touch L1 cache entries around the player to refresh their TTL timers.
     *
     * <p>Without this, a stationary player would see their L1 entries expire after
     * 60 minutes, causing unnecessary H2 cold reads or palette recomputation.
     */
    private void touchL1Around(ServerWorld world, VoxelChunkKey center,
                               int radiusX, int radiusZ, int radiusY) {
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                for (int dy = -radiusY; dy <= radiusY; dy++) {
                    cache.touchL1(world, center.offset(dx, dz, dy));
                }
            }
        }
    }

    /**
     * Called every tick on the main thread. No-op in fusion architecture.
     */
    public void consumePendingRestores(MinecraftServer server) {
        // No-op: L2/L3 prefetch loading removed in fusion architecture
    }

    public void removePlayer(UUID playerId) {
        lastPlayerVC.remove(playerId);
    }

    public int getPendingQueueSize() {
        return 0;
    }
}
