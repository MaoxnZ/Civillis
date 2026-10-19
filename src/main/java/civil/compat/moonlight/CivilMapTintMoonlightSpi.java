package civil.compat.moonlight;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Moonlight map-stack bridge: publishes per-pixel tint decisions when listeners register, and toggles
 * server-side bake when {@link MoonlightMapCompatBootstrap} runs — without referencing Moonlight types
 * outside {@code civil.compat.moonlight}.
 */
public final class CivilMapTintMoonlightSpi {

    public static final int SCHEMA_VERSION = 1;

    public record Payload(
            MapItemSavedData mapData,
            MapId mapId,
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
