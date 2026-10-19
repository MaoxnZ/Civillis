package civil.civilization;

import civil.CivilServices;
import civil.civilization.cache.ResultCache;
import civil.civilization.cache.ResultEntry;
import civil.civilization.storage.CivilStorage;
import civil.config.CivilConfig;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of baseScore sources (town center + API). Persisted via {@link CivilStorage}.
 */
public final class BaseScoreSourceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-basescore");

    public static final String TYPE_TC = "tc";
    public static final String TYPE_API = "api";

    public record SourceEntry(
            String sourceKey,
            String dim,
            VoxelChunkKey minVc,
            VoxelChunkKey maxVc,
            double rawValue,
            String type) {}

    private final ConcurrentHashMap<String, SourceEntry> sources = new ConcurrentHashMap<>();

    private volatile CivilStorage storage;
    private volatile boolean initialized;
    private volatile boolean sourcesDirty;

    public static String tcSourceKey(int x, int y, int z) {
        return "tc:" + x + "," + y + "," + z;
    }

    public void initialize(CivilStorage civilStorage) {
        this.storage = civilStorage;
        this.sourcesDirty = false;
        sources.clear();

        List<CivilStorage.StoredBaseScoreSource> stored = civilStorage.loadBaseScoreSources();
        for (CivilStorage.StoredBaseScoreSource s : stored) {
            VoxelChunkKey min = new VoxelChunkKey(s.minVcX(), s.minVcZ(), s.minVcY());
            VoxelChunkKey max = new VoxelChunkKey(s.maxVcX(), s.maxVcZ(), s.maxVcY());
            sources.put(s.sourceId(), new SourceEntry(
                    s.sourceId(), s.dim(), min, max, s.rawValue(), s.type()));
        }

        initialized = true;
        LOGGER.info("[civil-basescore] Loaded {} base score source(s) from storage", stored.size());
    }

    public void shutdown() {
        initialized = false;
        sources.clear();
        storage = null;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isSourcesDirty() {
        return sourcesDirty;
    }

    public void clearSourcesDirty() {
        sourcesDirty = false;
    }

    public List<CivilStorage.StoredBaseScoreSource> snapshotAllSources() {
        List<CivilStorage.StoredBaseScoreSource> out = new ArrayList<>(sources.size());
        for (SourceEntry e : sources.values()) {
            out.add(new CivilStorage.StoredBaseScoreSource(
                    e.sourceKey(),
                    e.dim(),
                    e.minVc().getCx(), e.minVc().getSy(), e.minVc().getCz(),
                    e.maxVc().getCx(), e.maxVc().getSy(), e.maxVc().getCz(),
                    e.rawValue(),
                    e.type()));
        }
        return out;
    }

    public SourceEntry get(String sourceKey) {
        return sources.get(sourceKey);
    }

    public void add(SourceEntry entry) {
        if (!initialized) return;
        sources.put(entry.sourceKey(), entry);
        sourcesDirty = true;
        refreshCache(entry.dim(), entry.minVc(), entry.maxVc());
    }

    public void remove(String sourceKey) {
        if (!initialized) return;
        SourceEntry removed = sources.remove(sourceKey);
        if (removed != null) {
            sourcesDirty = true;
            refreshCache(removed.dim(), removed.minVc(), removed.maxVc());
        }
    }

    public void update(String sourceKey, String dim, VoxelChunkKey newMin, VoxelChunkKey newMax, Double newValue) {
        if (!initialized) return;
        SourceEntry old = sources.get(sourceKey);
        if (old == null) return;

        VoxelChunkKey min = newMin != null ? newMin : old.minVc();
        VoxelChunkKey max = newMax != null ? newMax : old.maxVc();
        double raw = newValue != null ? newValue : old.rawValue();
        String d = dim != null ? dim : old.dim();

        SourceEntry updated = new SourceEntry(sourceKey, d, min, max, raw, old.type());
        sources.put(sourceKey, updated);
        sourcesDirty = true;

        VoxelChunkKey unionMin = new VoxelChunkKey(
                Math.min(old.minVc().getCx(), min.getCx()),
                Math.min(old.minVc().getCz(), min.getCz()),
                Math.min(old.minVc().getSy(), min.getSy()));
        VoxelChunkKey unionMax = new VoxelChunkKey(
                Math.max(old.maxVc().getCx(), max.getCx()),
                Math.max(old.maxVc().getCz(), max.getCz()),
                Math.max(old.maxVc().getSy(), max.getSy()));
        refreshCache(d, unionMin, unionMax);
    }

    public double queryBaseScore(String dim, VoxelChunkKey vc) {
        double sum = 0.0;
        for (SourceEntry e : sources.values()) {
            if (!dim.equals(e.dim())) continue;
            if (contains(e, vc)) {
                sum += e.rawValue();
            }
        }
        return clamp(sum);
    }

    private static boolean contains(SourceEntry e, VoxelChunkKey vc) {
        return vc.getCx() >= e.minVc().getCx() && vc.getCx() <= e.maxVc().getCx()
                && vc.getCz() >= e.minVc().getCz() && vc.getCz() <= e.maxVc().getCz()
                && vc.getSy() >= e.minVc().getSy() && vc.getSy() <= e.maxVc().getSy();
    }

    private static double clamp(double sum) {
        return Math.max(CivilConfig.baseScoreGlobalFloor,
                Math.min(CivilConfig.baseScoreGlobalCap, sum));
    }

    private void refreshCache(String dim, VoxelChunkKey minVc, VoxelChunkKey maxVc) {
        ResultCache cache = CivilServices.getResultCache();
        if (cache == null) return;

        int minCx = minVc.getCx();
        int maxCx = maxVc.getCx();
        int minCz = minVc.getCz();
        int maxCz = maxVc.getCz();
        int minSy = minVc.getSy();
        int maxSy = maxVc.getSy();

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                for (int sy = minSy; sy <= maxSy; sy++) {
                    VoxelChunkKey vc = new VoxelChunkKey(cx, cz, sy);
                    ResultEntry entry = cache.getIfPresent(dim, vc);
                    if (entry != null) {
                        entry.setBaseScore(queryBaseScore(dim, vc));
                    }
                }
            }
        }
    }

    /** World block positions → VC AABB for API callers. */
    public static VoxelChunkKey blockPosToVc(BlockPos pos) {
        return VoxelChunkKey.from(pos);
    }
}
