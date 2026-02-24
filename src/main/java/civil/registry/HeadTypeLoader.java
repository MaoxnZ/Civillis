package civil.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import civil.registry.HeadTypeRegistry.HeadTypeEntry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Datapack reload listener that populates {@link HeadTypeRegistry} from
 * JSON files located at {@code data/<namespace>/civil_heads/*.json}.
 *
 * <p>Platform entry points wrap this in the appropriate reload listener
 * (Fabric: SimpleSynchronousResourceReloadListener, NeoForge: AddReloadListenerEvent).
 */
public final class HeadTypeLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-registry");
    private static final String DATA_PATH = "civil_heads";

    /** Perform the actual reload. Called by platform-specific reload listener wrappers. */
    public static void reload(ResourceManager manager) {
        Map<String, HeadTypeEntry> accumulated = new LinkedHashMap<>();

        Map<Identifier, Resource> resources = manager.listResources(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier fileId = entry.getKey();
            try (InputStream is = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
                if (replace) {
                    LOGGER.warn("[civil-registry] replace=true in {} — clearing {} previously loaded head type(s)",
                            fileId, accumulated.size());
                    accumulated.clear();
                }

                if (!root.has("entries")) continue;
                JsonArray entries = root.getAsJsonArray("entries");

                for (JsonElement elem : entries) {
                    JsonObject obj = elem.getAsJsonObject();
                    String skullType = obj.get("skull_type").getAsString();

                    EntityType<?> entityType = null;
                    if (obj.has("entity_type") && !obj.get("entity_type").isJsonNull()) {
                        String entityId = obj.get("entity_type").getAsString();
                        Identifier id = Identifier.parse(entityId);
                        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                            entityType = BuiltInRegistries.ENTITY_TYPE.getValue(id);
                        } else {
                            LOGGER.warn("[civil-registry] Unknown entity type '{}' for skull '{}' in {}, skipping entity mapping",
                                    entityId, skullType, fileId);
                        }
                    }

                    boolean enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();
                    boolean convertEnabled = !obj.has("convert") || obj.get("convert").getAsBoolean();

                    Set<String> dimensions = null;
                    if (obj.has("dimensions") && obj.get("dimensions").isJsonArray()) {
                        JsonArray dimArr = obj.getAsJsonArray("dimensions");
                        Set<String> dimSet = new LinkedHashSet<>();
                        for (JsonElement de : dimArr) {
                            dimSet.add(de.getAsString());
                        }
                        dimensions = Set.copyOf(dimSet);
                    }

                    accumulated.put(skullType, new HeadTypeEntry(skullType, entityType, enabled, convertEnabled, dimensions));
                }
            } catch (Exception e) {
                LOGGER.error("[civil-registry] Failed to load head types from {}: {}",
                        fileId, e.getMessage());
            }
        }

        HeadTypeRegistry.reload(accumulated);
        LOGGER.info("[civil-registry] Loaded {} head type(s) from datapack", accumulated.size());
    }
}
