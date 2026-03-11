package civil.civilization;

import civil.CivilServices;
import civil.civilization.scoring.CivilizationService;
import civil.civilization.storage.CivilStorage;
import civil.config.CivilConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spatial tracker for undying anchor (emerald structure) positions.
 *
 * <p>Anchors are activated by right-clicking the center emerald with a totem.
 * Valid for rescue when: activated, global cooldown passed, full civilization (1.0), clearance above.
 *
 * <p>All data is persisted to the H2 {@code undying_anchors} table and loaded at server startup.
 */
public final class UndyingAnchorTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-undying-anchor");

    /** Maximum search radius in blocks (3D Euclidean). */
    public static final int MAX_SEARCH_RADIUS = 128;

    /**
     * Epsilon for civilization score comparison.
     * Sigmoid output never quite reaches 1.0; detector shows %.2f so 0.995 displays as "1.00".
     * Accept score >= required - CIV_EPSILON (e.g. 0.99 when required=1.0).
     */
    public static final double CIV_EPSILON = 0.01;

    /** dim -> { packedBlockPos -> AnchorEntry }. */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, AnchorEntry>> anchors = new ConcurrentHashMap<>();
    /** VC bucket index for efficient range queries. */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, ConcurrentHashMap<Integer, ConcurrentHashMap<Long, AnchorEntry>>>>
            anchorsByVcXZ = new ConcurrentHashMap<>();

    private volatile CivilStorage storage;
    private volatile boolean initialized = false;
    private volatile boolean anchorsDirty;

    /** For unified flush: produce full snapshot. */
    public List<CivilStorage.StoredUndyingAnchor> snapshotAllAnchors() {
        List<CivilStorage.StoredUndyingAnchor> out = new ArrayList<>();
        for (var dimEntry : anchors.entrySet()) {
            String dim = dimEntry.getKey();
            for (AnchorEntry a : dimEntry.getValue().values()) {
                out.add(new CivilStorage.StoredUndyingAnchor(dim, a.x(), a.y(), a.z(), a.activated(), a.lastUsedGlobal()));
            }
        }
        return out;
    }

    public boolean isAnchorsDirty() { return anchorsDirty; }
    public void clearAnchorsDirty() { anchorsDirty = false; }

    /** Single anchor entry: position + activation state. */
    public record AnchorEntry(int x, int y, int z, boolean activated, long lastUsedGlobal) {}

    /** Result of findNearestValidAnchor. */
    public record ValidAnchorResult(BlockPos anchorPos, BlockPos teleportDest) {}

    // ========== Lifecycle ==========

    public void initialize(CivilStorage civilStorage) {
        this.storage = civilStorage;
        this.anchorsDirty = false;
        anchors.clear();
        anchorsByVcXZ.clear();

        List<CivilStorage.StoredUndyingAnchor> stored = civilStorage.loadUndyingAnchors();
        for (CivilStorage.StoredUndyingAnchor a : stored) {
            AnchorEntry entry = new AnchorEntry(a.x(), a.y(), a.z(), a.activated(), a.lastUsedGlobal());
            getOrCreateDim(a.dim()).put(packPos(a.x(), a.y(), a.z()), entry);
            indexAnchor(a.dim(), entry);
        }

        initialized = true;
        LOGGER.info("[civil-undying-anchor] Loaded {} undying anchor(s) from database", stored.size());
    }

    public void shutdown() {
        initialized = false;
        anchors.clear();
        anchorsByVcXZ.clear();
        storage = null;
    }

    public boolean isInitialized() {
        return initialized;
    }

    // ========== Queries ==========

    /**
     * Check if there is an anchor (activated or not) at the given position.
     */
    public boolean isAnchorAt(String dim, int x, int y, int z) {
        if (!initialized) return false;
        var dimMap = anchors.get(dim);
        if (dimMap == null) return false;
        return dimMap.containsKey(packPos(x, y, z));
    }

    /**
     * Get the anchor entry at the given position, or null.
     */
    public AnchorEntry getAnchorAt(String dim, int x, int y, int z) {
        if (!initialized) return null;
        var dimMap = anchors.get(dim);
        if (dimMap == null) return null;
        return dimMap.get(packPos(x, y, z));
    }

    /**
     * Collect all activated anchors in the given dimension.
     * Used by the particle manager for server-side scanning.
     */
    public List<AnchorEntry> collectActivatedAnchors(String dim) {
        List<AnchorEntry> result = new ArrayList<>();
        if (!initialized) return result;
        var dimMap = anchors.get(dim);
        if (dimMap == null) return result;
        for (AnchorEntry e : dimMap.values()) {
            if (e.activated()) result.add(e);
        }
        return result;
    }

    /**
     * Find the nearest valid undying anchor within maxDist blocks.
     * Valid = activated, global cooldown passed, civilization >= required, 2-block clearance above.
     */
    public ValidAnchorResult findNearestValidAnchor(ServerLevel world, BlockPos pos, int maxDist) {
        if (!initialized) return null;

        CivilizationService civService = CivilServices.getCivilizationService();
        if (civService == null) return null;

        String dim = world.dimension().identifier().toString();
        var dimIndex = anchorsByVcXZ.get(dim);
        if (dimIndex == null || dimIndex.isEmpty()) return null;

        double civRequired = CivilConfig.getUndyingAnchorCivRequired();
        long cooldownMs = CivilConfig.undyingAnchorGlobalCooldownSeconds * 1000L;
        long now = ServerClock.now();

        double px = pos.getX() + 0.5;
        double py = pos.getY() + 0.5;
        double pz = pos.getZ() + 0.5;
        double maxDistSq = (double) maxDist * maxDist;

        int centerVCX = pos.getX() >> 4;
        int centerVCZ = pos.getZ() >> 4;
        int vcRadius = (maxDist + 16) / 16;

        List<AnchorEntry> candidates = new ArrayList<>();
        for (int dx = -vcRadius; dx <= vcRadius; dx++) {
            for (int dz = -vcRadius; dz <= vcRadius; dz++) {
                var syMap = dimIndex.get(packVcXZ(centerVCX + dx, centerVCZ + dz));
                if (syMap == null) continue;

                for (var cell : syMap.values()) {
                    if (cell == null) continue;
                    for (AnchorEntry a : cell.values()) {
                        if (!a.activated()) continue;
                        if (a.lastUsedGlobal() != 0 && (now - a.lastUsedGlobal()) < cooldownMs) continue;

                        double ax = a.x() + 0.5;
                        double ay = a.y() + 0.5;
                        double az = a.z() + 0.5;
                        double distSq = (ax - px) * (ax - px) + (ay - py) * (ay - py) + (az - pz) * (az - pz);
                        if (distSq <= maxDistSq) candidates.add(a);
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.comparingDouble(a -> {
            double ax = a.x() + 0.5;
            double ay = a.y() + 0.5;
            double az = a.z() + 0.5;
            return (ax - px) * (ax - px) + (ay - py) * (ay - py) + (az - pz) * (az - pz);
        }));

        for (AnchorEntry a : candidates) {
            BlockPos anchorPos = new BlockPos(a.x(), a.y(), a.z());
            double score = civService.getCScoreAt(world, anchorPos).score();
            if (score + CIV_EPSILON < civRequired) continue;

            BlockPos teleportDest = anchorPos.above();
            if (!hasClearanceAbove(world, teleportDest, 2)) continue;

            return new ValidAnchorResult(anchorPos, teleportDest);
        }
        return null;
    }

    private static boolean hasClearanceAbove(ServerLevel world, BlockPos base, int height) {
        for (int dy = 0; dy < height; dy++) {
            if (!world.getBlockState(base.offset(0, dy, 0)).isAir()) return false;
        }
        return true;
    }

    // ========== Updates ==========

    /**
     * Called when a player activates the structure with a totem. Adds or updates the anchor.
     */
    public void onAnchorActivated(String dim, int x, int y, int z) {
        if (!initialized) return;

        long key = packPos(x, y, z);
        AnchorEntry entry = new AnchorEntry(x, y, z, true, 0);

        var dimMap = getOrCreateDim(dim);
        AnchorEntry prev = dimMap.put(key, entry);

        if (prev == null) {
            indexAnchor(dim, entry);
        } else {
            updateIndexedAnchor(dim, prev, entry);
        }
        anchorsDirty = true;
    }

    /**
     * Called after a successful rescue. Sets activated=false, lastUsedGlobal=now.
     */
    public void onRescueUsed(String dim, int x, int y, int z) {
        if (!initialized) return;

        long key = packPos(x, y, z);
        long now = ServerClock.now();

        var dimMap = anchors.get(dim);
        if (dimMap == null) return;

        AnchorEntry current = dimMap.get(key);
        if (current == null) return;

        AnchorEntry updated = new AnchorEntry(x, y, z, false, now);
        dimMap.put(key, updated);
        updateIndexedAnchor(dim, current, updated);
        anchorsDirty = true;
    }

    /**
     * Called when the structure is broken (emerald/gold/stair removed). Removes from hot store.
     */
    public void onAnchorRemoved(String dim, int x, int y, int z) {
        if (!initialized) return;

        var dimAnchors = anchors.get(dim);
        if (dimAnchors == null) return;

        long key = packPos(x, y, z);
        AnchorEntry removed = dimAnchors.remove(key);
        if (removed != null) {
            unindexAnchor(dim, removed);
            anchorsDirty = true;
        }
    }

    /**
     * Re-activate an anchor that was deactivated after rescue. Called when player uses totem again.
     * Structure must still be valid (tracker entry exists from cold storage).
     */
    public void reactivateAnchor(String dim, int x, int y, int z) {
        if (!initialized) return;

        long key = packPos(x, y, z);
        var dimMap = anchors.get(dim);
        if (dimMap == null) return;

        AnchorEntry current = dimMap.get(key);
        if (current == null) return;

        AnchorEntry updated = new AnchorEntry(x, y, z, true, current.lastUsedGlobal());
        dimMap.put(key, updated);
        updateIndexedAnchor(dim, current, updated);
        anchorsDirty = true;
    }

    // ========== Helpers ==========

    private ConcurrentHashMap<Long, AnchorEntry> getOrCreateDim(String dim) {
        return anchors.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
    }

    private void indexAnchor(String dim, AnchorEntry entry) {
        int vcx = entry.x() >> 4;
        int vcz = entry.z() >> 4;
        int sy = Math.floorDiv(entry.y(), 16);
        long bucketKey = packVcXZ(vcx, vcz);
        long posKey = packPos(entry.x(), entry.y(), entry.z());
        anchorsByVcXZ
                .computeIfAbsent(dim, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(bucketKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(sy, k -> new ConcurrentHashMap<>())
                .put(posKey, entry);
    }

    private void unindexAnchor(String dim, AnchorEntry entry) {
        var dimIndex = anchorsByVcXZ.get(dim);
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
        if (dimIndex.isEmpty()) anchorsByVcXZ.remove(dim);
    }

    private void updateIndexedAnchor(String dim, AnchorEntry oldEntry, AnchorEntry newEntry) {
        unindexAnchor(dim, oldEntry);
        indexAnchor(dim, newEntry);
    }

    private static long packPos(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    private static long packVcXZ(int vcx, int vcz) {
        return (((long) vcx) << 32) ^ (vcz & 0xffffffffL);
    }
}
