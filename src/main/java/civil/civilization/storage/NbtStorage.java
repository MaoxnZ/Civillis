package civil.civilization.storage;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.civilization.VoxelChunkKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * NBT-based storage implementation. Replaces H2.
 *
 * <p>Phase 0: Stub — structure load returns empty, L1 returns null/empty.
 * Phase 1: meta, mob_heads, undying_anchors load/flush.
 * Phase 2–4: L1 region format, bulk load, unified flush.
 */
public final class NbtStorage implements CivilStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-storage");

    /** Recreated on re-initialize after close; executor is terminated after close(). */
    private volatile ColdIOQueue coldQueue = new ColdIOQueue();
    private volatile Path basePath;
    private volatile boolean closed;

    @Override
    public void initialize(ServerLevel world) {
        basePath = world.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve("civil");
        try {
            Files.createDirectories(basePath);
        } catch (Exception e) {
            LOGGER.warn("[civil-storage] Could not create data dir: {}", e.getMessage());
        }
        if (closed) {
            coldQueue = new ColdIOQueue();
        }
        closed = false;
        if (CivilMod.DEBUG) {
            LOGGER.info("[civil-storage] NBT storage initialized: {}", basePath);
        }
    }

    @Override
    public void close() {
        closed = true;
        coldQueue.shutdown(5);
    }

    @Override
    public long loadServerClockMillis() {
        Path p = resolve("meta.nbt");
        if (p == null || !Files.isRegularFile(p)) return 0;
        try {
            CompoundTag tag = NbtIo.readCompressed(p, NbtAccounter.unlimitedHeap());
            if (tag != null && tag.contains("serverClockMillis")) {
                return tag.getLong("serverClockMillis").orElse(0L);
            }
        } catch (Exception e) {
            LOGGER.warn("[civil-storage] Failed to load meta.nbt: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public void writeMeta(long serverClockMillis) {
        Path p = resolve("meta.nbt");
        if (p == null) return;
        try {
            CompoundTag tag = new CompoundTag();
            tag.putLong("serverClockMillis", serverClockMillis);
            NbtIo.writeCompressed(tag, p);
        } catch (Exception e) {
            LOGGER.warn("[civil-storage] Failed to write meta.nbt: {}", e.getMessage());
        }
    }

    @Override
    public List<StoredL1Entry> loadAllL1() {
        return Collections.emptyList();
    }

    @Override
    public Double loadL1Sync(String dim, VoxelChunkKey key) {
        int rx = Math.floorDiv(key.getCx(), 32);
        int rz = Math.floorDiv(key.getCz(), 32);
        Map<VoxelChunkKey, L1Entry> region;
        try {
            region = bulkLoadRegion(dim, rx, rz).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("[civil-storage] Bulk load region ({},{},{}) for L1 interrupted", dim, rx, rz);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn("[civil-storage] Bulk load region ({},{},{}) for L1 failed: {}", dim, rx, rz, e.getMessage());
            return null;
        }
        L1Entry e = region.get(key);
        return e != null ? e.score() : null;
    }

    @Override
    public CompletableFuture<Void> saveL1Async(String dim, VoxelChunkKey key, CScore cScore) {
        // NBT: no per-key async write; use pendingScoreWrites + unified flush
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public long[] loadPresenceSync(String dim, VoxelChunkKey key) {
        int rx = Math.floorDiv(key.getCx(), 32);
        int rz = Math.floorDiv(key.getCz(), 32);
        Map<VoxelChunkKey, L1Entry> region;
        try {
            region = bulkLoadRegion(dim, rx, rz).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("[civil-storage] Bulk load region ({},{},{}) for presence interrupted", dim, rx, rz);
            return null;
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn("[civil-storage] Bulk load region ({},{},{}) for presence failed: {}", dim, rx, rz, e.getMessage());
            return null;
        }
        L1Entry e = region.get(key);
        if (e == null || (e.presenceTime() == 0 && e.lastRecoveryTime() == 0)) return null;
        return new long[] { e.presenceTime(), e.lastRecoveryTime() };
    }

    @Override
    public CompletableFuture<Void> batchSavePresenceAsync(List<PresenceSaveRequest> requests) {
        return CompletableFuture.completedFuture(null);
    }

    private static final String MOB_HEADS_FILE = "mob_heads.nbt";
    private static final String UNDYING_ANCHORS_FILE = "undying_anchors.nbt";

    @Override
    public List<StoredMobHead> loadMobHeads() {
        Path p = resolve(MOB_HEADS_FILE);
        if (p == null || !Files.isRegularFile(p)) return Collections.emptyList();
        try {
            CompoundTag root = NbtIo.readCompressed(p, NbtAccounter.unlimitedHeap());
            if (root == null || !root.contains("entries")) return Collections.emptyList();
            ListTag entries = root.getList("entries").orElse(new ListTag());
            List<StoredMobHead> out = new ArrayList<>(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag e = entries.getCompound(i).orElse(new CompoundTag());
                out.add(new StoredMobHead(
                        e.getString("dim").orElse(""),
                        e.getInt("x").orElse(0), e.getInt("y").orElse(0), e.getInt("z").orElse(0),
                        e.getString("skullType").orElse("")));
            }
            return out;
        } catch (Exception e) {
            LOGGER.warn("[civil-storage] Failed to load mob_heads.nbt: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void writeMobHeads(List<StoredMobHead> snapshot) {
        Path p = resolve(MOB_HEADS_FILE);
        if (p == null) return;
        try {
            ListTag entries = new ListTag();
            for (StoredMobHead h : snapshot) {
                CompoundTag e = new CompoundTag();
                e.putString("dim", h.dim());
                e.putInt("x", h.x());
                e.putInt("y", h.y());
                e.putInt("z", h.z());
                e.putString("skullType", h.skullType());
                entries.add(e);
            }
            CompoundTag root = new CompoundTag();
            root.put("entries", entries);
            NbtIo.writeCompressed(root, p);
        } catch (Exception e) {
            LOGGER.warn("[civil-storage] Failed to write mob_heads.nbt: {}", e.getMessage());
        }
    }

    @Override
    public List<StoredUndyingAnchor> loadUndyingAnchors() {
        Path p = resolve(UNDYING_ANCHORS_FILE);
        if (p == null || !Files.isRegularFile(p)) return Collections.emptyList();
        try {
            CompoundTag root = NbtIo.readCompressed(p, NbtAccounter.unlimitedHeap());
            if (root == null || !root.contains("entries")) return Collections.emptyList();
            ListTag entries = root.getList("entries").orElse(new ListTag());
            List<StoredUndyingAnchor> out = new ArrayList<>(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag e = entries.getCompound(i).orElse(new CompoundTag());
                out.add(new StoredUndyingAnchor(
                        e.getString("dim").orElse(""),
                        e.getInt("x").orElse(0), e.getInt("y").orElse(0), e.getInt("z").orElse(0),
                        e.getBoolean("activated").orElse(false),
                        e.getLong("lastUsedGlobal").orElse(0L)));
            }
            return out;
        } catch (Exception e) {
            LOGGER.warn("[civil-storage] Failed to load undying_anchors.nbt: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void writeUndyingAnchors(List<StoredUndyingAnchor> snapshot) {
        Path p = resolve(UNDYING_ANCHORS_FILE);
        if (p == null) return;
        try {
            ListTag entries = new ListTag();
            for (StoredUndyingAnchor a : snapshot) {
                CompoundTag e = new CompoundTag();
                e.putString("dim", a.dim());
                e.putInt("x", a.x());
                e.putInt("y", a.y());
                e.putInt("z", a.z());
                e.putBoolean("activated", a.activated());
                e.putLong("lastUsedGlobal", a.lastUsedGlobal());
                entries.add(e);
            }
            CompoundTag root = new CompoundTag();
            root.put("entries", entries);
            NbtIo.writeCompressed(root, p);
        } catch (Exception e) {
            LOGGER.warn("[civil-storage] Failed to write undying_anchors.nbt: {}", e.getMessage());
        }
    }

    @Override
    public CompletableFuture<List<StoredL1Entry>> loadL1RangeAsync(
            String dim, int minCx, int maxCx, int minCz, int maxCz, int sy) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<Void> batchSaveL1Async(List<L1SaveRequest> requests) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Map<VoxelChunkKey, L1Entry>> bulkLoadRegion(String dim, int rx, int rz) {
        return coldQueue.submit(() -> loadL1Region(dim, rx, rz));
    }

    @Override
    public Map<VoxelChunkKey, L1Entry> loadL1RegionSync(String dim, int rx, int rz) {
        return loadL1Region(dim, rx, rz);
    }

    // ---------- L1 region format: l1/{dim}/r.{rx}.{rz}.nbt ----------
    private static final String L1_DIR = "l1";

    /** Convert dimension string to a filesystem-safe path segment (Windows disallows : in paths). */
    private static String dimToPathSegment(String dim) {
        if (dim == null || dim.isEmpty()) return "unknown";
        return dim.replace(':', '_').replace('/', '_').replace('\\', '_')
                .replace('*', '_').replace('?', '_').replace('"', '_')
                .replace('<', '_').replace('>', '_').replace('|', '_')
                .replace('[', '_').replace(']', '_').replace(' ', '_');
    }

    /** Sync load one region. Returns empty map if file missing. */
    Map<VoxelChunkKey, L1Entry> loadL1Region(String dim, int rx, int rz) {
        Path p = resolve(L1_DIR, dimToPathSegment(dim), "r." + rx + "." + rz + ".nbt");
        if (p == null || !Files.isRegularFile(p)) return Collections.emptyMap();
        try {
            CompoundTag root = NbtIo.readCompressed(p, NbtAccounter.unlimitedHeap());
            if (root == null || !root.contains("entries")) return Collections.emptyMap();
            ListTag entries = root.getList("entries").orElse(new ListTag());
            Map<VoxelChunkKey, L1Entry> out = new HashMap<>(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag e = entries.getCompound(i).orElse(new CompoundTag());
                int cx = e.getInt("cx").orElse(0);
                int cz = e.getInt("cz").orElse(0);
                int sy = e.getInt("sy").orElse(0);
                double score = e.contains("score") ? e.getDouble("score").orElse(0.0) : 0.0;
                long presenceTime = e.getLong("presenceTime").orElse(0L);
                long lastRecoveryTime = e.getLong("lastRecoveryTime").orElse(0L);
                out.put(new VoxelChunkKey(cx, cz, sy), new L1Entry(score, presenceTime, lastRecoveryTime));
            }
            return out;
        } catch (Exception e) {
            LOGGER.warn("[civil-storage] Failed to load L1 region r.{}.{}.nbt: {}", rx, rz, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public void writeL1Region(String dim, int rx, int rz, Map<VoxelChunkKey, L1Entry> data) {
        Path dir = resolve(L1_DIR, dimToPathSegment(dim));
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            LOGGER.warn("[civil-storage] Could not create L1 region dir: {}", e.getMessage());
            return;
        }
        Path p = dir.resolve("r." + rx + "." + rz + ".nbt");
        try {
            ListTag entries = new ListTag();
            for (Map.Entry<VoxelChunkKey, L1Entry> en : data.entrySet()) {
                VoxelChunkKey k = en.getKey();
                L1Entry v = en.getValue();
                // Omit key entirely if score=0 and no presence (per migration plan)
                if (v.score() == 0 && v.presenceTime() == 0 && v.lastRecoveryTime() == 0) continue;
                CompoundTag e = new CompoundTag();
                e.putInt("cx", k.getCx());
                e.putInt("cz", k.getCz());
                e.putInt("sy", k.getSy());
                if (v.score() != 0) e.putDouble("score", v.score());
                if (v.presenceTime() != 0) e.putLong("presenceTime", v.presenceTime());
                if (v.lastRecoveryTime() != 0) e.putLong("lastRecoveryTime", v.lastRecoveryTime());
                entries.add(e);
            }
            CompoundTag root = new CompoundTag();
            root.put("entries", entries);
            NbtIo.writeCompressed(root, p);
        } catch (Exception e) {
            LOGGER.warn("[civil-storage] Failed to write L1 region r.{}.{}.nbt: {}", rx, rz, e.getMessage());
        }
    }

    @Override
    public CompletableFuture<Void> submitOnIO(Runnable task) {
        return coldQueue.submit(() -> {
            task.run();
            return null;
        }).thenApply(x -> null);
    }

    /** For unified flush and bulk load (Phase 2+). */
    public ColdIOQueue getColdQueue() {
        return coldQueue;
    }

    /** Resolve path to a file under data/civil/. */
    public Path resolve(String... segments) {
        Path p = basePath;
        if (p == null) return null;
        for (String s : segments) {
            p = p.resolve(s);
        }
        return p;
    }
}
