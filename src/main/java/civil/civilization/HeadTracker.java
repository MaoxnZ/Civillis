package civil.civilization;

import civil.CivilMod;
import civil.civilization.storage.H2Storage;
import civil.registry.HeadTypeRegistry;
import civil.registry.HeadTypeRegistry.HeadTypeEntry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spatial tracker for all monster head (skull totem) positions in the world.
 *
 * <p>Provides O(1) position lookup and O(N) nearest-head queries where N is
 * the total number of placed heads (typically 10-100). All data is persisted
 * to the H2 {@code mob_heads} table and loaded at server startup.
 *
 * <p>Type resolution (skull type string → entity type) is delegated to
 * {@link HeadTypeRegistry}, which is populated from datapack JSON.
 * This class only tracks spatial state — it does not define game rules.
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
 *
 * <p>Replaces the former {@code MobHeadRegistry} class.
 */
public final class HeadTracker {

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

    /**
     * Combined query result: nearby head count + conversion pool (for HEAD_NEARBY)
     * + nearest distance info (for HEAD_SUPPRESS), computed in a single O(N) pass.
     *
     * <p>Only heads whose skull type is registered <b>and</b> {@code enabled=true}
     * in {@link HeadTypeRegistry} participate in any calculation.
     *
     * @param nearbyHeadCount Total enabled heads within the VC box (includes convert=false heads).
     * @param convertPool     Entity types of enabled+convert=true heads in the VC box
     *                        (with duplicates for weighted sampling). Empty if none qualify.
     * @param proximity       Nearest enabled head distance info across the entire dimension.
     */
    public record HeadQuery(int nearbyHeadCount, List<EntityType<?>> convertPool, HeadProximity proximity) {
        public static final HeadQuery NONE = new HeadQuery(0, List.of(), HeadProximity.NONE);

        public boolean hasNearbyHeads() { return nearbyHeadCount > 0; }
    }

    // ========== Lifecycle ==========

    /**
     * Initialize the tracker by loading all persisted heads from H2.
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

    /**
     * Extract the dimension identifier from a {@code RegistryKey.toString()} string.
     * Converts {@code "ResourceKey[minecraft:dimension / minecraft:the_nether]"}
     * to {@code "minecraft:the_nether"}.
     */
    static String dimIdOf(String registryKeyStr) {
        int idx = registryKeyStr.lastIndexOf(" / ");
        if (idx >= 0 && registryKeyStr.endsWith("]")) {
            return registryKeyStr.substring(idx + 3, registryKeyStr.length() - 1);
        }
        return registryKeyStr;
    }

    // ========== Queries ==========

    /**
     * Combined query: single O(N) pass that returns nearby head count + conversion
     * pool (for HEAD_NEARBY) and nearest distance info (for HEAD_SUPPRESS).
     *
     * <p>Only heads whose skull type is registered <b>and enabled</b> in
     * {@link HeadTypeRegistry} participate. Unregistered or disabled heads are
     * skipped entirely — they do not contribute to distance, count, or conversion.
     * This is both correct (datapack control) and performant (fewer distance calcs).
     *
     * <p>Cost: ~300ns for 100 heads (single traversal).
     *
     * @param dim     dimension key
     * @param pos     center position (block coordinates)
     * @param rangeCX nearby range in voxel chunks along X
     * @param rangeCZ nearby range in voxel chunks along Z
     * @param rangeSY nearby range in voxel chunks along Y
     * @return combined result; {@link HeadQuery#NONE} if no heads in dimension
     */
    public HeadQuery queryHeads(String dim, BlockPos pos, int rangeCX, int rangeCZ, int rangeSY) {
        var dimHeads = heads.get(dim);
        if (dimHeads == null || dimHeads.isEmpty()) return HeadQuery.NONE;

        String dimId = dimIdOf(dim);

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        int centerVCX = pos.getX() >> 4;
        int centerVCZ = pos.getZ() >> 4;
        int centerVCY = Math.floorDiv(pos.getY(), 16);

        List<EntityType<?>> convertPool = new ArrayList<>();
        int nearbyHeadCount = 0;
        double minDistSq3D = Double.MAX_VALUE;
        double minDistSqXZ = Double.MAX_VALUE;
        int totalEnabledCount = 0;

        for (HeadEntry h : dimHeads.values()) {
            // Registry gate: skip unregistered, disabled, or dimension-restricted heads
            HeadTypeEntry entry = HeadTypeRegistry.get(h.skullType());
            if (entry == null || !entry.enabled() || !entry.isActiveIn(dimId)) continue;

            totalEnabledCount++;

            // Distance tracking (for HEAD_SUPPRESS) — enabled heads only
            double dx = h.x() + 0.5 - cx;
            double dy = h.y() + 0.5 - cy;
            double dz = h.z() + 0.5 - cz;
            double distSq3D = dx * dx + dy * dy + dz * dz;
            double distSqXZ = dx * dx + dz * dz;
            if (distSq3D < minDistSq3D) minDistSq3D = distSq3D;
            if (distSqXZ < minDistSqXZ) minDistSqXZ = distSqXZ;

            // VC box check (for HEAD_NEARBY + conversion)
            int hvcx = h.x() >> 4;
            int hvcz = h.z() >> 4;
            int hvcy = Math.floorDiv(h.y(), 16);
            if (Math.abs(hvcx - centerVCX) <= rangeCX
                    && Math.abs(hvcz - centerVCZ) <= rangeCZ
                    && Math.abs(hvcy - centerVCY) <= rangeSY) {

                nearbyHeadCount++;
                if (entry.entityType() != null && entry.convertEnabled()) {
                    convertPool.add(entry.entityType());
                }
            }
        }

        if (totalEnabledCount == 0) return HeadQuery.NONE;

        HeadProximity proximity = new HeadProximity(true, Math.sqrt(minDistSq3D), Math.sqrt(minDistSqXZ), totalEnabledCount);
        return new HeadQuery(nearbyHeadCount, convertPool, proximity);
    }

    /**
     * Simple nearby head check (for CivilDetectorItem and other non-spawn callers).
     * Single O(N) pass, returns only the nearby entity types without distance info.
     *
     * <p>Only enabled heads with a non-null entity type are returned (regardless
     * of their {@code convertEnabled} flag — this is a general-purpose query,
     * not a conversion-specific one).
     */
    public List<EntityType<?>> getHeadTypesNear(String dim, BlockPos pos, int rangeCX, int rangeCZ, int rangeSY) {
        var dimHeads = heads.get(dim);
        if (dimHeads == null || dimHeads.isEmpty()) return List.of();

        String dimId = dimIdOf(dim);

        int centerCX = pos.getX() >> 4;
        int centerCZ = pos.getZ() >> 4;
        int centerSY = Math.floorDiv(pos.getY(), 16);

        List<EntityType<?>> result = new ArrayList<>();

        for (HeadEntry h : dimHeads.values()) {
            HeadTypeEntry entry = HeadTypeRegistry.get(h.skullType());
            if (entry == null || !entry.enabled() || !entry.isActiveIn(dimId)) continue;

            int hcx = h.x() >> 4;
            int hcz = h.z() >> 4;
            int hsy = Math.floorDiv(h.y(), 16);

            if (Math.abs(hcx - centerCX) <= rangeCX
                    && Math.abs(hcz - centerCZ) <= rangeCZ
                    && Math.abs(hsy - centerSY) <= rangeSY) {

                if (entry.entityType() != null) {
                    result.add(entry.entityType());
                }
            }
        }

        return result;
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
