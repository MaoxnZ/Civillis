package civil.compat.moonlight;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Moonlight map-stack bridge (with Supplementaries-style clients): lets the main map tint pipeline
 * publish per-pixel decisions and flip server-side bake off once {@link MoonlightMapCompatBootstrap}
 * runs — without this class referencing Moonlight types, so {@link civil.map} can import it safely
 * while Moonlight remains optional at runtime.
 */
public final class CivilMapTintMoonlightSpi {

    /** Increment when {@code band} / {@code edge} semantics or NBT layout change. */
    public static final int SCHEMA_VERSION = 1;

    /**
     * Per-pixel tint decision streamed to the Moonlight {@code CustomMapData} path after listeners
     * register (see bootstrap).
     */
    public record Payload(
            MapItemSavedData mapData,
            int mapId,
            int mapX,
            int mapY,
            byte band,
            boolean edge,
            int schemaVersion) {}

    private static final List<Consumer<Payload>> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile boolean moonlightParallelTintActive;

    private CivilMapTintMoonlightSpi() {}

    public static void addListener(Consumer<Payload> listener) {
        LISTENERS.add(listener);
    }

    /** Callers must check before building payloads to avoid per-pixel allocations. */
    public static boolean hasListeners() {
        return !LISTENERS.isEmpty();
    }

    public static void publish(Payload payload) {
        if (LISTENERS.isEmpty()) {
            return;
        }
        for (Consumer<Payload> listener : LISTENERS) {
            listener.accept(payload);
        }
    }

    public static void setMoonlightParallelTintActive(boolean active) {
        moonlightParallelTintActive = active;
    }

    public static boolean isMoonlightParallelTintActive() {
        return moonlightParallelTintActive;
    }
}
