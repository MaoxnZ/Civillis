package civil.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Datapack reload: loads {@code data/&lt;namespace&gt;/civil_spawn_gate_entities/*.json} into
 * {@link SpawnGateEntityRegistry}.
 *
 * <p>Each entry may contain {@code blacklist} and/or {@code whitelist} string arrays (entity ids).
 * Files are processed in lexicographic order by resource id; {@code replace: true} clears accumulated
 * sets from earlier files. Unknown entity ids are skipped with a warning.
 *
 * <p>If the same {@link EntityType} appears in both lists after merge, {@code whitelist} wins at
 * decision time; a warning is logged on reload.
 */
public final class SpawnGateEntityLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-registry");
    private static final String DATA_PATH = "civil_spawn_gate_entities";

    private SpawnGateEntityLoader() {
    }

    public static void reload(ResourceManager manager) {
        Map<Identifier, Resource> resources = manager.listResources(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        List<Map.Entry<Identifier, Resource>> sorted = new ArrayList<>(resources.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getKey().toString()));

        HashSet<EntityType<?>> blacklist = new HashSet<>();
        HashSet<EntityType<?>> whitelist = new HashSet<>();

        for (Map.Entry<Identifier, Resource> entry : sorted) {
            Identifier fileId = entry.getKey();
            try (InputStream is = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
                if (replace) {
                    LOGGER.warn("[civil-registry] replace=true in {} — clearing spawn gate blacklist={} whitelist={}",
                            fileId, blacklist.size(), whitelist.size());
                    blacklist.clear();
                    whitelist.clear();
                }

                if (!root.has("entries")) {
                    continue;
                }
                JsonArray entries = root.getAsJsonArray("entries");
                for (JsonElement elem : entries) {
                    JsonObject obj = elem.getAsJsonObject();
                    addEntityIds(obj, "blacklist", blacklist, fileId);
                    addEntityIds(obj, "whitelist", whitelist, fileId);
                }
            } catch (Exception e) {
                LOGGER.error("[civil-registry] Failed to load spawn gate entities from {}: {}",
                        fileId, e.getMessage());
            }
        }

        for (EntityType<?> t : whitelist) {
            if (blacklist.contains(t)) {
                LOGGER.warn("[civil-registry] Entity {} appears in both blacklist and whitelist; whitelist wins at decide",
                        BuiltInRegistries.ENTITY_TYPE.getKey(t));
            }
        }

        SpawnGateEntityRegistry.reload(blacklist, whitelist);
        LOGGER.info("[civil-registry] Loaded spawn gate entities: blacklist={} whitelist={}",
                blacklist.size(), whitelist.size());
    }

    private static void addEntityIds(JsonObject obj, String key, HashSet<EntityType<?>> out, Identifier fileId) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            return;
        }
        JsonArray arr = obj.getAsJsonArray(key);
        for (JsonElement el : arr) {
            String idStr = el.getAsString();
            try {
                Identifier parsed = Identifier.parse(idStr);
                if (BuiltInRegistries.ENTITY_TYPE.containsKey(parsed)) {
                    out.add(BuiltInRegistries.ENTITY_TYPE.getValue(parsed));
                } else {
                    LOGGER.warn("[civil-registry] Unknown entity type '{}' in {} ({}), skipping",
                            idStr, fileId, key);
                }
            } catch (Exception ex) {
                LOGGER.warn("[civil-registry] Invalid entity id '{}' in {} ({}), skipping",
                        idStr, fileId, key);
            }
        }
    }
}
