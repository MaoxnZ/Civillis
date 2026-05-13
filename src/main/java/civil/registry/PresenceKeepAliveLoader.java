package civil.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import civil.CivilMod;
import net.minecraft.world.entity.EntityType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datapack reload: loads {@code data/&lt;namespace&gt;/civil_presence_keepalive/*.json} into
 * {@link PresenceKeepAliveRegistry}.
 *
 * <p>Files are processed in lexicographic order by resource id; {@code replace: true} clears accumulated
 * entries from earlier files. Unknown entity ids are skipped with a warning.
 *
 * <p>Each {@code entries[]} item: {@code entity} (required id string), optional {@code radius_voxel_chunks}
 * object with {@code x},{@code z},{@code y} (half-extent in voxel chunks). If the whole object is omitted,
 * or a field inside it is omitted, defaults {@code 3}/{@code 3}/{@code 1} apply (same as a fully-specified
 * {@code radius_voxel_chunks} with those values).
 */
public final class PresenceKeepAliveLoader {

    private static final String DATA_PATH = "civil_presence_keepalive";

    /** Half-extent in voxel chunks when datapack omits {@code radius_voxel_chunks} or a coordinate inside it. */
    private static final int DEFAULT_RADIUS_VC_X = 3;
    private static final int DEFAULT_RADIUS_VC_Z = 3;
    private static final int DEFAULT_RADIUS_VC_Y = 1;

    private PresenceKeepAliveLoader() {
    }

    public static void reload(ResourceManager manager) {
        Map<Identifier, Resource> resources = manager.listResources(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        List<Map.Entry<Identifier, Resource>> sorted = new ArrayList<>(resources.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getKey().toString()));

        LinkedHashMap<EntityType<?>, PresenceKeepAliveRegistry.RadiusVc> merged = new LinkedHashMap<>();

        for (Map.Entry<Identifier, Resource> entry : sorted) {
            Identifier fileId = entry.getKey();
            try (InputStream is = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
                if (replace) {
                    CivilMod.LOGGER.warn("[civil-registry] replace=true in {} — clearing presence keepalive entries={}",
                            fileId, merged.size());
                    merged.clear();
                }

                if (!root.has("entries")) {
                    continue;
                }
                JsonArray entries = root.getAsJsonArray("entries");
                for (JsonElement elem : entries) {
                    JsonObject obj = elem.getAsJsonObject();
                    if (!obj.has("entity")) {
                        CivilMod.LOGGER.warn("[civil-registry] Missing \"entity\" in presence keepalive entry ({})", fileId);
                        continue;
                    }
                    String idStr = obj.get("entity").getAsString();
                    Identifier parsed;
                    try {
                        parsed = Identifier.parse(idStr);
                    } catch (Exception ex) {
                        CivilMod.LOGGER.warn("[civil-registry] Invalid entity id '{}' in presence keepalive ({})", idStr, fileId);
                        continue;
                    }
                    if (!BuiltInRegistries.ENTITY_TYPE.containsKey(parsed)) {
                        CivilMod.LOGGER.warn("[civil-registry] Unknown entity type '{}' in presence keepalive ({}), skipping",
                                idStr, fileId);
                        continue;
                    }
                    EntityType<?> et = BuiltInRegistries.ENTITY_TYPE.getValue(parsed);
                    PresenceKeepAliveRegistry.RadiusVc rv = parseRadius(obj, fileId);
                    if (merged.containsKey(et)) {
                        CivilMod.LOGGER.warn("[civil-registry] Duplicate presence keepalive entity {} in {}; later entry wins",
                                idStr, fileId);
                    }
                    merged.put(et, rv);
                }
            } catch (Exception e) {
                CivilMod.LOGGER.error("[civil-registry] Failed to load presence keepalive from {}: {}",
                        fileId, e.getMessage());
            }
        }

        PresenceKeepAliveRegistry.reload(merged);
        CivilMod.LOGGER.info("[civil-registry] Loaded presence keepalive entity types: {}", merged.size());
    }

    private static PresenceKeepAliveRegistry.RadiusVc parseRadius(JsonObject obj, Identifier fileId) {
        if (!obj.has("radius_voxel_chunks") || !obj.get("radius_voxel_chunks").isJsonObject()) {
            return new PresenceKeepAliveRegistry.RadiusVc(
                    DEFAULT_RADIUS_VC_X,
                    DEFAULT_RADIUS_VC_Z,
                    DEFAULT_RADIUS_VC_Y);
        }
        JsonObject r = obj.getAsJsonObject("radius_voxel_chunks");
        int rx = r.has("x") ? r.get("x").getAsInt() : DEFAULT_RADIUS_VC_X;
        int rz = r.has("z") ? r.get("z").getAsInt() : DEFAULT_RADIUS_VC_Z;
        int ry = r.has("y") ? r.get("y").getAsInt() : DEFAULT_RADIUS_VC_Y;
        rx = Math.max(0, rx);
        rz = Math.max(0, rz);
        ry = Math.max(0, ry);
        if (rx > 32 || rz > 32 || ry > 32) {
            CivilMod.LOGGER.warn("[civil-registry] Large radius_voxel_chunks ({},{},{}) in {}, clamping to 32",
                    rx, rz, ry, fileId);
            rx = Math.min(rx, 32);
            rz = Math.min(rz, 32);
            ry = Math.min(ry, 32);
        }
        return new PresenceKeepAliveRegistry.RadiusVc(rx, rz, ry);
    }
}
