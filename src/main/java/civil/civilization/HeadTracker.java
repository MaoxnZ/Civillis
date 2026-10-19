package civil.civilization;

import civil.CivilMod;
import civil.CivilServices;
import civil.civilization.storage.CivilStorage;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spatial tracker for all monster head (skull totem) positions in the world.
 *
 * <p>Provides O(1) position lookup and bucketed nearest-head queries where cost
 * is proportional to candidates inside scanned XZ buckets. Mob heads are persisted
 * via {@link CivilStorage} (NBT) and loaded at server startup.
 *
 * <p>Type resolution (skull type string → entity type) is delegated to
 * {@link HeadTypeRegistry}, which is populated from datapack JSON.
 * This class only tracks spatial state — it does not define game rules.
 *
 * <p><b>Data sources (priority order):</b>
 * <ol>
 *   <li>Block change mixin — real-time, incremental add/remove</li>
 *   <li>Chunk load event — discovers pre-existing heads (world upgrade path)</li>
 *   <li>Disk snapshot — restores state across server restarts</li>
 * </ol>
 *
 * <p><b>Thread safety:</b> ConcurrentHashMap for reads/writes. Block change
 * and chunk load events fire on the server thread; spawn queries also run on
 * the server thread. Dirty snapshots flush on the storage I/O executor.
 */
public final class HeadTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-heads");

    /**
     * dim -> { packedBlockPos -> HeadEntry }.
     * ConcurrentHashMap for thread safety; inner map is also concurrent.
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, HeadEntry>> heads = new ConcurrentHashMap<>();
    /**
     * Fixed 16x16 XZ bucket index:
     * dim -> { packed(vcx,vcz) -> { sy -> { packedBlockPos -> HeadEntry } } }.
     * Y is not windowed for suppress scan; query iterates existing sy buckets.
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, ConcurrentHashMap<Integer, ConcurrentHashMap<Long, HeadEntry>>>>
            headsByVcXZ = new ConcurrentHashMap<>();

    private volatile CivilStorage storage;
    private volatile boolean initialized = false;
    private volatile boolean mobHeadsDirty;

    /** For unified flush: produce full snapshot. Clears dirty after flush. */
    public List<CivilStorage.StoredMobHead> snapshotAllHeads() {
        List<CivilStorage.StoredMobHead> out = new ArrayList<>();
        for (var dimEntry : heads.entrySet()) {
            String dim = dimEntry.getKey();
            for (HeadEntry h : dimEntry.getValue().values()) {
                out.add(new CivilStorage.StoredMobHead(dim, h.x(), h.y(), h.z(), h.skullType()));
            }
        }
        return out;
    }

    public boolean isMobHeadsDirty() { return mobHeadsDirty; }
    public void clearMobHeadsDirty() { mobHeadsDirty = false; }

    /** Single head entry: exact block position + skull type name. */
    public record HeadEntry(int x, int y, int z, String skullType) {}

    // ========== Lifecycle ==========

    /**
     * Initialize the tracker by loading all persisted heads from storage.
     * Must be called on the server thread during world load.
     */
    public void initialize(CivilStorage civilStorage) {
        this.storage = civilStorage;
        this.mobHeadsDirty = false;
        heads.clear();
        headsByVcXZ.clear();

        List<CivilStorage.StoredMobHead> stored = civilStorage.loadMobHeads();
        for (CivilStorage.StoredMobHead h : stored) {
            HeadEntry entry = new HeadEntry(h.x(), h.y(), h.z(), h.skullType());
            getOrCreateDim(h.dim()).put(packPos(h.x(), h.y(), h.z()), entry);
            indexHead(h.dim(), entry);
        }

        initialized = true;
        LOGGER.info("[civil-heads] Loaded {} mob head(s) from storage", stored.size());
    }

    /**
     * Shutdown: clear in-memory state (persistence is handled by
     * {@link civil.civilization.cache.TtlCacheService#shutdown()}).
     */
    public void shutdown() {
        initialized = false;
        heads.clear();
        headsByVcXZ.clear();
        storage = null;
    }

    public boolean isInitialized() {
        return initialized;
    }

    // ========== Queries ==========

    /**
     * Returns the head entry at the exact block, or null.
     */
    public HeadEntry getHeadAt(String dim, int x, int y, int z) {
        var dimHeads = heads.get(dim);
        if (dimHeads == null) return null;
        return dimHeads.get(packPos(x, y, z));
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

    /**
     * Get all head entries in a dimension.
     * Returns an unmodifiable view of the values; safe to iterate but may see
     * concurrent modifications (which is acceptable for visualization).
     *
     * @return collection of head entries, or empty collection if no heads in dimension
     */
    public java.util.Collection<HeadEntry> getHeadsInDimension(String dim) {
        var dimHeads = heads.get(dim);
        if (dimHeads == null || dimHeads.isEmpty()) return List.of();
        return dimHeads.values();
    }

    // ========== Updates ==========

    /**
     * Called when a monster head block is placed or discovered (chunk load).
     * Adds to in-memory map (if absent); marks mob heads dirty for the next unified flush.
     *
     * @return true if this was a new head (not already known)
     */
    public boolean onHeadAdded(String dim, int x, int y, int z, String skullType) {
        if (!initialized) return false;

        long key = packPos(x, y, z);
        HeadEntry entry = new HeadEntry(x, y, z, skullType);
        HeadEntry prev = getOrCreateDim(dim).putIfAbsent(key, entry);

        if (prev == null) {
            indexHead(dim, entry);
            mobHeadsDirty = true;
            FarmShrineTracker shrines = CivilServices.getFarmShrineTracker();
            if (shrines != null && shrines.isInitialized()) {
                shrines.onMobHeadAdded(dim, x, y, z, skullType);
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
            unindexHead(dim, removed);
            mobHeadsDirty = true;
            FarmShrineTracker shrines = CivilServices.getFarmShrineTracker();
            if (shrines != null && shrines.isInitialized()) {
                shrines.onMobHeadRemoved(dim, x, y, z);
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

    private void indexHead(String dim, HeadEntry entry) {
        int vcx = entry.x() >> 4;
        int vcz = entry.z() >> 4;
        int sy = Math.floorDiv(entry.y(), 16);
        long bucketKey = packVcXZ(vcx, vcz);
        long posKey = packPos(entry.x(), entry.y(), entry.z());
        headsByVcXZ
                .computeIfAbsent(dim, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(bucketKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(sy, k -> new ConcurrentHashMap<>())
                .put(posKey, entry);
    }

    private void unindexHead(String dim, HeadEntry entry) {
        var dimIndex = headsByVcXZ.get(dim);
        if (dimIndex == null) return;

        int vcx = entry.x() >> 4;
        int vcz = entry.z() >> 4;
        int sy = Math.floorDiv(entry.y(), 16);
        long bucketKey = packVcXZ(vcx, vcz);
        long posKey = packPos(entry.x(), entry.y(), entry.z());

        var syMap = dimIndex.get(bucketKey);
        if (syMap == null) return;
        var cell = syMap.get(sy);
        if (cell == null) return;
        cell.remove(posKey);
        if (cell.isEmpty()) syMap.remove(sy);
        if (syMap.isEmpty()) dimIndex.remove(bucketKey);
        if (dimIndex.isEmpty()) headsByVcXZ.remove(dim);
    }

    /**
     * Pack block coordinates into a single long for map key.
     * Uses Minecraft's BlockPos encoding for consistency.
     */
    private static long packPos(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    private static long packVcXZ(int vcx, int vcz) {
        return (((long) vcx) << 32) ^ (vcz & 0xffffffffL);
    }
}
