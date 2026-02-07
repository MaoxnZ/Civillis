package civil.civilization.storage;

import civil.CivilMod;
import civil.civilization.CScore;
import civil.civilization.cache.simple.LruPyramidCache;
import civil.civilization.structure.L2Entry;
import civil.civilization.structure.L2Key;
import civil.civilization.structure.L3Entry;
import civil.civilization.structure.L3Key;
import civil.civilization.structure.VoxelChunkKey;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * NBT storage: saves L1/L2/L3 cache to world save.
 * 
 * <p>Storage location: world/civil_cache.dat
 * <p>Reads on world load, saves on world unload.
 */
public class NbtStorage {

    private static final String FILE_NAME = "civil_cache.dat";
    
    private final LruPyramidCache cache;
    private Path savePath;

    public NbtStorage(LruPyramidCache cache) {
        this.cache = cache;
    }

    /**
     * Load cache from world save.
     */
    public void load(ServerWorld world) {
        this.savePath = world.getServer().getSavePath(WorldSavePath.ROOT).resolve(FILE_NAME).normalize();
        
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[civil] Attempting to load cache: {}", savePath);
        }
        
        if (!Files.exists(savePath)) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[civil] Cache file does not exist, skipping load: {}", savePath);
            }
            return;
        }

        try {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[civil] Starting to read cache file...");
            }
            NbtCompound nbt = NbtIo.readCompressed(savePath, NbtSizeTracker.ofUnlimitedBytes());
            loadFromNbt(nbt);
        } catch (IOException e) {
            CivilMod.LOGGER.error("[civil] Failed to load cache: {}", e.getMessage(), e);
        } catch (Exception e) {
            CivilMod.LOGGER.error("[civil] Unexpected error while loading cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Save cache to world save.
     */
    public void save() {
        if (savePath == null) {
            CivilMod.LOGGER.warn("[civil] Save path not initialized, skipping save");
            return;
        }

        try {
            NbtCompound nbt = saveToNbt();
            NbtIo.writeCompressed(nbt, savePath);
        } catch (IOException e) {
            CivilMod.LOGGER.error("[civil] Failed to save cache: {}", e.getMessage());
        }
    }

    /**
     * Read cache data from NBT.
     */
    private void loadFromNbt(NbtCompound nbt) {
        long startTime = System.currentTimeMillis();
        int l1Count = 0, l2Count = 0, l3Count = 0;

        // Read L1 cache
        if (nbt.contains("l1")) {
            NbtList l1List = nbt.getListOrEmpty("l1");
            for (int i = 0; i < l1List.size(); i++) {
                NbtCompound entry = l1List.getCompoundOrEmpty(i);
                String dim = entry.getString("dim", "");
                int cx = entry.getInt("cx", 0);
                int cz = entry.getInt("cz", 0);
                int sy = entry.getInt("sy", 0);
                double score = entry.getDouble("score", 0.0);
                
                // Read headTypes
                List<EntityType<?>> headTypes = new ArrayList<>();
                if (entry.contains("heads")) {
                    NbtList headsList = entry.getListOrEmpty("heads");
                    for (int j = 0; j < headsList.size(); j++) {
                        NbtCompound headEntry = headsList.getCompoundOrEmpty(j);
                        String headId = headEntry.getString("id", "");
                        if (!headId.isEmpty()) {
                            EntityType<?> entityType = Registries.ENTITY_TYPE.get(Identifier.of(headId));
                            if (entityType != null) {
                                headTypes.add(entityType);
                            }
                        }
                    }
                }
                
                VoxelChunkKey key = new VoxelChunkKey(cx, cz, sy);
                CScore cScore = new CScore(score, headTypes);
                cache.getL1Cache().restoreEntry(dim, key, cScore);
                l1Count++;
            }
        }

        // Read L2 cache
        if (nbt.contains("l2")) {
            NbtList l2List = nbt.getListOrEmpty("l2");
            for (int i = 0; i < l2List.size(); i++) {
                NbtCompound entry = l2List.getCompoundOrEmpty(i);
                String dim = entry.getString("dim", "");
                int c2x = entry.getInt("c2x", 0);
                int c2z = entry.getInt("c2z", 0);
                int s2y = entry.getInt("s2y", 0);
                
                L2Key key = new L2Key(c2x, c2z, s2y);
                L2Entry l2Entry = new L2Entry(key);
                
                // Read cells
                long[] scoresLong = entry.getLongArray("scores").orElse(new long[0]);
                byte[] states = entry.getByteArray("states").orElse(new byte[0]);
                if (scoresLong.length == L2Key.CELL_COUNT && states.length == L2Key.CELL_COUNT) {
                    double[] scores = longArrayToDoubleArray(scoresLong);
                    l2Entry.restoreFromArrays(scores, states);
                }
                
                cache.getL2Cache().restoreEntry(dim, key, l2Entry);
                l2Count++;
            }
        }

        // Read L3 cache
        if (nbt.contains("l3")) {
            NbtList l3List = nbt.getListOrEmpty("l3");
            for (int i = 0; i < l3List.size(); i++) {
                NbtCompound entry = l3List.getCompoundOrEmpty(i);
                String dim = entry.getString("dim", "");
                int c3x = entry.getInt("c3x", 0);
                int c3z = entry.getInt("c3z", 0);
                int s3y = entry.getInt("s3y", 0);
                
                L3Key key = new L3Key(c3x, c3z, s3y);
                L3Entry l3Entry = new L3Entry(key);
                
                // Read cells
                long[] scoresLong = entry.getLongArray("scores").orElse(new long[0]);
                byte[] states = entry.getByteArray("states").orElse(new byte[0]);
                if (scoresLong.length == L3Key.CELL_COUNT && states.length == L3Key.CELL_COUNT) {
                    double[] scores = longArrayToDoubleArray(scoresLong);
                    l3Entry.restoreFromArrays(scores, states);
                }
                
                cache.getL3Cache().restoreEntry(dim, key, l3Entry);
                l3Count++;
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[civil] Loaded cache from save: L1={} L2={} L3={} (took {}ms)", 
                    l1Count, l2Count, l3Count, elapsed);
        }
    }

    /**
     * Save cache to NBT.
     */
    private NbtCompound saveToNbt() {
        long startTime = System.currentTimeMillis();
        NbtCompound nbt = new NbtCompound();

        // Save L1 cache
        NbtList l1List = new NbtList();
        cache.getL1Cache().forEachEntry((dim, key, cScore) -> {
            NbtCompound entry = new NbtCompound();
            entry.putString("dim", dim);
            entry.putInt("cx", key.getCx());
            entry.putInt("cz", key.getCz());
            entry.putInt("sy", key.getSy());
            entry.putDouble("score", cScore.score());
            
            // Save headTypes
            if (cScore.headTypes() != null && !cScore.headTypes().isEmpty()) {
                NbtList headsList = new NbtList();
                for (EntityType<?> type : cScore.headTypes()) {
                    Identifier id = Registries.ENTITY_TYPE.getId(type);
                    NbtCompound headEntry = new NbtCompound();
                    headEntry.putString("id", id.toString());
                    headsList.add(headEntry);
                }
                entry.put("heads", headsList);
            }
            
            l1List.add(entry);
        });
        nbt.put("l1", l1List);

        // Save L2 cache
        NbtList l2List = new NbtList();
        cache.getL2Cache().forEachEntry((dim, key, l2Entry) -> {
            NbtCompound entry = new NbtCompound();
            entry.putString("dim", dim);
            entry.putInt("c2x", key.getC2x());
            entry.putInt("c2z", key.getC2z());
            entry.putInt("s2y", key.getS2y());
            entry.putLongArray("scores", doubleArrayToLongArray(l2Entry.getScoresArray()));
            entry.putByteArray("states", l2Entry.getStatesArray());
            l2List.add(entry);
        });
        nbt.put("l2", l2List);

        // Save L3 cache
        NbtList l3List = new NbtList();
        cache.getL3Cache().forEachEntry((dim, key, l3Entry) -> {
            NbtCompound entry = new NbtCompound();
            entry.putString("dim", dim);
            entry.putInt("c3x", key.getC3x());
            entry.putInt("c3z", key.getC3z());
            entry.putInt("s3y", key.getS3y());
            entry.putLongArray("scores", doubleArrayToLongArray(l3Entry.getScoresArray()));
            entry.putByteArray("states", l3Entry.getStatesArray());
            l3List.add(entry);
        });
        nbt.put("l3", l3List);

        long elapsed = System.currentTimeMillis() - startTime;
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[civil] Saved cache to save: L1={} L2={} L3={} (took {}ms)", 
                    l1List.size(), l2List.size(), l3List.size(), elapsed);
        }

        return nbt;
    }

    // ========== Utility Methods ==========

    private static long[] doubleArrayToLongArray(double[] doubles) {
        long[] longs = new long[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            longs[i] = Double.doubleToRawLongBits(doubles[i]);
        }
        return longs;
    }

    private static double[] longArrayToDoubleArray(long[] longs) {
        double[] doubles = new double[longs.length];
        for (int i = 0; i < longs.length; i++) {
            doubles[i] = Double.longBitsToDouble(longs[i]);
        }
        return doubles;
    }
}
