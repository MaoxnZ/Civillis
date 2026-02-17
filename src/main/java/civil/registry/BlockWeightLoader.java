package civil.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Datapack reload listener that populates {@link BlockWeightRegistry} from
 * JSON files located at {@code data/<namespace>/civil_blocks/*.json}.
 *
 * <p>Loaded at server startup and on {@code /reload}. Files within each
 * namespace are processed in alphabetical order; later entries for the
 * same block ID override earlier ones (useful for modpack customization).
 *
 * <p>Supports two block specifiers:
 * <ul>
 *   <li>Block ID: {@code "minecraft:beacon"}</li>
 *   <li>Block tag (prefixed with {@code #}): {@code "#minecraft:beds"}</li>
 * </ul>
 */
public final class BlockWeightLoader implements SimpleSynchronousResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-registry");
    private static final String DATA_PATH = "civil_blocks";

    @Override
    public Identifier getFabricId() {
        return Identifier.of("civil", "block_weights");
    }

    @Override
    public void reload(ResourceManager manager) {
        IdentityHashMap<Block, Double> accumulated = new IdentityHashMap<>();

        Map<Identifier, Resource> resources = manager.findResources(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier fileId = entry.getKey();
            try (InputStream is = entry.getValue().getInputStream();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
                if (replace) {
                    LOGGER.warn("[civil-registry] replace=true in {} — clearing {} previously loaded block weight(s)",
                            fileId, accumulated.size());
                    accumulated.clear();
                }

                if (!root.has("entries")) continue;
                JsonArray entries = root.getAsJsonArray("entries");

                for (JsonElement elem : entries) {
                    JsonObject obj = elem.getAsJsonObject();
                    String blockSpec = obj.get("block").getAsString();
                    double weight = obj.get("weight").getAsDouble();

                    if (blockSpec.startsWith("#")) {
                        // Block tag
                        String tagPath = blockSpec.substring(1);
                        Identifier tagId = Identifier.of(tagPath);
                        TagKey<Block> tagKey = TagKey.of(RegistryKeys.BLOCK, tagId);
                        Registries.BLOCK.iterateEntries(tagKey).forEach(blockEntry ->
                                accumulated.put(blockEntry.value(), weight));
                    } else {
                        // Single block ID
                        Identifier blockId = Identifier.of(blockSpec);
                        if (Registries.BLOCK.containsId(blockId)) {
                            Block block = Registries.BLOCK.get(blockId);
                            accumulated.put(block, weight);
                        } else {
                            LOGGER.warn("[civil-registry] Unknown block '{}' in {}, skipping",
                                    blockSpec, fileId);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[civil-registry] Failed to load block weights from {}: {}",
                        fileId, e.getMessage());
            }
        }

        BlockWeightRegistry.reload(accumulated);
        LOGGER.info("[civil-registry] Loaded {} block weight(s) from datapack", accumulated.size());
    }
}
