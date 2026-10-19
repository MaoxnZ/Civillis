package civil.compat.bandsync;

import civil.CivilMod;
import civil.map.CivilMapTintLogic;
import civil.map.CivilMapTintPalette;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Client-side merge of {@link ChunkBandTintPayload} data; shared by all world-map adapters.
 * <p>
 * L1 is sparse: only {@link CivilMapTintPalette#isChunkBandOverlayStored(byte)} chunks are kept. Any other band removes
 * the packed key (same as “unknown” for {@link #getBand}).
 */
public final class ChunkBandClientCache {

    /** Fastutil map default for “no entry yet”. */
    public static final byte MISSING_BAND = -1;

    private static final Object2ObjectOpenHashMap<String, Long2ByteOpenHashMap> BY_DIMENSION = new Object2ObjectOpenHashMap<>();

    private static final CopyOnWriteArrayList<Runnable> CHANGE_LISTENERS = new CopyOnWriteArrayList<>();

    private static final CopyOnWriteArrayList<Consumer<ChunkBandTintPayload>> TINT_PAYLOAD_LISTENERS =
            new CopyOnWriteArrayList<>();

    private static final CopyOnWriteArrayList<Runnable> FULL_MAP_LISTENERS = new CopyOnWriteArrayList<>();

    /** Monotonic stamp bumped after cache mutations; overlays (e.g. Xaero region hash) can key off this cheaply. */
    private static final AtomicInteger OVERLAY_STAMP = new AtomicInteger();

    /** Current {@link #OVERLAY_STAMP} value (read on render thread). */
    public static int overlayStamp() {
        return OVERLAY_STAMP.get();
    }

    /**
     * Reads the merged band for a chunk without copying the dimension map. Only overlay-stored bands are present;
     * otherwise {@link #MISSING_BAND}.
     */
    public static byte getBand(String dimensionId, int chunkX, int chunkZ) {
        Long2ByteOpenHashMap src = BY_DIMENSION.get(dimensionId);
        if (src == null) {
            return MISSING_BAND;
        }
        return src.get(CivilMapTintLogic.packChunk(chunkX, chunkZ));
    }

    /**
     * Returns the raw inner map for a dimension (no copy). {@code null} if no data exists.
     * Callers must treat the map as <b>read-only</b>; mutations violate cache invariants.
     */
    public static Long2ByteOpenHashMap getRawDimensionMap(String dimensionId) {
        return BY_DIMENSION.get(dimensionId);
    }

    private static void bumpOverlayStamp() {
        OVERLAY_STAMP.incrementAndGet();
    }

    private ChunkBandClientCache() {
    }

    /**
     * Registers a listener invoked on the client thread after {@link #apply} / {@link #clearAll} /
     * {@link #notifyOverlayListeners()}. Multiple adapters (e.g. several world-map mods) may register;
     * the same instance is only added once.
     */
    public static void addChangeListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (!CHANGE_LISTENERS.contains(listener)) {
            CHANGE_LISTENERS.add(listener);
        }
    }

    /** Removes a listener registered with {@link #addChangeListener(Runnable)}. */
    public static void removeChangeListener(Runnable listener) {
        if (listener != null) {
            CHANGE_LISTENERS.remove(listener);
        }
    }

    /**
     * Invoked on the client thread after each {@link #apply(ChunkBandTintPayload)} with that payload. Same instance only
     * added once.
     */
    public static void addTintPayloadListener(Consumer<ChunkBandTintPayload> listener) {
        Objects.requireNonNull(listener, "listener");
        if (!TINT_PAYLOAD_LISTENERS.contains(listener)) {
            TINT_PAYLOAD_LISTENERS.add(listener);
        }
    }

    public static void removeTintPayloadListener(Consumer<ChunkBandTintPayload> listener) {
        if (listener != null) {
            TINT_PAYLOAD_LISTENERS.remove(listener);
        }
    }

    /**
     * Invoked on the client thread after {@link #clearAll()}, {@link #mergePersistedDimension}, or
     * {@link #notifyOverlayListeners()} — rebuild from {@link #copyDimensionMap(String)}. Same instance only added once.
     */
    public static void addFullMapListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        if (!FULL_MAP_LISTENERS.contains(listener)) {
            FULL_MAP_LISTENERS.add(listener);
        }
    }

    public static void removeFullMapListener(Runnable listener) {
        if (listener != null) {
            FULL_MAP_LISTENERS.remove(listener);
        }
    }

    public static void clearAll() {
        BY_DIMENSION.clear();
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[bandsync][client] ChunkBandClientCache cleared (all dimensions)");
        }
        notifyFullMapListeners();
        notifyListener();
    }

    /**
     * Notifies overlay listeners without mutating cache (e.g. JM ref forced off while another consumer
     * still holds chunk-band subscription so {@link #clearAll()} does not run).
     */
    public static void notifyOverlayListeners() {
        notifyFullMapListeners();
        notifyListener();
    }

    public static void apply(ChunkBandTintPayload payload) {
        Long2ByteOpenHashMap map = BY_DIMENSION.computeIfAbsent(payload.dimensionId(), d -> {
            Long2ByteOpenHashMap m = new Long2ByteOpenHashMap();
            m.defaultReturnValue(MISSING_BAND);
            return m;
        });
        for (ChunkBandTintPayload.Entry e : payload.entries()) {
            long pk = CivilMapTintLogic.packChunk(e.cx(), e.cz());
            if (CivilMapTintPalette.isChunkBandOverlayStored(e.band())) {
                map.put(pk, e.band());
            } else {
                map.remove(pk);
            }
        }
        removeDimensionIfEmpty(payload.dimensionId(), map);
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info(
                    "[bandsync][client] S2C tint applied dim={} entries={}", payload.dimensionId(), payload.entries().size());
        }
        notifyTintPayloadListeners(payload);
        notifyListener();
    }

    private static void removeDimensionIfEmpty(String dimensionId, Long2ByteOpenHashMap map) {
        if (map != null && map.isEmpty()) {
            BY_DIMENSION.remove(dimensionId);
        }
    }

    private static void notifyListener() {
        bumpOverlayStamp();
        for (Runnable r : CHANGE_LISTENERS) {
            r.run();
        }
    }

    private static void notifyTintPayloadListeners(ChunkBandTintPayload payload) {
        for (Consumer<ChunkBandTintPayload> c : TINT_PAYLOAD_LISTENERS) {
            c.accept(payload);
        }
    }

    private static void notifyFullMapListeners() {
        for (Runnable r : FULL_MAP_LISTENERS) {
            r.run();
        }
    }

    /** Bands keyed by {@link CivilMapTintLogic#packChunk}; default {@link #MISSING_BAND}. */
    public static Long2ByteOpenHashMap copyDimensionMap(String dimensionId) {
        Long2ByteOpenHashMap src = BY_DIMENSION.get(dimensionId);
        if (src == null) {
            Long2ByteOpenHashMap empty = new Long2ByteOpenHashMap();
            empty.defaultReturnValue(MISSING_BAND);
            return empty;
        }
        Long2ByteOpenHashMap copy = new Long2ByteOpenHashMap(src);
        copy.defaultReturnValue(MISSING_BAND);
        return copy;
    }


    /**
     * Deep copies of all dimension maps for persistence (E3). Caller must not mutate returned inner maps if reused
     * after {@link #clearAll()}.
     */
    public static Map<String, Long2ByteOpenHashMap> copyAllDimensionsForPersistence() {
        if (BY_DIMENSION.isEmpty()) {
            return Map.of();
        }
        Map<String, Long2ByteOpenHashMap> out = new LinkedHashMap<>();
        for (var e : BY_DIMENSION.object2ObjectEntrySet()) {
            Long2ByteOpenHashMap copy = new Long2ByteOpenHashMap(e.getValue());
            copy.defaultReturnValue(MISSING_BAND);
            out.put(e.getKey(), copy);
        }
        return out;
    }

    /** Merges a persisted snapshot into L1 for one dimension (E1); later S2C {@link #apply} overwrites overlaps. */
    public static void mergePersistedDimension(String dimensionId, Long2ByteOpenHashMap data) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(data, "data");
        if (data.isEmpty()) {
            return;
        }
        Long2ByteOpenHashMap map = BY_DIMENSION.computeIfAbsent(dimensionId, d -> {
            Long2ByteOpenHashMap m = new Long2ByteOpenHashMap();
            m.defaultReturnValue(MISSING_BAND);
            return m;
        });
        for (Long2ByteMap.Entry en : data.long2ByteEntrySet()) {
            byte b = en.getByteValue();
            if (CivilMapTintPalette.isChunkBandOverlayStored(b)) {
                map.put(en.getLongKey(), b);
            } else {
                map.remove(en.getLongKey());
            }
        }
        removeDimensionIfEmpty(dimensionId, map);
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[bandsync][client] merged persisted snapshot dim={} entries={}", dimensionId, data.size());
        }
        notifyFullMapListeners();
        notifyListener();
    }
}
