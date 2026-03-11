package civil.civilization.storage;

import civil.civilization.CScore;
import civil.civilization.VoxelChunkKey;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract storage layer for Civil mod persistence.
 *
 * <p>NBT implementation replaces H2. Structure data (mob heads, undying anchors) use
 * load-on-init + periodic flush (write full snapshot). L1 and presence use bulk region IO.
 */
public interface CivilStorage {

    // ========== Stored record types ==========

    record StoredMobHead(String dim, int x, int y, int z, String skullType) {}
    record StoredUndyingAnchor(String dim, int x, int y, int z, boolean activated, long lastUsedGlobal) {}
    record StoredL1Entry(VoxelChunkKey key, CScore cScore, long createTime, String dim) {
        public StoredL1Entry(VoxelChunkKey key, CScore cScore, long createTime) {
            this(key, cScore, createTime, null);
        }
    }
    record L1SaveRequest(String dim, VoxelChunkKey key, CScore cScore) {}
    record PresenceSaveRequest(String dim, VoxelChunkKey key, long presenceTime, long lastRecoveryTime) {}

    /** Per-key L1 data in a region file (score optional if 0; presence optional). */
    record L1Entry(double score, long presenceTime, long lastRecoveryTime) {}

    // ========== Lifecycle ==========

    /**
     * Initialize storage for the given world (e.g. resolve paths, open files).
     */
    void initialize(ServerLevel world);

    /**
     * Close storage and release resources.
     */
    void close();

    // ========== Meta (ServerClock) ==========

    /** Load persisted ServerClock value. Returns 0 if absent (fresh world). */
    long loadServerClockMillis();

    /** Write meta (ServerClock). Called by unified flush or shutdown. */
    void writeMeta(long serverClockMillis);

    // ========== L1 (score cache) ==========

    /** Load all L1 entries. NBT: returns empty (L1 not preloaded). */
    List<StoredL1Entry> loadAllL1();

    /**
     * Sync load L1 score for a single key. NBT: triggers bulk load of region; returns null if miss.
     */
    Double loadL1Sync(String dim, VoxelChunkKey key);

    /** Async save L1. NBT: adds to pendingScoreWrites; actual write in unified flush. */
    CompletableFuture<Void> saveL1Async(String dim, VoxelChunkKey key, CScore cScore);

    // ========== Presence ==========

    /**
     * Sync load presenceTime/lastRecoveryTime. NBT: triggers region bulk load; returns null if miss.
     */
    long[] loadPresenceSync(String dim, VoxelChunkKey key);

    /** Batch save presence. NBT: adds to pendingPresenceWrites; unified flush writes. */
    CompletableFuture<Void> batchSavePresenceAsync(List<PresenceSaveRequest> requests);

    // ========== Structure: mob heads ==========

    /** Load all mob heads at init. NBT: read mob_heads.nbt. */
    List<StoredMobHead> loadMobHeads();

    /**
     * Write full snapshot of mob heads. Called by unified flush when dirty.
     * NBT: write mob_heads.nbt.
     */
    void writeMobHeads(List<StoredMobHead> snapshot);

    // ========== Structure: undying anchors ==========

    /** Load all undying anchors at init. NBT: read undying_anchors.nbt. */
    List<StoredUndyingAnchor> loadUndyingAnchors();

    /**
     * Write full snapshot of undying anchors. Called by unified flush when dirty.
     * NBT: write undying_anchors.nbt.
     */
    void writeUndyingAnchors(List<StoredUndyingAnchor> snapshot);

    // ========== Batch L1 (legacy / prefetch) ==========

    CompletableFuture<List<StoredL1Entry>> loadL1RangeAsync(
            String dim, int minCx, int maxCx, int minCz, int maxCz, int sy);

    CompletableFuture<Void> batchSaveL1Async(List<L1SaveRequest> requests);

    /**
     * Bulk load L1 region (32×32 XZ chunks). Async, runs on ColdIOQueue.
     */
    CompletableFuture<java.util.Map<VoxelChunkKey, L1Entry>> bulkLoadRegion(String dim, int rx, int rz);

    /**
     * Sync load L1 region. For main-thread Hot miss recovery.
     * Returns empty map if file missing.
     */
    java.util.Map<VoxelChunkKey, L1Entry> loadL1RegionSync(String dim, int rx, int rz);

    /**
     * Write L1 region. Caller merges before calling. Runs on ColdIOQueue during flush.
     */
    void writeL1Region(String dim, int rx, int rz, java.util.Map<VoxelChunkKey, L1Entry> data);

    /**
     * Submit a task to run on the cold IO executor (for unified flush).
     * Returns future that completes when the task has run.
     */
    CompletableFuture<Void> submitOnIO(Runnable task);
}
