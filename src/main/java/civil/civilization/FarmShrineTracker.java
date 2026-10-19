package civil.civilization;

import civil.civilization.storage.CivilStorage;
import civil.config.CivilConfig;
import civil.registry.HeadTypeRegistry;
import civil.registry.HeadTypeRegistry.HeadTypeEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spatial tracker for farm shrines (soul campfire anchors). Symmetric persistence to
 * {@link UndyingAnchorTracker}: NBT snapshot + dirty flush.
 */
public final class FarmShrineTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-farm-shrine");

    /** dim -> packed anchor -> entry. */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, ShrineEntry>> shrines =
            new ConcurrentHashMap<>();
    /** VC bucket index (same shape as {@link UndyingAnchorTracker}). */
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, ConcurrentHashMap<Integer, ConcurrentHashMap<Long, ShrineEntry>>>>
            shrinesByVcXZ = new ConcurrentHashMap<>();

    private volatile CivilStorage storage;
    private volatile HeadTracker headTracker;
    private volatile boolean initialized;
    private volatile boolean shrinesDirty;

    public List<CivilStorage.StoredFarmShrine> snapshotAllShrines() {
        List<CivilStorage.StoredFarmShrine> out = new ArrayList<>();
        for (var dimEntry : shrines.entrySet()) {
            String dim = dimEntry.getKey();
            for (ShrineEntry s : dimEntry.getValue().values()) {
                if (s.activated()) {
                    out.add(new CivilStorage.StoredFarmShrine(dim, s.x(), s.y(), s.z(), true));
                }
            }
        }
        return out;
    }

    public boolean isShrinesDirty() {
        return shrinesDirty;
    }

    public void clearShrinesDirty() {
        shrinesDirty = false;
    }

    /** In-memory entry: anchor block + head positions (packed long) attributed to this shrine. */
    public static final class ShrineEntry {
        private final int x;
        private final int y;
        private final int z;
        private final boolean activated;
        private final Set<Long> headPosSet = ConcurrentHashMap.newKeySet();

        public ShrineEntry(int x, int y, int z, boolean activated) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.activated = activated;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }

        public boolean activated() {
            return activated;
        }

        public Set<Long> headPosSet() {
            return headPosSet;
        }
    }

    public record ShrineQuery(
            boolean insideShrine,
            boolean hasNearbyShrine,
            int conversionHeadCount,
            List<EntityType<?>> convertPool,
            int suppressionUnionHeadCount,
            double suppressNearestDist3d,
            double suppressNearestDistXZ,
            BlockPos dominantAnchor) {

        public static final ShrineQuery NONE = new ShrineQuery(
                false, false, 0, List.of(), 0, Double.MAX_VALUE, Double.MAX_VALUE, null);
    }

    public void initialize(CivilStorage civilStorage, HeadTracker heads) {
        this.storage = civilStorage;
        this.headTracker = heads;
        this.shrinesDirty = false;
        shrines.clear();
        shrinesByVcXZ.clear();

        List<CivilStorage.StoredFarmShrine> stored = civilStorage.loadFarmShrines();
        for (CivilStorage.StoredFarmShrine s : stored) {
            if (!s.activated()) continue;
            ShrineEntry entry = new ShrineEntry(s.x(), s.y(), s.z(), true);
            getOrCreateDim(s.dim()).put(packPos(s.x(), s.y(), s.z()), entry);
            indexShrine(s.dim(), entry);
        }

        for (String dim : shrines.keySet()) {
            for (ShrineEntry se : shrines.get(dim).values()) {
                rebuildHeadPosSetForShrine(dim, se);
            }
        }

        initialized = true;
        LOGGER.info("[civil-farm-shrine] Loaded {} farm shrine(s) from storage", stored.size());
    }

    /**
     * Recomputes every activated shrine's {@link ShrineEntry#headPosSet()} from {@link HeadTracker}
     * using the current {@link HeadTypeRegistry} (after {@link civil.registry.HeadTypeLoader#reload}).
     * Does not set {@link #shrinesDirty} — derived attribution only.
     */
    public void rebuildAllShrineHeadPosSetsFromHeadTracker() {
        if (!initialized || headTracker == null) {
            return;
        }
        for (var dimEntry : shrines.entrySet()) {
            String dim = dimEntry.getKey();
            for (ShrineEntry se : dimEntry.getValue().values()) {
                if (!se.activated()) {
                    continue;
                }
                rebuildHeadPosSetForShrine(dim, se);
            }
        }
    }

    public void shutdown() {
        initialized = false;
        shrines.clear();
        shrinesByVcXZ.clear();
        storage = null;
        headTracker = null;
    }

    public boolean isInitialized() {
        return initialized;
    }

    /** Query spawn-related shrine state for {@link civil.spawn.SpawnPolicy}. */
    public ShrineQuery queryForSpawn(String dim, BlockPos spawnPos) {
        if (!initialized) return ShrineQuery.NONE;

        Set<ShrineEntry> sSet = shrinesWhoseBypassContains(dim, spawnPos);
        boolean inside = !sSet.isEmpty();

        int convCount = 0;
        List<EntityType<?>> convertPool = List.of();
        if (inside) {
            Set<Long> union = unionHeadPosSet(sSet);
            convCount = union.size();
            convertPool = buildConvertPool(dim, union);
        }

        if (inside) {
            return new ShrineQuery(true, false, convCount, convertPool, 0, Double.MAX_VALUE, Double.MAX_VALUE, null);
        }

        Set<ShrineEntry> tSet = shrinesWithinAttractRadius(dim, spawnPos);
        boolean hasNearby = !tSet.isEmpty();
        if (!hasNearby) {
            return ShrineQuery.NONE;
        }

        Set<Long> tUnion = unionHeadPosSet(tSet);
        int supCount = tUnion.size();

        double px = spawnPos.getX() + 0.5;
        double py = spawnPos.getY() + 0.5;
        double pz = spawnPos.getZ() + 0.5;
        double best3d = Double.MAX_VALUE;
        double bestXZ = Double.MAX_VALUE;
        BlockPos dominant = null;
        for (ShrineEntry e : tSet) {
            double ax = e.x() + 0.5;
            double ay = e.y() + 0.5;
            double az = e.z() + 0.5;
            double dx = ax - px;
            double dy = ay - py;
            double dz = az - pz;
            double d3 = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double dxz = Math.sqrt(dx * dx + dz * dz);
            if (d3 < best3d) {
                best3d = d3;
                bestXZ = dxz;
                dominant = new BlockPos(e.x(), e.y(), e.z());
            }
        }

        return new ShrineQuery(false, true, 0, List.of(), supCount, best3d, bestXZ, dominant);
    }

    /** Nearest activated shrine anchor within attract radius for flee AI (center = mob pos). */
    public BlockPos findNearestShrineAnchorWithinAttract(String dim, BlockPos mobPos) {
        if (!initialized) return null;
        Set<ShrineEntry> tSet = shrinesWithinAttractRadius(dim, mobPos);
        if (tSet.isEmpty()) return null;
        double px = mobPos.getX() + 0.5;
        double py = mobPos.getY() + 0.5;
        double pz = mobPos.getZ() + 0.5;
        ShrineEntry best = null;
        double bestD = Double.MAX_VALUE;
        for (ShrineEntry e : tSet) {
            double ax = e.x() + 0.5;
            double ay = e.y() + 0.5;
            double az = e.z() + 0.5;
            double dx = ax - px;
            double dy = ay - py;
            double dz = az - pz;
            double d3 = dx * dx + dy * dy + dz * dz;
            if (d3 < bestD) {
                bestD = d3;
                best = e;
            }
        }
        return best == null ? null : new BlockPos(best.x(), best.y(), best.z());
    }

    /** True if block position lies in any activated shrine bypass box. */
    public boolean isInsideAnyBypassBox(String dim, BlockPos pos) {
        if (!initialized) return false;
        return !shrinesWhoseBypassContains(dim, pos).isEmpty();
    }

    /** Axis-aligned bypass boxes in block coords (debug / tooling). */
    public List<ShrineBypassBox> collectShrineBypassBoxes(String dim) {
        if (!initialized) return List.of();
        var dimMap = shrines.get(dim);
        if (dimMap == null || dimMap.isEmpty()) return List.of();
        List<ShrineBypassBox> out = new ArrayList<>();
        int rx = CivilConfig.farmShrineRangeX;
        int rz = CivilConfig.farmShrineRangeZ;
        int ry = CivilConfig.farmShrineRangeY;
        for (ShrineEntry s : dimMap.values()) {
            if (!s.activated()) continue;
            int ax = s.x();
            int ay = s.y();
            int az = s.z();
            int avcx = ax >> 4;
            int avcz = az >> 4;
            int avcy = Math.floorDiv(ay, 16);
            int minBx = (avcx - rx) << 4;
            int maxBx = (avcx + rx + 1) << 4;
            int minBz = (avcz - rz) << 4;
            int maxBz = (avcz + rz + 1) << 4;
            int minBy = (avcy - ry) << 4;
            int maxBy = (avcy + ry + 1) << 4;
            out.add(new ShrineBypassBox(minBx, maxBx, minBy, maxBy, minBz, maxBz));
        }
        return out;
    }

    public record ShrineBypassBox(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }

    public void forEachActivatedShrine(String dim, java.util.function.Consumer<ShrineEntry> consumer) {
        var m = shrines.get(dim);
        if (m == null) return;
        for (ShrineEntry e : m.values()) {
            if (e.activated()) {
                consumer.accept(e);
            }
        }
    }

    public boolean isShrineAt(String dim, int x, int y, int z) {
        if (!initialized) return false;
        var m = shrines.get(dim);
        return m != null && m.containsKey(packPos(x, y, z));
    }

    public void onShrineActivated(String dim, int x, int y, int z) {
        if (!initialized) return;
        long key = packPos(x, y, z);
        ShrineEntry entry = new ShrineEntry(x, y, z, true);
        getOrCreateDim(dim).put(key, entry);
        indexShrine(dim, entry);
        rebuildHeadPosSetForShrine(dim, entry);
        shrinesDirty = true;
    }

    public void onShrineRemoved(String dim, int x, int y, int z) {
        if (!initialized) return;
        var dimMap = shrines.get(dim);
        if (dimMap == null) return;
        ShrineEntry removed = dimMap.remove(packPos(x, y, z));
        if (removed != null) {
            unindexShrine(dim, removed);
            shrinesDirty = true;
        }
    }

    /** Called from {@link HeadTracker} when a mob head is added. */
    public void onMobHeadAdded(String dim, int x, int y, int z, String skullType) {
        if (!initialized) return;
        if (!isHeadEligible(dim, skullType)) return;
        long hpack = packPos(x, y, z);
        BlockPos hp = new BlockPos(x, y, z);
        for (ShrineEntry s : shrinesNearHeadVc(dim, hp)) {
            if (!s.activated()) continue;
            if (bypassContainsAnchor(s, hp)) {
                s.headPosSet().add(hpack);
            }
        }
    }

    /** Called from {@link HeadTracker} when a mob head is removed. */
    public void onMobHeadRemoved(String dim, int x, int y, int z) {
        if (!initialized) return;
        long hpack = packPos(x, y, z);
        BlockPos hp = new BlockPos(x, y, z);
        for (ShrineEntry s : shrinesNearHeadVc(dim, hp)) {
            s.headPosSet().remove(hpack);
        }
    }

    private boolean isHeadEligible(String dim, String skullType) {
        HeadTypeEntry e = HeadTypeRegistry.get(skullType);
        return e != null && e.enabled() && e.isActiveIn(dim);
    }

    private void rebuildHeadPosSetForShrine(String dim, ShrineEntry shrine) {
        shrine.headPosSet().clear();
        if (headTracker == null) return;
        for (HeadTracker.HeadEntry h : headTracker.getHeadsInDimension(dim)) {
            if (!isHeadEligible(dim, h.skullType())) continue;
            BlockPos hp = new BlockPos(h.x(), h.y(), h.z());
            if (bypassContainsAnchor(shrine, hp)) {
                shrine.headPosSet().add(packPos(h.x(), h.y(), h.z()));
            }
        }
    }

    private Set<Long> unionHeadPosSet(Set<ShrineEntry> entries) {
        Set<Long> u = new HashSet<>();
        for (ShrineEntry e : entries) {
            u.addAll(e.headPosSet());
        }
        return u;
    }

    private List<EntityType<?>> buildConvertPool(String dim, Set<Long> unionHeadPositions) {
        List<EntityType<?>> pool = new ArrayList<>();
        if (headTracker == null) return pool;
        for (long packed : unionHeadPositions) {
            BlockPos p = BlockPos.of(packed);
            HeadTracker.HeadEntry he = headTracker.getHeadAt(dim, p.getX(), p.getY(), p.getZ());
            if (he == null) continue;
            HeadTypeEntry e = HeadTypeRegistry.get(he.skullType());
            if (e == null || !e.enabled() || !e.isActiveIn(dim)) continue;
            if (e.entityType() != null && e.convertEnabled()) {
                pool.add(e.entityType());
            }
        }
        return pool;
    }

    private Set<ShrineEntry> shrinesWhoseBypassContains(String dim, BlockPos pos) {
        int rx = CivilConfig.farmShrineRangeX;
        int rz = CivilConfig.farmShrineRangeZ;
        int ry = CivilConfig.farmShrineRangeY;
        int pvcx = pos.getX() >> 4;
        int pvcz = pos.getZ() >> 4;
        int pvcy = Math.floorDiv(pos.getY(), 16);
        Set<Long> seen = new HashSet<>();
        Set<ShrineEntry> out = new HashSet<>();
        var dimIndex = shrinesByVcXZ.get(dim);
        if (dimIndex == null) return out;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                var syMap = dimIndex.get(packVcXZ(pvcx + dx, pvcz + dz));
                if (syMap == null) continue;
                for (var cell : syMap.values()) {
                    if (cell == null) continue;
                    for (ShrineEntry s : cell.values()) {
                        if (!s.activated()) continue;
                        long k = packPos(s.x(), s.y(), s.z());
                        if (!seen.add(k)) continue;
                        if (bypassContainsAnchor(s, pos)) {
                            out.add(s);
                        }
                    }
                }
            }
        }
        return out;
    }

    private Set<ShrineEntry> shrinesWithinAttractRadius(String dim, BlockPos pos) {
        double maxR = CivilConfig.farmShrineAttractMaxRadius;
        int maxDist = (int) Math.rint(maxR);
        if (maxDist < 0) {
            maxDist = 0;
        }
        boolean aligned = Math.abs(maxR - maxDist) < 1e-3 && (maxDist % 16 == 0);
        int vcRadius = aligned
                ? (maxDist <= 0 ? 0 : (maxDist + 16) / 16)
                : (int) Math.ceil((maxR + 16.0) / 16.0);
        int centerVCX = pos.getX() >> 4;
        int centerVCZ = pos.getZ() >> 4;
        double px = pos.getX() + 0.5;
        double py = pos.getY() + 0.5;
        double pz = pos.getZ() + 0.5;
        double maxRSq = maxR * maxR;
        Set<Long> seen = new HashSet<>();
        Set<ShrineEntry> out = new HashSet<>();
        var dimIndex = shrinesByVcXZ.get(dim);
        if (dimIndex == null) return out;
        for (int dx = -vcRadius; dx <= vcRadius; dx++) {
            for (int dz = -vcRadius; dz <= vcRadius; dz++) {
                var syMap = dimIndex.get(packVcXZ(centerVCX + dx, centerVCZ + dz));
                if (syMap == null) continue;
                for (var cell : syMap.values()) {
                    if (cell == null) continue;
                    for (ShrineEntry s : cell.values()) {
                        if (!s.activated()) continue;
                        long k = packPos(s.x(), s.y(), s.z());
                        if (!seen.add(k)) continue;
                        double ax = s.x() + 0.5;
                        double ay = s.y() + 0.5;
                        double az = s.z() + 0.5;
                        double ddx = ax - px;
                        double ddy = ay - py;
                        double ddz = az - pz;
                        if (ddx * ddx + ddy * ddy + ddz * ddz <= maxRSq) {
                            out.add(s);
                        }
                    }
                }
            }
        }
        return out;
    }

    private List<ShrineEntry> shrinesNearHeadVc(String dim, BlockPos headPos) {
        int rx = CivilConfig.farmShrineRangeX;
        int rz = CivilConfig.farmShrineRangeZ;
        int ry = CivilConfig.farmShrineRangeY;
        int hvcx = headPos.getX() >> 4;
        int hvcz = headPos.getZ() >> 4;
        int hvcy = Math.floorDiv(headPos.getY(), 16);
        List<ShrineEntry> list = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        var dimIndex = shrinesByVcXZ.get(dim);
        if (dimIndex == null) return list;
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                var syMap = dimIndex.get(packVcXZ(hvcx + dx, hvcz + dz));
                if (syMap == null) continue;
                for (int sy = hvcy - ry; sy <= hvcy + ry; sy++) {
                    var cell = syMap.get(sy);
                    if (cell == null) continue;
                    for (ShrineEntry s : cell.values()) {
                        long k = packPos(s.x(), s.y(), s.z());
                        if (seen.add(k)) {
                            list.add(s);
                        }
                    }
                }
            }
        }
        return list;
    }

    private static boolean bypassContainsAnchor(ShrineEntry shrine, BlockPos p) {
        int rx = CivilConfig.farmShrineRangeX;
        int rz = CivilConfig.farmShrineRangeZ;
        int ry = CivilConfig.farmShrineRangeY;
        int ax = shrine.x();
        int ay = shrine.y();
        int az = shrine.z();
        int avcx = ax >> 4;
        int avcz = az >> 4;
        int avcy = Math.floorDiv(ay, 16);
        int pvcx = p.getX() >> 4;
        int pvcz = p.getZ() >> 4;
        int pvcy = Math.floorDiv(p.getY(), 16);
        return Math.abs(pvcx - avcx) <= rx
                && Math.abs(pvcz - avcz) <= rz
                && Math.abs(pvcy - avcy) <= ry;
    }

    private ConcurrentHashMap<Long, ShrineEntry> getOrCreateDim(String dim) {
        return shrines.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
    }

    private void indexShrine(String dim, ShrineEntry entry) {
        int vcx = entry.x() >> 4;
        int vcz = entry.z() >> 4;
        int sy = Math.floorDiv(entry.y(), 16);
        long bucketKey = packVcXZ(vcx, vcz);
        long posKey = packPos(entry.x(), entry.y(), entry.z());
        shrinesByVcXZ
                .computeIfAbsent(dim, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(bucketKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(sy, k -> new ConcurrentHashMap<>())
                .put(posKey, entry);
    }

    private void unindexShrine(String dim, ShrineEntry entry) {
        var dimIndex = shrinesByVcXZ.get(dim);
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
        if (dimIndex.isEmpty()) shrinesByVcXZ.remove(dim);
    }

    private static long packPos(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    private static long packVcXZ(int vcx, int vcz) {
        return (((long) vcx) << 32) ^ (vcz & 0xffffffffL);
    }
}
