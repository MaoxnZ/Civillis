package civil.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
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
 * Datapack reload: loads {@code data/<namespace>/civil_town_center_levels/*.json} into
 * {@link TownCenterLevelRegistry}.
 *
 * <p>Each file may define:
 * <ul>
 *   <li>{@code offers.zoneBuffOffers} — optional offer definitions (merged; later files override by id)</li>
 *   <li>{@code entries} or {@code levels} — upgrade steps; {@code targetLevel} keys override earlier files</li>
 * </ul>
 *
 * <p>{@code replace: true} clears all offers and level steps loaded so far.
 *
 * <p>Stored effect amplifier is computed at upgrade time from TC {@code appliedEffects} and offer {@code increment}.
 */
public final class TownCenterLevelLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-registry");
    private static final String DATA_PATH = "civil_town_center_levels";

    private TownCenterLevelLoader() {}

    public static void reload(ResourceManager manager) {
        Map<Identifier, Resource> resources = manager.listResources(
                DATA_PATH, id -> id.getPath().endsWith(".json"));

        List<Map.Entry<Identifier, Resource>> sorted = new ArrayList<>(resources.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getKey().toString()));

        HashMap<String, TownCenterLevelRegistry.ZoneBuffOffer> offers = new HashMap<>();
        HashMap<Integer, TownCenterLevelRegistry.LevelStep> steps = new HashMap<>();
        int maxLevel = 5;

        for (Map.Entry<Identifier, Resource> entry : sorted) {
            Identifier fileId = entry.getKey();
            try (InputStream is = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
                if (replace) {
                    LOGGER.warn("[civil-registry] replace=true in {} — clearing {} town center level(s) and {} offer(s)",
                            fileId, steps.size(), offers.size());
                    offers.clear();
                    steps.clear();
                    maxLevel = 5;
                }

                parseOffers(root, fileId, offers);
                maxLevel = Math.max(maxLevel, parseLevels(root, fileId, offers, steps));
            } catch (Exception e) {
                LOGGER.error("[civil-registry] Failed to load town center levels from {}: {}",
                        fileId, e.getMessage());
            }
        }

        TownCenterLevelRegistry.reload(steps, offers, maxLevel);
        LOGGER.info("[civil-registry] Loaded {} town center level step(s), {} zone buff offer(s), maxLevel={}",
                steps.size(), offers.size(), maxLevel);
    }

    private static void parseOffers(JsonObject root, Identifier fileId,
                                    HashMap<String, TownCenterLevelRegistry.ZoneBuffOffer> offers) {
        if (!root.has("offers")) return;
        JsonObject offersRoot = root.getAsJsonObject("offers");
        if (!offersRoot.has("zoneBuffOffers")) return;
        JsonArray offerArr = offersRoot.getAsJsonArray("zoneBuffOffers");
        for (JsonElement el : offerArr) {
            JsonObject o = el.getAsJsonObject();
            String id = o.get("id").getAsString();
            Identifier effectId = Identifier.parse(o.get("effect").getAsString());
            var effectOpt = BuiltInRegistries.MOB_EFFECT.getOptional(effectId);
            if (effectOpt.isEmpty()) {
                LOGGER.warn("[civil-registry] Unknown effect {} in offer {} ({})", effectId, id, fileId);
                continue;
            }
            TownCenterLevelRegistry.ZoneBuffOffer offer = new TownCenterLevelRegistry.ZoneBuffOffer(
                    id,
                    effectOpt.get(),
                    parseIncrement(o, id, fileId),
                    TownCenterLevelRegistry.PaymentTier.fromString(o.get("paymentTier").getAsString()),
                    o.get("translationKey").getAsString(),
                    o.get("ambient").getAsBoolean(),
                    o.get("showParticles").getAsBoolean(),
                    o.get("showIcon").getAsBoolean());
            offers.put(id, offer);
        }
    }

    private static int parseIncrement(JsonObject o, String offerId, Identifier fileId) {
        int inc = o.has("increment") ? o.get("increment").getAsInt() : 1;
        if (inc < 1) {
            LOGGER.warn("[civil-registry] increment < 1 for offer {} ({}), clamping to 1", offerId, fileId);
            inc = 1;
        }
        return inc;
    }

    private static int parseLevels(JsonObject root, Identifier fileId,
                                   HashMap<String, TownCenterLevelRegistry.ZoneBuffOffer> offers,
                                   HashMap<Integer, TownCenterLevelRegistry.LevelStep> steps) {
        JsonArray levels = null;
        if (root.has("entries")) {
            levels = root.getAsJsonArray("entries");
        } else if (root.has("levels")) {
            levels = root.getAsJsonArray("levels");
        }
        if (levels == null) return 5;

        int maxLevel = 5;
        for (JsonElement el : levels) {
            JsonObject lv = el.getAsJsonObject();
            int target = lv.get("targetLevel").getAsInt();
            JsonObject up = lv.getAsJsonObject("upgrade");
            TownCenterLevelRegistry.UpgradeStep upgrade = new TownCenterLevelRegistry.UpgradeStep(
                    up.get("emeraldBlockCost").getAsInt(),
                    up.get("horizRadiusVc").getAsInt(),
                    up.get("vertRadiusVc").getAsInt(),
                    up.get("baseScoreRaw").getAsDouble());

            List<TownCenterLevelRegistry.ZoneBuffOffer> resolved = resolveOffers(lv, offers, fileId, target);
            if (resolved.size() != 3) {
                LOGGER.warn("[civil-registry] Level {} in {} expected 3 offers, got {}",
                        target, fileId, resolved.size());
            }
            steps.put(target, new TownCenterLevelRegistry.LevelStep(target, upgrade, List.copyOf(resolved)));
            maxLevel = Math.max(maxLevel, target);
        }
        return maxLevel;
    }

    private static List<TownCenterLevelRegistry.ZoneBuffOffer> resolveOffers(
            JsonObject lv,
            HashMap<String, TownCenterLevelRegistry.ZoneBuffOffer> offers,
            Identifier fileId,
            int target) {
        if (lv.has("zoneBuffOffers")) {
            List<TownCenterLevelRegistry.ZoneBuffOffer> inline = new ArrayList<>();
            for (JsonElement el : lv.getAsJsonArray("zoneBuffOffers")) {
                JsonObject o = el.getAsJsonObject();
                String id = o.get("id").getAsString();
                Identifier effectId = Identifier.parse(o.get("effect").getAsString());
                var effectOpt = BuiltInRegistries.MOB_EFFECT.getOptional(effectId);
                if (effectOpt.isEmpty()) {
                    LOGGER.warn("[civil-registry] Unknown effect {} in inline offer {} ({})",
                            effectId, id, fileId);
                    continue;
                }
                TownCenterLevelRegistry.ZoneBuffOffer offer = new TownCenterLevelRegistry.ZoneBuffOffer(
                        id,
                        effectOpt.get(),
                        parseIncrement(o, id, fileId),
                        TownCenterLevelRegistry.PaymentTier.fromString(o.get("paymentTier").getAsString()),
                        o.get("translationKey").getAsString(),
                        o.get("ambient").getAsBoolean(),
                        o.get("showParticles").getAsBoolean(),
                        o.get("showIcon").getAsBoolean());
                offers.put(id, offer);
                inline.add(offer);
            }
            return inline;
        }

        List<TownCenterLevelRegistry.ZoneBuffOffer> resolved = new ArrayList<>();
        if (!lv.has("zoneBuffOfferIds")) return resolved;
        for (JsonElement idEl : lv.getAsJsonArray("zoneBuffOfferIds")) {
            TownCenterLevelRegistry.ZoneBuffOffer offer = offers.get(idEl.getAsString());
            if (offer != null) resolved.add(offer);
        }
        return resolved;
    }
}
