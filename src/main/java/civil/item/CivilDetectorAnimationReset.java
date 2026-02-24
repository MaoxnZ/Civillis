package civil.item;

import civil.ModItems;
import civil.component.ModComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Civilization detector animation reset: only runs when there are active animations.
 * Call {@link #markActive()} after using detector to trigger animation.
 * Server checks all players' inventory/main hand/off-hand civilization detectors every tick;
 * if DETECTOR_ANIMATION_END has arrived then restore default and remove animation end tick.
 * When no items are in animation, stop checking.
 *
 * <p>Platform entry points register events that call {@link #onPlayerJoin} and
 * {@link #onServerTick}.
 */
public final class CivilDetectorAnimationReset {

    private static volatile boolean hasActiveAnimation;

    private CivilDetectorAnimationReset() {
    }

    /** Called when using civilization detector to trigger animation. */
    public static void markActive() {
        hasActiveAnimation = true;
    }

    /** Called when player joins — reset all detectors to default state. */
    public static void onPlayerJoin(ServerPlayer player) {
        resetAllDetectorsForPlayer(player);
    }

    /** Called at end of each server tick (registered by platform entry point). */
    public static void onServerTick(MinecraftServer server) {
        if (!hasActiveAnimation) return;
        long now = server.overworld().getGameTime();
        server.getPlayerList().getPlayers().forEach(player -> {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var stack = inv.getItem(i);
                if (stack.isEmpty() || !stack.is(ModItems.getCivilDetector())) continue;
                Long endTick = stack.get(ModComponents.DETECTOR_ANIMATION_END);
                if (endTick == null) continue;
                if (now >= endTick) {
                    stack.set(ModComponents.DETECTOR_DISPLAY, "default");
                    stack.remove(ModComponents.DETECTOR_ANIMATION_END);
                }
            }
            var main = player.getMainHandItem();
            if (!main.isEmpty() && main.is(ModItems.getCivilDetector())) {
                Long endTick = main.get(ModComponents.DETECTOR_ANIMATION_END);
                if (endTick != null && now >= endTick) {
                    main.set(ModComponents.DETECTOR_DISPLAY, "default");
                    main.remove(ModComponents.DETECTOR_ANIMATION_END);
                }
            }
            var off = player.getOffhandItem();
            if (!off.isEmpty() && off.is(ModItems.getCivilDetector())) {
                Long endTick = off.get(ModComponents.DETECTOR_ANIMATION_END);
                if (endTick != null && now >= endTick) {
                    off.set(ModComponents.DETECTOR_DISPLAY, "default");
                    off.remove(ModComponents.DETECTOR_ANIMATION_END);
                }
            }
        });
        boolean stillActive = server.getPlayerList().getPlayers().stream().anyMatch(player -> {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                var stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(ModItems.getCivilDetector()) && stack.get(ModComponents.DETECTOR_ANIMATION_END) != null)
                    return true;
            }
            var main = player.getMainHandItem();
            if (!main.isEmpty() && main.is(ModItems.getCivilDetector()) && main.get(ModComponents.DETECTOR_ANIMATION_END) != null)
                return true;
            var off = player.getOffhandItem();
            return !off.isEmpty() && off.is(ModItems.getCivilDetector()) && off.get(ModComponents.DETECTOR_ANIMATION_END) != null;
        });
        if (!stillActive) hasActiveAnimation = false;
    }

    /** Forcibly restore all civilization detectors on player to default state. */
    public static void resetAllDetectorsForPlayer(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(ModItems.getCivilDetector())) continue;
            stack.set(ModComponents.DETECTOR_DISPLAY, "default");
            stack.remove(ModComponents.DETECTOR_ANIMATION_END);
        }
        var main = player.getMainHandItem();
        if (!main.isEmpty() && main.is(ModItems.getCivilDetector())) {
            main.set(ModComponents.DETECTOR_DISPLAY, "default");
            main.remove(ModComponents.DETECTOR_ANIMATION_END);
        }
        var off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ModItems.getCivilDetector())) {
            off.set(ModComponents.DETECTOR_DISPLAY, "default");
            off.remove(ModComponents.DETECTOR_ANIMATION_END);
        }
    }
}
