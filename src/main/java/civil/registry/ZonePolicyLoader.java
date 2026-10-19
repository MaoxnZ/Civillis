package civil.registry;

import civil.CivilMod;
import civil.CivilServices;
import civil.civilization.ZonePolicyService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Datapack reload: loads {@code data/&lt;namespace&gt;/civil_zone_policies/*.json} into
 * {@link ZonePolicyRegistry} and invalidates {@link ZonePolicyService}.
 */
public final class ZonePolicyLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-registry");
    private static final String DATA_PATH = "civil_zone_policies";

    private ZonePolicyLoader() {}

    /**
     * @param registryAccess used to filter {@code structures} to ids present in {@link Registries#STRUCTURE}
     *         (unknown ids are skipped with a warning, like {@code allow_mobs} and {@link BlockWeightLoader}).
     */
    public static void reload(ResourceManager manager, RegistryAccess registryAccess) {
        ArrayList<ZonePolicyRegistry.ZonePolicyRule> accumulated = new ArrayList<>();

        Map<Identifier, Resource> resources = manager.listResources(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier fileId = entry.getKey();
            try (InputStream is = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
                if (replace) {
                    LOGGER.warn("[civil-registry] replace=true in {} — clearing {} previously loaded zone policy rule(s)",
                            fileId, accumulated.size());
                    accumulated.clear();
                }

                if (!root.has("entries")) continue;
                JsonArray entries = root.getAsJsonArray("entries");
                int idx = 0;
                for (JsonElement elem : entries) {
                    JsonObject obj = elem.getAsJsonObject();
                    String ruleId = obj.has("id") ? obj.get("id").getAsString() : fileId + "#" + ++idx;

                    LinkedHashSet<Identifier> structures = new LinkedHashSet<>();
                    if (obj.has("structures") && obj.get("structures").isJsonArray()) {
                        JsonArray structuresArr = obj.getAsJsonArray("structures");
                        var structureLookup = registryAccess.lookupOrThrow(Registries.STRUCTURE);
                        for (JsonElement se : structuresArr) {
                            String raw = se.getAsString();
                            try {
                                Identifier id = Identifier.parse(raw);
                                ResourceKey<Structure> sKey = ResourceKey.create(Registries.STRUCTURE, id);
                                if (structureLookup.get(sKey).isEmpty()) {
                                    if (CivilMod.DEBUG) {
                                        LOGGER.warn("[civil-registry] Unknown structure '{}' in {} (rule={}), skipping",
                                                raw, fileId, ruleId);
                                    }
                                    continue;
                                }
                                structures.add(id);
                            } catch (Exception ex) {
                                if (CivilMod.DEBUG) {
                                    LOGGER.warn("[civil-registry] Invalid structure id '{}' in {} (rule={}), skipping",
                                            raw, fileId, ruleId);
                                }
                            }
                        }
                    }
                    if (structures.isEmpty()) {
                        if (CivilMod.DEBUG) {
                            LOGGER.warn("[civil-registry] Rule '{}' in {} has no valid structures, skipping", ruleId, fileId);
                        }
                        continue;
                    }

                    JsonObject rulesObj = obj.has("rules") && obj.get("rules").isJsonObject()
                            ? obj.getAsJsonObject("rules") : new JsonObject();
                    boolean allowHostileSpawn = rulesObj.has("allow_hostile_spawn")
                            && rulesObj.get("allow_hostile_spawn").getAsBoolean();

                    LinkedHashSet<EntityType<?>> allowMobs = new LinkedHashSet<>();
                    if (rulesObj.has("allow_mobs") && rulesObj.get("allow_mobs").isJsonArray()) {
                        JsonArray mobsArr = rulesObj.getAsJsonArray("allow_mobs");
                        for (JsonElement me : mobsArr) {
                            String mobId = me.getAsString();
                            try {
                                Identifier parsed = Identifier.parse(mobId);
                                if (BuiltInRegistries.ENTITY_TYPE.containsKey(parsed)) {
                                    allowMobs.add(BuiltInRegistries.ENTITY_TYPE.getValue(parsed));
                                } else {
                                    if (CivilMod.DEBUG) {
                                        LOGGER.warn("[civil-registry] Unknown entity type '{}' in {} (rule={}), skipping",
                                                mobId, fileId, ruleId);
                                    }
                                }
                            } catch (Exception ex) {
                                if (CivilMod.DEBUG) {
                                    LOGGER.warn("[civil-registry] Invalid entity id '{}' in {} (rule={}), skipping",
                                            mobId, fileId, ruleId);
                                }
                            }
                        }
                    }

                    ZonePolicyRegistry.Rules rules = new ZonePolicyRegistry.Rules(allowHostileSpawn, Set.copyOf(allowMobs));
                    accumulated.add(new ZonePolicyRegistry.ZonePolicyRule(ruleId, Set.copyOf(structures), rules));
                }
            } catch (Exception e) {
                LOGGER.error("[civil-registry] Failed to load zone policy from {}: {}", fileId, e.getMessage());
            }
        }

        ZonePolicyRegistry.reload(accumulated);
        ZonePolicyService service = CivilServices.getZonePolicyService();
        if (service != null) {
            service.clear();
        }
        LOGGER.info("[civil-registry] Loaded {} zone policy rule(s) from datapack", accumulated.size());
    }
}
