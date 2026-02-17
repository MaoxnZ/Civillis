package civil.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import civil.registry.HeadTypeRegistry.HeadTypeEntry;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Datapack reload listener that populates {@link HeadTypeRegistry} from
 * JSON files located at {@code data/<namespace>/civil_heads/*.json}.
 *
 * <p>Loaded at server startup and on {@code /reload}. Files within each
 * namespace are processed in alphabetical order; later entries for the
 * same skull_type override earlier ones.
 */
public final class HeadTypeLoader implements SimpleSynchronousResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-registry");
    private static final String DATA_PATH = "civil_heads";

    @Override
    public Identifier getFabricId() {
        return Identifier.of("civil", "head_types");
    }

    @Override
    public void reload(ResourceManager manager) {
        Map<String, HeadTypeEntry> accumulated = new LinkedHashMap<>();

        Map<Identifier, Resource> resources = manager.findResources(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier fileId = entry.getKey();
            try (InputStream is = entry.getValue().getInputStream();
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
                        Identifier id = Identifier.of(entityId);
                        if (Registries.ENTITY_TYPE.containsId(id)) {
                            entityType = Registries.ENTITY_TYPE.get(id);
                        } else {
                            LOGGER.warn("[civil-registry] Unknown entity type '{}' for skull '{}' in {}, skipping entity mapping",
                                    entityId, skullType, fileId);
                        }
                    }

                    boolean enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();
                    boolean convertEnabled = !obj.has("convert") || obj.get("convert").getAsBoolean();

                    accumulated.put(skullType, new HeadTypeEntry(skullType, entityType, enabled, convertEnabled));
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
