package civil.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Datapack reload: loads {@code data/&lt;namespace&gt;/civil_dimension_policies/*.json} into
 * {@link DimensionPolicyRegistry}.
 *
 * <p>Entry fields:
 * <ul>
 *   <li>{@code dimension} — dimension id (e.g. {@code minecells:prison}, {@code dimdungeons:dungeon_dimension})</li>
 *   <li>{@code civilization} — optional, default {@code true}. When {@code false}, head-based spawn stages
 *       (HEAD_NEARBY / HEAD_SUPPRESS) and zone + score spawn stages are skipped ({@code SpawnPolicy}
 *       returns {@code DIM_NEUTRAL} without querying heads).</li>
 *   <li>{@code head_mechanics} — optional, default {@code true}. When {@code false}, head-based spawn
 *       stages are skipped when civilization is enabled for the dimension.</li>
 * </ul>
 *
 * <p>Files are processed in lexicographic order by resource id; later entries for the same
 * {@code dimension} override earlier ones.
 */
public final class DimensionPolicyLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-registry");
    private static final String DATA_PATH = "civil_dimension_policies";

    private DimensionPolicyLoader() {
    }

    /**
     * @param registryAccess used to drop {@code dimension} entries not in {@link Registries#DIMENSION}.
     */
    public static void reload(ResourceManager manager, RegistryAccess registryAccess) {
        Map<Identifier, Resource> resources = manager.listResources(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        List<Map.Entry<Identifier, Resource>> sorted = new ArrayList<>(resources.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getKey().toString()));

        HashMap<String, DimensionPolicyRegistry.DimensionPolicy> accumulated = new HashMap<>();

        for (Map.Entry<Identifier, Resource> entry : sorted) {
            Identifier fileId = entry.getKey();
            try (InputStream is = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
                if (replace) {
                    LOGGER.warn("[civil-registry] replace=true in {} — clearing {} dimension polic(ies)",
                            fileId, accumulated.size());
                    accumulated.clear();
                }

                if (!root.has("entries")) continue;
                JsonArray entries = root.getAsJsonArray("entries");

                var dimensionLookup = registryAccess.lookupOrThrow(Registries.DIMENSION);
                for (JsonElement elem : entries) {
                    JsonObject obj = elem.getAsJsonObject();
                    String dimSpec = obj.get("dimension").getAsString();
                    Identifier dimId = Identifier.parse(dimSpec);
                    ResourceKey<Level> dKey = ResourceKey.create(Registries.DIMENSION, dimId);
                    if (dimensionLookup.get(dKey).isEmpty()) {
                        LOGGER.warn("[civil-registry] Unknown dimension '{}' in {}, skipping", dimSpec, fileId);
                        continue;
                    }
                    String dimKey = dimId.toString();

                    boolean civilization = !obj.has("civilization") || obj.get("civilization").getAsBoolean();
                    boolean headMechanics = !obj.has("head_mechanics") || obj.get("head_mechanics").getAsBoolean();

                    accumulated.put(dimKey, new DimensionPolicyRegistry.DimensionPolicy(civilization, headMechanics));
                }
            } catch (Exception e) {
                LOGGER.error("[civil-registry] Failed to load dimension policies from {}: {}",
                        fileId, e.getMessage());
            }
        }

        DimensionPolicyRegistry.reload(accumulated);
        LOGGER.info("[civil-registry] Loaded {} dimension polic(ies) from datapack", accumulated.size());
    }
}
