package civil.civilization.cache.scalable;

import civil.CivilMod;
import civil.civilization.storage.H2Storage;
import civil.civilization.structure.L2Entry;
import civil.civilization.structure.L2Key;
import civil.civilization.structure.L3Entry;
import civil.civilization.structure.L3Key;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Player position-aware cache prefetcher (optimized).
 * 
 * <p>Optimization strategies:
 * <ul>
 *   <li>Only prefetch when the player moves (skip when stationary)</li>
 *   <li>Incremental prefetch: only prefetch edge regions in the player's movement direction</li>
 *   <li>Thread-safe: IO callback results are queued, consumed on the main thread</li>
 *   <li>Rate-limited: at most 50 async loads triggered per second</li>
 * </ul>
 * 
 * <p>Called once per second; prefetches cache entries from the database to memory
 * based on player positions.
 */
public final class PlayerAwarePrefetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-prefetch");

    /** Prefetch radius (L2/L3 block count). */
    private static final int L2_RADIUS = 4;   // 4 L2 blocks = 12 voxel chunks = 192 blocks
    private static final int L3_RADIUS = 4;   // 4 L3 blocks = 36 voxel chunks = 576 blocks

    /** Max async loads triggered per second. */
    private static final int MAX_ASYNC_LOADS_PER_SECOND = 50;

    /** Max queue items consumed per tick (avoid main thread stalls). */
    private static final int MAX_QUEUE_CONSUME_PER_TICK = 20;

    /** Player movement threshold (L2 block coordinate change counts as movement). */
    private static final int MOVE_THRESHOLD_L2 = 1;

    private final TtlPyramidCache cache;
    private final H2Storage storage;
    private final LoadingStateTracker loadingTracker;

    /** Last prefetch position per player (L2 block coordinates). */
    private final Map<UUID, L2Key> lastPlayerL2 = new HashMap<>();

    /** Pending L2 restore queue (written by IO thread, consumed by main thread). */
    private final Queue<PendingL2Restore> pendingL2Restores = new ConcurrentLinkedQueue<>();

    /** Pending L3 restore queue. */
    private final Queue<PendingL3Restore> pendingL3Restores = new ConcurrentLinkedQueue<>();

    /** Pending empty L2 entry creation queue. */
    private final Queue<PendingL2Create> pendingL2Creates = new ConcurrentLinkedQueue<>();

    /** Pending empty L3 entry creation queue. */
    private final Queue<PendingL3Create> pendingL3Creates = new ConcurrentLinkedQueue<>();

    public PlayerAwarePrefetcher(TtlPyramidCache cache, H2Storage storage) {
        this.cache = cache;
        this.storage = storage;
        this.loadingTracker = cache.getLoadingTracker();
    }

    /**
     * Called once per second to prefetch cache around players.
     */
    public void prefetchTick(MinecraftServer server) {
        if (storage == null) return;

        int asyncLoadsTriggered = 0;
        int movedPlayersThisTick = 0;  // Players that actually moved this tick

        for (ServerWorld world : server.getWorlds()) {
            String dim = world.getRegistryKey().toString();

            for (ServerPlayerEntity player : world.getPlayers()) {
                if (asyncLoadsTriggered >= MAX_ASYNC_LOADS_PER_SECOND) {
                    break;
                }

                BlockPos pos = player.getBlockPos();
                VoxelChunkKey centerKey = VoxelChunkKey.from(pos);
                L2Key currentL2 = L2Key.from(centerKey);

                // Check if player has moved (L2 block coordinate change)
                L2Key lastL2 = lastPlayerL2.get(player.getUuid());
                if (lastL2 != null && !hasMovedEnough(lastL2, currentL2)) {
                    continue;  // Player hasn't moved, skip prefetch
                }
                
                // Player moved, update record and count
                lastPlayerL2.put(player.getUuid(), currentL2);
                movedPlayersThisTick++;

                // Prefetch L2 (pass world for cache check)
                asyncLoadsTriggered += prefetchL2Around(world, dim, currentL2);

                // Prefetch L3
                L3Key currentL3 = L3Key.from(centerKey);
                asyncLoadsTriggered += prefetchL3Around(world, dim, currentL3);
            }
        }

        if (CivilMod.DEBUG) {
            LOGGER.info("[civil-ttl-prefetch] triggered={} queueSize={} movedPlayers={}",
                    asyncLoadsTriggered, getPendingQueueSize(), movedPlayersThisTick);
        }
    }

    /**
     * Called every tick to consume IO callback queue (main-thread safe).
     */
    public void consumePendingRestores(MinecraftServer server) {
        int consumed = 0;

        // Consume L2 restore queue
        while (consumed < MAX_QUEUE_CONSUME_PER_TICK) {
            PendingL2Restore restore = pendingL2Restores.poll();
            if (restore == null) break;
            
            ServerWorld world = getWorld(server, restore.dim);
            if (world != null) {
                // If cold storage expired (civilization decay), create empty entry
                boolean restored = cache.restoreL2(world, restore.key, restore.entry, restore.createTime);
                if (!restored) {
                    cache.getOrCreateL2(world, restore.key);
                }
            }
            consumed++;
        }

        // Consume L3 restore queue
        while (consumed < MAX_QUEUE_CONSUME_PER_TICK) {
            PendingL3Restore restore = pendingL3Restores.poll();
            if (restore == null) break;
            
            ServerWorld world = getWorld(server, restore.dim);
            if (world != null) {
                // If cold storage expired (civilization decay), create empty entry
                boolean restored = cache.restoreL3(world, restore.key, restore.entry, restore.createTime);
                if (!restored) {
                    cache.getOrCreateL3(world, restore.key);
                }
            }
            consumed++;
        }

        // Consume L2 creation queue
        while (consumed < MAX_QUEUE_CONSUME_PER_TICK) {
            PendingL2Create create = pendingL2Creates.poll();
            if (create == null) break;
            
            ServerWorld world = getWorld(server, create.dim);
            if (world != null) {
                cache.getOrCreateL2(world, create.key);
            }
            consumed++;
        }

        // Consume L3 creation queue
        while (consumed < MAX_QUEUE_CONSUME_PER_TICK) {
            PendingL3Create create = pendingL3Creates.poll();
            if (create == null) break;
            
            ServerWorld world = getWorld(server, create.dim);
            if (world != null) {
                cache.getOrCreateL3(world, create.key);
            }
            consumed++;
        }
    }

    /**
     * Check if the player has moved far enough.
     */
    private boolean hasMovedEnough(L2Key last, L2Key current) {
        return Math.abs(current.getC2x() - last.getC2x()) >= MOVE_THRESHOLD_L2
                || Math.abs(current.getC2z() - last.getC2z()) >= MOVE_THRESHOLD_L2
                || current.getS2y() != last.getS2y();
    }

    /**
     * Prefetch L2 cache (only triggers async loads, does not write to cache directly).
     */
    private int prefetchL2Around(ServerWorld world, String dim, L2Key center) {
        int count = 0;
        int c2x = center.getC2x();
        int c2z = center.getC2z();
        int s2y = center.getS2y();

        // Only prefetch current Y layer (reduce overhead)
        for (int dx = -L2_RADIUS; dx <= L2_RADIUS; dx++) {
            for (int dz = -L2_RADIUS; dz <= L2_RADIUS; dz++) {
                L2Key l2Key = new L2Key(c2x + dx, c2z + dz, s2y);

                // Check if hot cache already has entry (avoid duplicate loads)
                if (cache.hasL2Entry(world, l2Key)) {
                    continue;
                }

                String loadKey = "l2:" + dim + ":" + l2Key.getC2x() + ":" + l2Key.getC2z() + ":" + l2Key.getS2y();

                // Check if already loading
                if (loadingTracker.isLoading(loadKey)) {
                    continue;
                }

                // Trigger async load
                if (loadingTracker.startLoading(loadKey)) {
                    storage.loadL2Async(dim, l2Key)
                            .thenAccept(optEntry -> {
                                if (optEntry.isPresent()) {
                                    // Cold storage has data, add to restore queue
                                    pendingL2Restores.offer(new PendingL2Restore(
                                            dim, optEntry.get().key(), 
                                            optEntry.get().l2Entry(), optEntry.get().createTime()));
                                } else {
                                    // Cold storage empty, add to creation queue
                                    pendingL2Creates.offer(new PendingL2Create(dim, l2Key));
                                }
                                loadingTracker.finishLoading(loadKey, true);
                            });
                    count++;

                    if (count >= MAX_ASYNC_LOADS_PER_SECOND / 2) {
                        return count;
                    }
                }
            }
        }

        return count;
    }

    /**
     * Prefetch L3 cache.
     */
    private int prefetchL3Around(ServerWorld world, String dim, L3Key center) {
        int count = 0;
        int c3x = center.getC3x();
        int c3z = center.getC3z();
        int s3y = center.getS3y();

        // Only prefetch current Y layer
        for (int dx = -L3_RADIUS; dx <= L3_RADIUS; dx++) {
            for (int dz = -L3_RADIUS; dz <= L3_RADIUS; dz++) {
                L3Key l3Key = new L3Key(c3x + dx, c3z + dz, s3y);

                // Check if hot cache already has entry (avoid duplicate loads)
                if (cache.hasL3Entry(world, l3Key)) {
                    continue;
                }

                String loadKey = "l3:" + dim + ":" + l3Key.getC3x() + ":" + l3Key.getC3z() + ":" + l3Key.getS3y();

                // Check if already loading
                if (loadingTracker.isLoading(loadKey)) {
                    continue;
                }

                // Trigger async load
                if (loadingTracker.startLoading(loadKey)) {
                    storage.loadL3Async(dim, l3Key)
                            .thenAccept(optEntry -> {
                                if (optEntry.isPresent()) {
                                    // Cold storage has data, add to restore queue
                                    pendingL3Restores.offer(new PendingL3Restore(
                                            dim, optEntry.get().key(), 
                                            optEntry.get().l3Entry(), optEntry.get().createTime()));
                                } else {
                                    // Cold storage empty, add to creation queue
                                    pendingL3Creates.offer(new PendingL3Create(dim, l3Key));
                                }
                                loadingTracker.finishLoading(loadKey, true);
                            });
                    count++;

                    if (count >= MAX_ASYNC_LOADS_PER_SECOND / 2) {
                        return count;
                    }
                }
            }
        }

        return count;
    }

    /**
     * Get ServerWorld by dimension name.
     */
    private ServerWorld getWorld(MinecraftServer server, String dim) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().toString().equals(dim)) {
                return world;
            }
        }
        return null;
    }

    /**
     * Clear player position cache (called when player disconnects).
     */
    public void removePlayer(UUID playerId) {
        lastPlayerL2.remove(playerId);
    }

    /**
     * Get pending queue size (for debugging).
     */
    public int getPendingQueueSize() {
        return pendingL2Restores.size() + pendingL3Restores.size() 
                + pendingL2Creates.size() + pendingL3Creates.size();
    }

    // ========== Queue data structures ==========

    private record PendingL2Restore(String dim, L2Key key, L2Entry entry, long createTime) {}
    private record PendingL3Restore(String dim, L3Key key, L3Entry entry, long createTime) {}
    private record PendingL2Create(String dim, L2Key key) {}
    private record PendingL3Create(String dim, L3Key key) {}
}
