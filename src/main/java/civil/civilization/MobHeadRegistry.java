package civil.civilization;

import civil.CivilMod;
import civil.civilization.storage.H2Storage;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global registry of all monster head (skull totem) positions in the world.
 *
 * <p>Provides O(1) position lookup and O(N) nearest-head queries where N is
 * the total number of placed heads (typically 10-100). All data is persisted
 * to the H2 {@code mob_heads} table and loaded at server startup.
 *
 * <p><b>Data sources (priority order):</b>
 * <ol>
 *   <li>Block change mixin — real-time, incremental add/remove</li>
 *   <li>Chunk load event — discovers pre-existing heads (world upgrade path)</li>
 *   <li>H2 cold storage — restores state across server restarts</li>
 * </ol>
 *
 * <p><b>Thread safety:</b> ConcurrentHashMap for reads/writes. Block change
 * and chunk load events fire on the server thread; spawn queries also run on
 * the server thread. Async H2 writes run on the storage IO executor.
 */
public final class MobHeadRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-heads");

    /**
     * dim -> { packedBlockPos -> HeadEntry }.
     * ConcurrentHashMap for thread safety; inner map is also concurrent.
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, HeadEntry>> heads = new ConcurrentHashMap<>();

    private volatile H2Storage storage;
    private volatile boolean initialized = false;

    /** Single head entry: exact block position + skull type name. */
    public record HeadEntry(int x, int y, int z, String skullType) {}

    /**
     * Query result: nearest head distance and aggregate info.
     *
     * @param nearestDist3D   Euclidean distance in full 3D (for suppression curve)
     * @param nearestDistXZ   Horizontal (XZ plane) distance (for max radius check)
     */
    public record HeadProximity(boolean hasHeads, double nearestDist3D, double nearestDistXZ, int totalCount) {
        public static final HeadProximity NONE = new HeadProximity(false, Double.MAX_VALUE, Double.MAX_VALUE, 0);
    }

    // ========== Lifecycle ==========

    /**
     * Initialize the registry by loading all persisted heads from H2.
     * Must be called on the server thread during world load, after H2 is ready.
     */
    public void initialize(H2Storage h2Storage) {
        this.storage = h2Storage;
        heads.clear();

        List<H2Storage.StoredMobHead> stored = h2Storage.loadAllMobHeads();
        for (H2Storage.StoredMobHead h : stored) {
            getOrCreateDim(h.dim()).put(packPos(h.x(), h.y(), h.z()),
                    new HeadEntry(h.x(), h.y(), h.z(), h.skullType()));
        }

        initialized = true;
        LOGGER.info("[civil-heads] Loaded {} mob head(s) from database", stored.size());
    }

    /**
     * Shutdown: clear in-memory data. H2 is already up to date.
     */
    public void shutdown() {
        initialized = false;
        heads.clear();
        storage = null;
    }

    public boolean isInitialized() {
        return initialized;
    }

    // ========== Queries ==========

    /**
     * Find the nearest mob head to the given position in full 3D space.
     * Returns {@link HeadProximity#NONE} if no heads exist in this dimension.
     *
     * <p>Iterates all heads in the dimension (typically 10-100).
     * Cost: ~200ns for 100 heads.
     */
    public HeadProximity queryNearest(String dim, double x, double y, double z) {
        var dimHeads = heads.get(dim);
        if (dimHeads == null || dimHeads.isEmpty()) {
            return HeadProximity.NONE;
        }

        double minDistSq3D = Double.MAX_VALUE;
        double minDistSqXZ = Double.MAX_VALUE;
        int count = 0;

        for (HeadEntry h : dimHeads.values()) {
            count++;
            double dx = h.x() + 0.5 - x;  // center of block
            double dy = h.y() + 0.5 - y;
            double dz = h.z() + 0.5 - z;
            double distSq3D = dx * dx + dy * dy + dz * dz;
            double distSqXZ = dx * dx + dz * dz;
            if (distSq3D < minDistSq3D) {
                minDistSq3D = distSq3D;
            }
            if (distSqXZ < minDistSqXZ) {
                minDistSqXZ = distSqXZ;
            }
        }

        return new HeadProximity(true, Math.sqrt(minDistSq3D), Math.sqrt(minDistSqXZ), count);
    }

    /**
     * Check if a head exists at the exact position. O(1).
     */
    public boolean hasHeadAt(String dim, int x, int y, int z) {
        var dimHeads = heads.get(dim);
        if (dimHeads == null) return false;
        return dimHeads.containsKey(packPos(x, y, z));
    }

    /**
     * Get total head count in a dimension.
     */
    public int getHeadCount(String dim) {
        var dimHeads = heads.get(dim);
        return dimHeads == null ? 0 : dimHeads.size();
    }

    // ========== Updates ==========

    /**
     * Called when a monster head block is placed or discovered (chunk load).
     * Adds to in-memory map (if absent) and persists to H2 asynchronously.
     *
     * @return true if this was a new head (not already known)
     */
    public boolean onHeadAdded(String dim, int x, int y, int z, String skullType) {
        if (!initialized) return false;

        long key = packPos(x, y, z);
        HeadEntry entry = new HeadEntry(x, y, z, skullType);
        HeadEntry prev = getOrCreateDim(dim).putIfAbsent(key, entry);

        if (prev == null) {
            // New head — persist to H2
            if (storage != null) {
                storage.saveMobHeadAsync(dim, x, y, z, skullType);
            }
            if (CivilMod.DEBUG) {
                LOGGER.info("[civil-heads] Added head dim={} pos=({},{},{}) type={}",
                        dim, x, y, z, skullType);
            }
            return true;
        }
        return false;
    }

    /**
     * Called when a block at this position is no longer a monster head.
     * No-op if no head was registered here. O(1).
     */
    public void onHeadRemoved(String dim, int x, int y, int z) {
        if (!initialized) return;

        var dimHeads = heads.get(dim);
        if (dimHeads == null) return;

        long key = packPos(x, y, z);
        HeadEntry removed = dimHeads.remove(key);
        if (removed != null) {
            if (storage != null) {
                storage.deleteMobHeadAsync(dim, x, y, z);
            }
            if (CivilMod.DEBUG) {
                LOGGER.info("[civil-heads] Removed head dim={} pos=({},{},{})",
                        dim, x, y, z);
            }
        }
    }

    // ========== Helpers ==========

    private ConcurrentHashMap<Long, HeadEntry> getOrCreateDim(String dim) {
        return heads.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
    }

    /**
     * Pack block coordinates into a single long for map key.
     * Uses Minecraft's BlockPos encoding for consistency.
     */
    private static long packPos(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }
}
