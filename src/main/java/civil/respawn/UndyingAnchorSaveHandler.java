package civil.respawn;

import civil.CivilPlatform;
import civil.CivilServices;
import civil.civilization.UndyingAnchorTracker;
import civil.civilization.UndyingAnchorTracker.ValidAnchorResult;
import civil.config.CivilConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Civil save logic: when a player would die, teleport them to a nearby emerald block
 * in a high-civilization area and apply totem effects. No charge consumption.
 *
 * <p>Called from platform-specific death interception (Fabric AllowDeath, NeoForge LivingDeathEvent).
 * Returns true if the death was prevented and the player was saved.
 */
public final class UndyingAnchorSaveHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("civil-undying-anchor");

    /** Totem effect durations — same as vanilla Totem of Undying (Java Edition). */
    private static final int REGENERATION_TICKS = 900;   // Regeneration II, 45s
    private static final int ABSORPTION_TICKS = 100;    // Absorption II, 5s
    private static final int FIRE_RESISTANCE_TICKS = 800; // Fire Resistance I, 40s

    /** Health after save (1 heart = 2.0f). */
    private static final float SAVED_HEALTH = 2.0f;

    /** Players immobilized until this game tick (UUID -> end tick). Movement blocked via AttributeModifier. */
    private static final Map<UUID, Long> IMMOBILIZED_UNTIL = new ConcurrentHashMap<>();

    /** Pending teleports: UUID -> execute at tick. Teleport happens after phase 0. */
    private static final Map<UUID, PendingTeleport> PENDING_TELEPORTS = new ConcurrentHashMap<>();

    private static final Identifier IMMOBILIZE_MODIFIER_ID =
            Identifier.fromNamespaceAndPath("civil", "undying_cinematic_immobilize");

    private record PendingTeleport(
            BlockPos anchorPos, BlockPos teleportDest, ServerLevel world, long executeAtTick) {}

    private UndyingAnchorSaveHandler() {}

    /**
     * Clear pending teleports and immobilizations for the unloading world.
     * Call from onWorldUnload to avoid stale references and players stuck immobilized in a new world.
     */
    public static void clearForWorld(ServerLevel world) {
        PENDING_TELEPORTS.entrySet().removeIf(entry -> {
            if (entry.getValue().world != world) return false;
            IMMOBILIZED_UNTIL.remove(entry.getKey());
            return true;
        });
    }

    /**
     * Called each server tick. Processes pending teleports and removes immobilization when cinematic ends.
     */
    public static void onServerTick(MinecraftServer server) {
        long now = server.overworld().getGameTime();

        // Execute pending teleports after phase 0
        PENDING_TELEPORTS.entrySet().removeIf(entry -> {
            PendingTeleport p = entry.getValue();
            if (now < p.executeAtTick) return false;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.level() != p.world) return true;
            executeTeleport(player, p);
            return true;
        });

        // Remove immobilization and invulnerability when cinematic ends
        IMMOBILIZED_UNTIL.entrySet().removeIf(entry -> {
            if (now >= entry.getValue()) {
                ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
                if (p != null) {
                    removeImmobilizeModifier(p);
                    p.setInvulnerable(false);
                }
                return true;
            }
            return false;
        });
    }

    private static void executeTeleport(ServerPlayer player, PendingTeleport p) {
        player.teleportTo(
                p.teleportDest.getX() + 0.5, p.teleportDest.getY(), p.teleportDest.getZ() + 0.5);
        player.setHealth(SAVED_HEALTH);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGENERATION_TICKS, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ABSORPTION_TICKS, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_RESISTANCE_TICKS, 0));
    }

    private static void addImmobilizeModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && attr.getModifier(IMMOBILIZE_MODIFIER_ID) == null) {
            attr.addTransientModifier(new AttributeModifier(
                    IMMOBILIZE_MODIFIER_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeImmobilizeModifier(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null) attr.removeModifier(IMMOBILIZE_MODIFIER_ID);
    }

    /**
     * Attempt to save the player using a nearby civil undying anchor.
     *
     * @param player the player who would die
     * @return true if saved (death should be prevented), false if no valid anchor (allow death)
     */
    public static boolean trySave(ServerPlayer player) {
        if (!CivilConfig.undyingAnchorEnabled) return false;

        // Fallback only: totem has priority. If player holds totem, let totem save them.
        if (player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return false;
        }

        UndyingAnchorTracker tracker = CivilServices.getUndyingAnchorTracker();
        if (tracker == null || !tracker.isInitialized()) return false;

        ServerLevel world = (ServerLevel) player.level();
        BlockPos playerPos = player.blockPosition();

        ValidAnchorResult result = tracker.findNearestValidAnchor(
                world, playerPos, CivilConfig.undyingAnchorMaxSearchRadius);
        if (result == null) return false;

        BlockPos anchorPos = result.anchorPos();
        BlockPos teleportDest = result.teleportDest();

        if (!world.getBlockState(anchorPos).is(Blocks.EMERALD_BLOCK)) return false;

        // Immediate: prevent re-death, invulnerable during cinematic, immobilize, send pre-teleport
        player.setHealth(SAVED_HEALTH);
        player.setInvulnerable(true);  // prevent cascade death (fire, fall, mob hits) during cinematic
        addImmobilizeModifier(player);
        IMMOBILIZED_UNTIL.put(player.getUUID(), world.getGameTime() + UndyingAnchorCinematic.TOTAL_CINEMATIC_TICKS);

        long executeAtTick = world.getGameTime() + UndyingAnchorCinematic.TELEPORT_AFTER_TICKS;
        PENDING_TELEPORTS.put(player.getUUID(), new PendingTeleport(
                anchorPos, teleportDest, world, executeAtTick));

        CivilPlatform.sendToPlayer(player, new UndyingAnchorPreTeleportPayload(
                UndyingAnchorCinematic.PHASE0_TICKS,
                anchorPos.getX(), anchorPos.getY(), anchorPos.getZ()));

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("[civil-undying-anchor] Saved player {} at emerald block ({},{},{})",
                    player.getName().getString(), anchorPos.getX(), anchorPos.getY(), anchorPos.getZ());
        }

        tracker.onRescueUsed(world.dimension().identifier().toString(),
                anchorPos.getX(), anchorPos.getY(), anchorPos.getZ());

        return true;
    }
}
