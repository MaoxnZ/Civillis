package civil.civilization.cache;

import civil.CivilMod;
import civil.config.CivilConfig;
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

    // Prefetch parameters are in CivilConfig

    // Move threshold is in CivilConfig.prefetchMoveThreshold

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
     * Called once per second to prefetch cache and advance presenceTime around players.
     *
     * <p>Two passes per player:
     * <ol>
     *   <li><b>Visit pass</b> (all players, regardless of movement): call
     *       {@code onVisit()} on every L2/L3 entry in the prefetch radius that
     *       is already in hot cache. This is the sole mechanism for advancing
     *       presenceTime and recovering from decay — it ties recovery directly
     *       to player proximity rather than mob spawn attempts.</li>
     *   <li><b>Prefetch-load pass</b> (moved players only): trigger async loads
     *       from cold storage for entries not yet in hot cache.</li>
     * </ol>
     */
    public void prefetchTick(MinecraftServer server) {
        if (storage == null) return;

        int asyncLoadsTriggered = 0;
        int movedPlayersThisTick = 0;

        for (ServerWorld world : server.getWorlds()) {
            String dim = world.getRegistryKey().toString();

            for (ServerPlayerEntity player : world.getPlayers()) {
                BlockPos pos = player.getBlockPos();
                VoxelChunkKey centerKey = VoxelChunkKey.from(pos);
                L2Key currentL2 = L2Key.from(centerKey);
                L3Key currentL3 = L3Key.from(centerKey);

                // ── Visit pass: advance presenceTime for all nearby hot-cache entries ──
                // Runs for every online player every tick (cheap: HashMap lookups only).
                visitL2Around(world, currentL2);
                visitL3Around(world, currentL3);

                // ── Prefetch-load pass: only for players who moved ──
                if (asyncLoadsTriggered >= CivilConfig.maxAsyncLoadsPerSecond) {
                    continue;  // Still visit, but skip expensive loads
                }

                L2Key lastL2 = lastPlayerL2.get(player.getUuid());
                if (lastL2 != null && !hasMovedEnough(lastL2, currentL2)) {
                    continue;  // Player hasn't moved, skip prefetch loading
                }

                lastPlayerL2.put(player.getUuid(), currentL2);
                movedPlayersThisTick++;

                asyncLoadsTriggered += prefetchL2Around(world, dim, currentL2);
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
        while (consumed < CivilConfig.maxQueueConsumePerTick) {
            PendingL2Restore restore = pendingL2Restores.poll();
            if (restore == null) break;
            
            ServerWorld world = getWorld(server, restore.dim);
            if (world != null) {
                cache.restoreL2(world, restore.key, restore.entry, restore.createTime);
            }
            consumed++;
        }

        // Consume L3 restore queue
        while (consumed < CivilConfig.maxQueueConsumePerTick) {
            PendingL3Restore restore = pendingL3Restores.poll();
            if (restore == null) break;
            
            ServerWorld world = getWorld(server, restore.dim);
            if (world != null) {
                cache.restoreL3(world, restore.key, restore.entry, restore.createTime);
            }
            consumed++;
        }

        // Consume L2 creation queue
        while (consumed < CivilConfig.maxQueueConsumePerTick) {
            PendingL2Create create = pendingL2Creates.poll();
            if (create == null) break;
            
            ServerWorld world = getWorld(server, create.dim);
            if (world != null) {
                cache.getOrCreateL2(world, create.key);
            }
            consumed++;
        }

        // Consume L3 creation queue
        while (consumed < CivilConfig.maxQueueConsumePerTick) {
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
        return Math.abs(current.getC2x() - last.getC2x()) >= CivilConfig.prefetchMoveThreshold
                || Math.abs(current.getC2z() - last.getC2z()) >= CivilConfig.prefetchMoveThreshold
                || current.getS2y() != last.getS2y();
    }

    // ========== Presence visit (player-driven presenceTime recovery) ==========

    /**
     * Visit all L2 entries around the player's position that are in hot cache.
     * Advances presenceTime on each, enabling decay recovery purely from player proximity.
     */
    private void visitL2Around(ServerWorld world, L2Key center) {
        int c2x = center.getC2x();
        int c2z = center.getC2z();
        int s2y = center.getS2y();
        for (int dx = -CivilConfig.prefetchL2Radius; dx <= CivilConfig.prefetchL2Radius; dx++) {
            for (int dz = -CivilConfig.prefetchL2Radius; dz <= CivilConfig.prefetchL2Radius; dz++) {
                cache.visitL2(world, new L2Key(c2x + dx, c2z + dz, s2y));
            }
        }
    }

    /**
     * Visit all L3 entries around the player's position that are in hot cache.
     */
    private void visitL3Around(ServerWorld world, L3Key center) {
        int c3x = center.getC3x();
        int c3z = center.getC3z();
        int s3y = center.getS3y();
        for (int dx = -CivilConfig.prefetchL3Radius; dx <= CivilConfig.prefetchL3Radius; dx++) {
            for (int dz = -CivilConfig.prefetchL3Radius; dz <= CivilConfig.prefetchL3Radius; dz++) {
                cache.visitL3(world, new L3Key(c3x + dx, c3z + dz, s3y));
            }
        }
    }

    // ========== Prefetch loading (cold storage → hot cache) ==========

    /**
     * Prefetch L2 cache (only triggers async loads, does not write to cache directly).
     */
    private int prefetchL2Around(ServerWorld world, String dim, L2Key center) {
        int count = 0;
        int c2x = center.getC2x();
        int c2z = center.getC2z();
        int s2y = center.getS2y();

        // Only prefetch current Y layer (reduce overhead)
        for (int dx = -CivilConfig.prefetchL2Radius; dx <= CivilConfig.prefetchL2Radius; dx++) {
            for (int dz = -CivilConfig.prefetchL2Radius; dz <= CivilConfig.prefetchL2Radius; dz++) {
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

                    if (count >= CivilConfig.maxAsyncLoadsPerSecond / 2) {
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
        for (int dx = -CivilConfig.prefetchL3Radius; dx <= CivilConfig.prefetchL3Radius; dx++) {
            for (int dz = -CivilConfig.prefetchL3Radius; dz <= CivilConfig.prefetchL3Radius; dz++) {
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

                    if (count >= CivilConfig.maxAsyncLoadsPerSecond / 2) {
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
