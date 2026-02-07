package civil.item;

import civil.ModItems;
import civil.component.ModComponents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Civilization detector animation reset: only runs when there are active animations; call {@link #markActive()} after using detector to trigger animation,
 * server checks all players' inventory/main hand/off-hand civilization detectors every tick, if DETECTOR_ANIMATION_END has arrived then restore default and remove animation end tick;
 * when no items are in animation, stop checking.
 * When player joins world, forcibly resets all civilization detectors on that player to default, avoiding previous session's state being persisted and brought into new session.
 */
public final class CivilDetectorAnimationReset {

    private static volatile boolean hasActiveAnimation;

    private CivilDetectorAnimationReset() {
    }

    /** Called when using civilization detector to trigger animation, starts checking for animation end from this tick. */
    public static void markActive() {
        hasActiveAnimation = true;
    }

    /** Forcibly restore all civilization detectors on player to default state (for reset when entering game). */
    public static void resetAllDetectorsForPlayer(net.minecraft.server.network.ServerPlayerEntity player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            var stack = inv.getStack(i);
            if (stack.isEmpty() || !stack.isOf(ModItems.getCivilDetector())) continue;
            stack.set(ModComponents.DETECTOR_DISPLAY, "default");
            stack.remove(ModComponents.DETECTOR_ANIMATION_END);
        }
        var main = player.getMainHandStack();
        if (!main.isEmpty() && main.isOf(ModItems.getCivilDetector())) {
            main.set(ModComponents.DETECTOR_DISPLAY, "default");
            main.remove(ModComponents.DETECTOR_ANIMATION_END);
        }
        var off = player.getOffHandStack();
        if (!off.isEmpty() && off.isOf(ModItems.getCivilDetector())) {
            off.set(ModComponents.DETECTOR_DISPLAY, "default");
            off.remove(ModComponents.DETECTOR_ANIMATION_END);
        }
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                resetAllDetectorsForPlayer(handler.player));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!hasActiveAnimation) return;
            long now = server.getOverworld().getTime();
            server.getPlayerManager().getPlayerList().forEach(player -> {
                var inv = player.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    var stack = inv.getStack(i);
                    if (stack.isEmpty() || !stack.isOf(ModItems.getCivilDetector())) continue;
                    Long endTick = stack.get(ModComponents.DETECTOR_ANIMATION_END);
                    if (endTick == null) continue;
                    if (now >= endTick) {
                        stack.set(ModComponents.DETECTOR_DISPLAY, "default");
                        stack.remove(ModComponents.DETECTOR_ANIMATION_END);
                    }
                }
                var main = player.getMainHandStack();
                if (!main.isEmpty() && main.isOf(ModItems.getCivilDetector())) {
                    Long endTick = main.get(ModComponents.DETECTOR_ANIMATION_END);
                    if (endTick != null && now >= endTick) {
                        main.set(ModComponents.DETECTOR_DISPLAY, "default");
                        main.remove(ModComponents.DETECTOR_ANIMATION_END);
                    }
                }
                var off = player.getOffHandStack();
                if (!off.isEmpty() && off.isOf(ModItems.getCivilDetector())) {
                    Long endTick = off.get(ModComponents.DETECTOR_ANIMATION_END);
                    if (endTick != null && now >= endTick) {
                        off.set(ModComponents.DETECTOR_DISPLAY, "default");
                        off.remove(ModComponents.DETECTOR_ANIMATION_END);
                    }
                }
            });
            // If no items are in animation, stop subsequent tick checks
            boolean stillActive = server.getPlayerManager().getPlayerList().stream().anyMatch(player -> {
                for (int i = 0; i < player.getInventory().size(); i++) {
                    var stack = player.getInventory().getStack(i);
                    if (!stack.isEmpty() && stack.isOf(ModItems.getCivilDetector()) && stack.get(ModComponents.DETECTOR_ANIMATION_END) != null)
                        return true;
                }
                var main = player.getMainHandStack();
                if (!main.isEmpty() && main.isOf(ModItems.getCivilDetector()) && main.get(ModComponents.DETECTOR_ANIMATION_END) != null)
                    return true;
                var off = player.getOffHandStack();
                return !off.isEmpty() && off.isOf(ModItems.getCivilDetector()) && off.get(ModComponents.DETECTOR_ANIMATION_END) != null;
            });
            if (!stillActive) hasActiveAnimation = false;
        });
    }
}
