package civil.progress;

import civil.CivilMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side helper to grant advancements that use {@code minecraft:impossible} criteria
 * (e.g. structure milestones) or to centralize resource locations.
 */
public final class CivilAdvancements {

    public static final Identifier BELL_SONAR = id("guide/structures/bell_sonar");
    public static final Identifier FARM_SHRINE = id("guide/structures/farm_shrine");
    public static final Identifier UNDYING_ANCHOR = id("guide/structures/undying_anchor");
    /** Detector reads HIGH at current position (via {@code civil:guide/items/high_civilization}). */
    public static final Identifier HIGH_CIVILIZATION = id("guide/items/high_civilization");

    private CivilAdvancements() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(CivilMod.MOD_ID, path);
    }

    /**
     * Awards every criterion of the given advancement (idempotent: already-completed
     * criteria are no-ops). Use for {@code impossible} triggers granted from code.
     */
    public static void tryAward(ServerPlayer player, Identifier advancementId) {
        if (player == null) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        AdvancementHolder holder = server.getAdvancements().get(advancementId);
        if (holder == null) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.warn("[civil] advancement not found: {}", advancementId);
            }
            return;
        }
        for (String criterion : holder.value().criteria().keySet()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
