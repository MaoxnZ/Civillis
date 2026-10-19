package civil.compat.bandsync;

import civil.CivilMod;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Client L2 snapshot: one small NBT per dimension. Written on session end (E3) and when chunk-band
 * subscription drops to zero mid-session; read on session begin (E1), dimension change, and when
 * subscription resumes so L1 can warm from disk before S2C catches up.
 */
public final class ChunkBandClientSnapshotStore {

    private static final int SCHEMA_VERSION = 1;

    private ChunkBandClientSnapshotStore() {
    }

    public static Path baseDirForSession(ChunkBandSessionKey key, Minecraft mc) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mc, "mc");
        if (key.integrated()) {
            return key.integratedWorldRoot()
                    .resolve("data")
                    .resolve("civil")
                    .resolve("chunk_band_cache");
        }
        Path gameDir = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        return gameDir.resolve("civil").resolve("chunk_band_cache").resolve(key.dirSegment());
    }

    private static Path fileForDimension(Path baseDir, String dimensionId) {
        String safe = dimensionId.replace(':', '_');
        return baseDir.resolve("chunk_band_dim_" + safe + ".nbt");
    }

    public static void writeSessionSnapshots(ChunkBandSessionKey key, Map<String, Long2ByteOpenHashMap> byDim, Minecraft mc) {
        if (byDim.isEmpty()) {
            return;
        }
        Path base = baseDirForSession(key, mc);
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.warn("[bandsync][client] snapshot mkdir failed {}: {}", base, e.getMessage());
            }
            return;
        }
        for (var e : byDim.entrySet()) {
            writeOneDimension(base, key, e.getKey(), e.getValue());
        }
    }

    private static void writeOneDimension(Path baseDir, ChunkBandSessionKey key, String dimensionId, Long2ByteOpenHashMap map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", SCHEMA_VERSION);
        root.putString("sessionFingerprint", key.fingerprintHex());
        root.putString("dimensionId", dimensionId);
        ListTag list = new ListTag();
        for (var it = map.long2ByteEntrySet().fastIterator(); it.hasNext(); ) {
            var en = it.next();
            CompoundTag row = new CompoundTag();
            row.putLong("pk", en.getLongKey());
            row.putByte("band", en.getByteValue());
            list.add(row);
        }
        root.put("chunks", list);
        Path out = fileForDimension(baseDir, dimensionId);
        try {
            NbtIo.writeCompressed(root, out);
        } catch (IOException ex) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.warn("[bandsync][client] snapshot write failed {}: {}", out, ex.getMessage());
            }
        }
    }

    public static void loadAndMergeDimension(ChunkBandSessionKey key, String dimensionId, Minecraft mc) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Path base = baseDirForSession(key, mc);
        Path file = fileForDimension(base, dimensionId);
        tryMergeSnapshotFile(file, key, dimensionId);
    }

    /**
     * Merges every {@code chunk_band_dim_*.nbt} in this session's snapshot dir (same validation as single-dimension load).
     * Used after chunk-band subscription resumes so all explored dims rehydrate from L2 without re-walking.
     */
    public static void loadAllPersistedDimensions(ChunkBandSessionKey key, Minecraft mc) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(mc, "mc");
        Path base = baseDirForSession(key, mc);
        if (!Files.isDirectory(base)) {
            return;
        }
        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("chunk_band_dim_") && n.endsWith(".nbt");
                    })
                    .forEach(p -> tryMergeSnapshotFile(p, key, null));
        } catch (IOException e) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.warn("[bandsync][client] snapshot list/read failed {}: {}", base, e.getMessage());
            }
        }
    }

    /**
     * @param expectedDimensionId if non-null, must equal {@code dimensionId} inside the NBT (path/dim consistency).
     */
    private static void tryMergeSnapshotFile(Path file, ChunkBandSessionKey key, String expectedDimensionId) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.warn("[bandsync][client] snapshot read failed {}: {}", file, e.getMessage());
            }
            return;
        }
        if (root == null) {
            return;
        }
        if (root.getInt("schemaVersion") != SCHEMA_VERSION) {
            return;
        }
        if (!key.fingerprintHex().equals(root.getString("sessionFingerprint"))) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[bandsync][client] snapshot fingerprint mismatch, discarding {}", file);
            }
            return;
        }
        String dimensionId = root.getString("dimensionId");
        if (expectedDimensionId != null && !expectedDimensionId.equals(dimensionId)) {
            return;
        }
        ListTag list = root.getList("chunks", Tag.TAG_COMPOUND);
        if (list == null || list.isEmpty()) {
            return;
        }
        Long2ByteOpenHashMap data = new Long2ByteOpenHashMap(list.size());
        data.defaultReturnValue(ChunkBandClientCache.MISSING_BAND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            data.put(row.getLong("pk"), row.getByte("band"));
        }
        ChunkBandClientCache.mergePersistedDimension(dimensionId, data);
    }
}
