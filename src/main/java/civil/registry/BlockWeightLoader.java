package civil.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;
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
 * <p>Platform entry points wrap this in the appropriate reload listener
 * (Fabric: SimpleSynchronousResourceReloadListener, NeoForge: AddReloadListenerEvent).
 */
public final class BlockWeightLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-registry");
    private static final String DATA_PATH = "civil_blocks";

    /** Perform the actual reload. Called by platform-specific reload listener wrappers. */
    public static void reload(ResourceManager manager) {
        IdentityHashMap<Block, Double> accumulated = new IdentityHashMap<>();

        Map<Identifier, Resource> resources = manager.listResources(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier fileId = entry.getKey();
            try (InputStream is = entry.getValue().open();
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
                        String tagPath = blockSpec.substring(1);
                        Identifier tagId = Identifier.parse(tagPath);
                        TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagId);
                        BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey).forEach(blockEntry ->
                                accumulated.put(blockEntry.value(), weight));
                    } else {
                        Identifier blockId = Identifier.parse(blockSpec);
                        if (BuiltInRegistries.BLOCK.containsKey(blockId)) {
                            Block block = BuiltInRegistries.BLOCK.getValue(blockId);
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
