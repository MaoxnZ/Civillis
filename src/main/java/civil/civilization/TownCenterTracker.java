package civil.civilization;

import civil.civilization.storage.CivilStorage;
import civil.config.CivilConfig;
import civil.towncenter.TownCenterLevelTable;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spatial tracker for town center lectern positions. Persisted via {@link CivilStorage}.
 */
public final class TownCenterTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-town-center");

    public enum LevelEffectChoice {
        APPLIED,
        SKIPPED
    }

    public record Member(UUID uuid, String name) {}

    public record AppliedEffect(
            String zoneBuffOfferId,
            String effectId,
            int amplifier,
            int chosenAtLevel,
            boolean ambient,
            boolean showParticles,
            boolean showIcon) {}

    public record TownCenterEntry(
            int x, int y, int z,
            int level,
            boolean activated,
            long deactivateDeadlineTick,
            String displayName,
            UUID creatorUuid,
            String creatorName,
            List<AppliedEffect> appliedEffects,
            Map<Integer, LevelEffectChoice> levelEffectState,
            boolean openRegistration,
            List<Member> members) {

        public boolean isGameplayActive(long gameTime) {
            return activated || (deactivateDeadlineTick > 0 && deactivateDeadlineTick > gameTime);
        }

        public boolean isCreator(UUID uuid) {
            return creatorUuid != null && creatorUuid.equals(uuid);
        }

        public boolean isMember(UUID uuid) {
            if (isCreator(uuid)) return false;
            for (Member m : members) {
                if (m.uuid().equals(uuid)) return true;
            }
            return false;
        }

        public boolean hasBenefit(UUID uuid) {
            return isCreator(uuid) || isMember(uuid);
        }

        public boolean hasShutdownDeadline(long gameTime) {
            return deactivateDeadlineTick > 0 && deactivateDeadlineTick > gameTime;
        }
    }

    public record AuthorizedTcView(int x, int y, int z, int level, TownCenterAabb region) {}

    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, TownCenterEntry>> centers =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, ConcurrentHashMap<Integer, ConcurrentHashMap<Long, TownCenterEntry>>>>
            centersByVcXZ = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>> maxLevelClaimCountsByDim =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Boolean>> maxLevelClaimedCentersByDim =
            new ConcurrentHashMap<>();

    private volatile CivilStorage storage;
    private volatile boolean initialized;
    private volatile boolean townCentersDirty;

    public void initialize(CivilStorage civilStorage) {
        this.storage = civilStorage;
        this.townCentersDirty = false;
        centers.clear();
        centersByVcXZ.clear();
        maxLevelClaimCountsByDim.clear();
        maxLevelClaimedCentersByDim.clear();

        List<CivilStorage.StoredTownCenter> stored = civilStorage.loadTownCenters();
        for (CivilStorage.StoredTownCenter t : stored) {
            putEntry(t.dim(), t.entry());
        }

        initialized = true;
        LOGGER.info("[civil-town-center] Loaded {} town center(s) from storage", stored.size());
    }

    public void shutdown() {
        initialized = false;
        centers.clear();
        centersByVcXZ.clear();
        maxLevelClaimCountsByDim.clear();
        maxLevelClaimedCentersByDim.clear();
        storage = null;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isTownCentersDirty() {
        return townCentersDirty;
    }

    public void clearTownCentersDirty() {
        townCentersDirty = false;
    }

    public List<CivilStorage.StoredTownCenter> snapshotAllTownCenters() {
        List<CivilStorage.StoredTownCenter> out = new ArrayList<>();
        for (var dimEntry : centers.entrySet()) {
            String dim = dimEntry.getKey();
            for (TownCenterEntry e : dimEntry.getValue().values()) {
                out.add(new CivilStorage.StoredTownCenter(dim, e));
            }
        }
        return out;
    }

    public boolean hasEntry(String dim, int x, int y, int z) {
        if (!initialized) return false;
        var dimMap = centers.get(dim);
        return dimMap != null && dimMap.containsKey(packPos(x, y, z));
    }

    public TownCenterEntry getEntry(String dim, int x, int y, int z) {
        if (!initialized) return null;
        var dimMap = centers.get(dim);
        if (dimMap == null) return null;
        return dimMap.get(packPos(x, y, z));
    }

    public int countAll() {
        int n = 0;
        for (var dimMap : centers.values()) {
            n += dimMap.size();
        }
        return n;
    }

    public boolean add(String dim, int x, int y, int z, TownCenterEntry entry) {
        if (!initialized) return false;
        if (countAll() >= CivilConfig.townCenterMaxCount) return false;
        putEntry(dim, entry);
        return true;
    }

    public boolean add(String dim, int x, int y, int z, int level, boolean activated,
                       UUID creatorUuid, String creatorName) {
        TownCenterEntry entry = new TownCenterEntry(
                x, y, z, level, activated, 0L,
                "", creatorUuid, creatorName == null ? "" : creatorName,
                List.of(), Map.of(),
                false, List.of());
        return add(dim, x, y, z, entry);
    }

    public void remove(String dim, int x, int y, int z) {
        if (!initialized) return;
        var dimMap = centers.get(dim);
        if (dimMap == null) return;
        TownCenterEntry removed = dimMap.remove(packPos(x, y, z));
        if (removed != null) {
            unindexCenter(dim, removed);
            townCentersDirty = true;
            if (dimMap.isEmpty()) centers.remove(dim);
        }
    }

    public void setActivated(String dim, int x, int y, int z, boolean activated) {
        replace(dim, x, y, z, cur -> new TownCenterEntry(
                x, y, z, cur.level(), activated, activated ? 0L : cur.deactivateDeadlineTick(),
                cur.displayName(), cur.creatorUuid(), cur.creatorName(),
                cur.appliedEffects(), cur.levelEffectState(),
                cur.openRegistration(), cur.members()));
    }

    public void setShutdownDeadline(String dim, int x, int y, int z, long deadlineTick) {
        replace(dim, x, y, z, cur -> new TownCenterEntry(
                x, y, z, cur.level(), cur.activated(), deadlineTick,
                cur.displayName(), cur.creatorUuid(), cur.creatorName(),
                cur.appliedEffects(), cur.levelEffectState(),
                cur.openRegistration(), cur.members()));
    }

    public void setLevel(String dim, int x, int y, int z, int level) {
        replace(dim, x, y, z, cur -> new TownCenterEntry(
                x, y, z, level, cur.activated(), cur.deactivateDeadlineTick(),
                cur.displayName(), cur.creatorUuid(), cur.creatorName(),
                cur.appliedEffects(), cur.levelEffectState(),
                cur.openRegistration(), cur.members()));
    }

    public void setDisplayName(String dim, int x, int y, int z, String displayName) {
        replace(dim, x, y, z, cur -> new TownCenterEntry(
                x, y, z, cur.level(), cur.activated(), cur.deactivateDeadlineTick(),
                displayName == null ? "" : displayName, cur.creatorUuid(), cur.creatorName(),
                cur.appliedEffects(), cur.levelEffectState(),
                cur.openRegistration(), cur.members()));
    }

    public void setOpenRegistration(String dim, int x, int y, int z, boolean open) {
        replace(dim, x, y, z, cur -> new TownCenterEntry(
                x, y, z, cur.level(), cur.activated(), cur.deactivateDeadlineTick(),
                cur.displayName(), cur.creatorUuid(), cur.creatorName(),
                cur.appliedEffects(), cur.levelEffectState(),
                open, cur.members()));
    }

    public void addMember(String dim, int x, int y, int z, UUID uuid, String name) {
        replace(dim, x, y, z, cur -> {
            List<Member> next = new ArrayList<>(cur.members());
            next.add(new Member(uuid, name == null ? "" : name));
            return new TownCenterEntry(
                    x, y, z, cur.level(), cur.activated(), cur.deactivateDeadlineTick(),
                    cur.displayName(), cur.creatorUuid(), cur.creatorName(),
                    cur.appliedEffects(), cur.levelEffectState(),
                    cur.openRegistration(), List.copyOf(next));
        });
    }

    public void removeMember(String dim, int x, int y, int z, UUID uuid) {
        replace(dim, x, y, z, cur -> {
            List<Member> next = new ArrayList<>();
            for (Member m : cur.members()) {
                if (!m.uuid().equals(uuid)) next.add(m);
            }
            return new TownCenterEntry(
                    x, y, z, cur.level(), cur.activated(), cur.deactivateDeadlineTick(),
                    cur.displayName(), cur.creatorUuid(), cur.creatorName(),
                    cur.appliedEffects(), cur.levelEffectState(),
                    cur.openRegistration(), List.copyOf(next));
        });
    }

    public void setLevelEffectChoice(String dim, int x, int y, int z, int targetLevel, LevelEffectChoice choice) {
        replace(dim, x, y, z, cur -> {
            var next = new java.util.HashMap<>(cur.levelEffectState());
            next.put(targetLevel, choice);
            return new TownCenterEntry(
                    x, y, z, cur.level(), cur.activated(), cur.deactivateDeadlineTick(),
                    cur.displayName(), cur.creatorUuid(), cur.creatorName(),
                    cur.appliedEffects(), Map.copyOf(next),
                    cur.openRegistration(), cur.members());
        });
    }

    public void addAppliedEffect(String dim, int x, int y, int z, AppliedEffect effect) {
        replace(dim, x, y, z, cur -> {
            List<AppliedEffect> next = new ArrayList<>(cur.appliedEffects());
            next.add(effect);
            return new TownCenterEntry(
                    x, y, z, cur.level(), cur.activated(), cur.deactivateDeadlineTick(),
                    cur.displayName(), cur.creatorUuid(), cur.creatorName(),
                    List.copyOf(next), cur.levelEffectState(),
                    cur.openRegistration(), cur.members());
        });
    }

    public void finalizeShutdown(String dim, int x, int y, int z) {
        replace(dim, x, y, z, cur -> new TownCenterEntry(
                x, y, z, cur.level(), false, 0L,
                cur.displayName(), cur.creatorUuid(), cur.creatorName(),
                cur.appliedEffects(), cur.levelEffectState(),
                cur.openRegistration(), cur.members()));
    }

    public boolean isClaimedByMaxLevelTownCenter(String dim, VoxelChunkKey vc) {
        if (!initialized) return false;
        var dimClaims = maxLevelClaimCountsByDim.get(dim);
        return dimClaims != null && dimClaims.containsKey(claimKey(vc));
    }

    public void rebuildMaxLevelClaims(long gameTime) {
        maxLevelClaimCountsByDim.clear();
        maxLevelClaimedCentersByDim.clear();
        if (!initialized) return;
        for (var dimEntry : centers.entrySet()) {
            String dim = dimEntry.getKey();
            for (TownCenterEntry entry : dimEntry.getValue().values()) {
                if (shouldClaim(entry, gameTime)) {
                    addClaimAabb(dim, entry);
                }
            }
        }
    }

    public void refreshMaxLevelClaim(String dim, TownCenterEntry oldEntry, TownCenterEntry newEntry, long gameTime) {
        if (!initialized) return;
        if (oldEntry != null && shouldClaim(oldEntry, gameTime)) {
            removeClaimAabb(dim, oldEntry);
        }
        if (newEntry != null && shouldClaim(newEntry, gameTime)) {
            addClaimAabb(dim, newEntry);
        }
    }

    public void removeMaxLevelClaimIfPresent(String dim, TownCenterEntry entry) {
        if (!initialized || entry == null) return;
        if (entry.level() >= TownCenterLevelTable.maxLevel()
                && (entry.activated() || entry.deactivateDeadlineTick() > 0)) {
            removeClaimAabb(dim, entry);
        }
    }

    public List<AuthorizedTcView> collectAuthorizedViews(String dim, UUID playerUuid, long gameTime) {
        List<AuthorizedTcView> out = new ArrayList<>();
        var dimMap = centers.get(dim);
        if (dimMap == null) return out;
        for (TownCenterEntry e : dimMap.values()) {
            if (!e.isGameplayActive(gameTime)) continue;
            if (!e.hasBenefit(playerUuid)) continue;
            BlockPos lectern = new BlockPos(e.x(), e.y(), e.z());
            out.add(new AuthorizedTcView(
                    e.x(), e.y(), e.z(), e.level(),
                    TownCenterAabb.atLectern(lectern, e.level())));
        }
        return out;
    }

    public List<TownCenterEntry> entriesCoveringVc(String dim, VoxelChunkKey vc) {
        List<TownCenterEntry> out = new ArrayList<>();
        var dimMap = centers.get(dim);
        if (dimMap == null) return out;
        for (TownCenterEntry e : dimMap.values()) {
            BlockPos lectern = new BlockPos(e.x(), e.y(), e.z());
            TownCenterAabb aabb = TownCenterAabb.atLectern(lectern, e.level());
            if (aabb.contains(vc)) out.add(e);
        }
        return out;
    }

    public Iterable<TownCenterEntry> allEntriesInDim(String dim) {
        var dimMap = centers.get(dim);
        if (dimMap == null) return List.of();
        return dimMap.values();
    }

    /** Only entries visible to gameplay (activated or shutdown countdown in progress). */
    public void forEachGameplayActive(String dim, long gameTime, java.util.function.Consumer<TownCenterEntry> consumer) {
        if (!initialized) return;
        var dimMap = centers.get(dim);
        if (dimMap == null) return;
        for (TownCenterEntry e : dimMap.values()) {
            if (e.isGameplayActive(gameTime)) {
                consumer.accept(e);
            }
        }
    }

    public boolean passesSpacing(String dim, int x, int y, int z) {
        VoxelChunkKey anchor = VoxelChunkKey.from(new BlockPos(x, y, z));
        int h = CivilConfig.townCenterMinSpacingHorizontal;
        int v = CivilConfig.townCenterMinSpacingVertical;
        var dimMap = centers.get(dim);
        if (dimMap == null) return true;
        for (TownCenterEntry other : dimMap.values()) {
            VoxelChunkKey otherAnchor = VoxelChunkKey.from(new BlockPos(other.x(), other.y(), other.z()));
            int dx = Math.abs(anchor.getCx() - otherAnchor.getCx());
            int dz = Math.abs(anchor.getCz() - otherAnchor.getCz());
            int dy = Math.abs(anchor.getSy() - otherAnchor.getSy());
            if (Math.max(dx, dz) < h && dy < v) {
                return false;
            }
        }
        return true;
    }

    private void putEntry(String dim, TownCenterEntry entry) {
        long key = packPos(entry.x(), entry.y(), entry.z());
        var dimMap = getOrCreateDim(dim);
        TownCenterEntry prev = dimMap.put(key, entry);
        if (prev == null) {
            indexCenter(dim, entry);
        } else {
            updateIndexedCenter(dim, prev, entry);
        }
        townCentersDirty = true;
    }

    private void replace(String dim, int x, int y, int z, java.util.function.UnaryOperator<TownCenterEntry> op) {
        if (!initialized) return;
        var dimMap = centers.get(dim);
        if (dimMap == null) return;
        long key = packPos(x, y, z);
        TownCenterEntry cur = dimMap.get(key);
        if (cur == null) return;
        TownCenterEntry next = op.apply(cur);
        dimMap.put(key, next);
        updateIndexedCenter(dim, cur, next);
        townCentersDirty = true;
    }

    private ConcurrentHashMap<Long, TownCenterEntry> getOrCreateDim(String dim) {
        return centers.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
    }

    private void indexCenter(String dim, TownCenterEntry entry) {
        int vcx = entry.x() >> 4;
        int vcz = entry.z() >> 4;
        int sy = Math.floorDiv(entry.y(), 16);
        long bucketKey = packVcXZ(vcx, vcz);
        long posKey = packPos(entry.x(), entry.y(), entry.z());
        centersByVcXZ
                .computeIfAbsent(dim, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(bucketKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(sy, k -> new ConcurrentHashMap<>())
                .put(posKey, entry);
    }

    private void unindexCenter(String dim, TownCenterEntry entry) {
        var dimIndex = centersByVcXZ.get(dim);
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
        if (dimIndex.isEmpty()) centersByVcXZ.remove(dim);
    }

    private void updateIndexedCenter(String dim, TownCenterEntry oldEntry, TownCenterEntry newEntry) {
        unindexCenter(dim, oldEntry);
        indexCenter(dim, newEntry);
    }

    private static boolean shouldClaim(TownCenterEntry entry, long gameTime) {
        return entry.level() >= TownCenterLevelTable.maxLevel() && entry.isGameplayActive(gameTime);
    }

    private void addClaimAabb(String dim, TownCenterEntry entry) {
        var claimedCenters = maxLevelClaimedCentersByDim.computeIfAbsent(dim, ignored -> new ConcurrentHashMap<>());
        if (claimedCenters.putIfAbsent(packPos(entry.x(), entry.y(), entry.z()), Boolean.TRUE) != null) {
            return;
        }
        updateClaimAabb(dim, entry, 1);
    }

    private void removeClaimAabb(String dim, TownCenterEntry entry) {
        var claimedCenters = maxLevelClaimedCentersByDim.get(dim);
        if (claimedCenters == null || claimedCenters.remove(packPos(entry.x(), entry.y(), entry.z())) == null) {
            return;
        }
        if (claimedCenters.isEmpty()) {
            maxLevelClaimedCentersByDim.remove(dim, claimedCenters);
        }
        updateClaimAabb(dim, entry, -1);
    }

    private void updateClaimAabb(String dim, TownCenterEntry entry, int delta) {
        TownCenterAabb aabb = TownCenterAabb.atLectern(new BlockPos(entry.x(), entry.y(), entry.z()), entry.level());
        var dimClaims = maxLevelClaimCountsByDim.computeIfAbsent(dim, unused -> new ConcurrentHashMap<>());
        for (int cx = aabb.minVc().getCx(); cx <= aabb.maxVc().getCx(); cx++) {
            for (int cz = aabb.minVc().getCz(); cz <= aabb.maxVc().getCz(); cz++) {
                for (int sy = aabb.minVc().getSy(); sy <= aabb.maxVc().getSy(); sy++) {
                    long key = claimKey(cx, cz, sy);
                    dimClaims.compute(key, (unused, count) -> {
                        int next = (count == null ? 0 : count) + delta;
                        return next > 0 ? next : null;
                    });
                }
            }
        }
        if (dimClaims.isEmpty()) {
            maxLevelClaimCountsByDim.remove(dim, dimClaims);
        }
    }

    private static long packPos(int x, int y, int z) {
        return BlockPos.asLong(x, y, z);
    }

    private static long packVcXZ(int vcx, int vcz) {
        return (((long) vcx) << 32) ^ (vcz & 0xffffffffL);
    }

    private static long claimKey(VoxelChunkKey vc) {
        return claimKey(vc.getCx(), vc.getCz(), vc.getSy());
    }

    private static long claimKey(int cx, int cz, int sy) {
        return BlockPos.asLong(cx, sy, cz);
    }
}
