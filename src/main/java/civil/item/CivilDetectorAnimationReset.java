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

    public static void markActive() {
        hasActiveAnimation = true;
    }

    public static void onPlayerJoin(ServerPlayer player) {
        resetAllDetectorsForPlayer(player);
    }

    public static void onServerTick(MinecraftServer server) {
        if (!hasActiveAnimation) return;
        long now = server.overworld().getGameTime();
        server.getPlayerList().getPlayers().forEach(player -> {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var stack = inv.getItem(i);
                if (stack.isEmpty() || !stack.is(ModItems.getCivilDetector())) continue;
                if (!ModComponents.hasAnimationEnd(stack)) continue;
                long endTick = ModComponents.getAnimationEnd(stack);
                if (now >= endTick) {
                    ModComponents.setDisplay(stack, "default");
                    ModComponents.removeAnimationEnd(stack);
                }
            }
            var main = player.getMainHandItem();
            if (!main.isEmpty() && main.is(ModItems.getCivilDetector()) && ModComponents.hasAnimationEnd(main)) {
                long endTick = ModComponents.getAnimationEnd(main);
                if (now >= endTick) {
                    ModComponents.setDisplay(main, "default");
                    ModComponents.removeAnimationEnd(main);
                }
            }
            var off = player.getOffhandItem();
            if (!off.isEmpty() && off.is(ModItems.getCivilDetector()) && ModComponents.hasAnimationEnd(off)) {
                long endTick = ModComponents.getAnimationEnd(off);
                if (now >= endTick) {
                    ModComponents.setDisplay(off, "default");
                    ModComponents.removeAnimationEnd(off);
                }
            }
        });
        boolean stillActive = server.getPlayerList().getPlayers().stream().anyMatch(player -> {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                var stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(ModItems.getCivilDetector()) && ModComponents.hasAnimationEnd(stack))
                    return true;
            }
            var main = player.getMainHandItem();
            if (!main.isEmpty() && main.is(ModItems.getCivilDetector()) && ModComponents.hasAnimationEnd(main))
                return true;
            var off = player.getOffhandItem();
            return !off.isEmpty() && off.is(ModItems.getCivilDetector()) && ModComponents.hasAnimationEnd(off);
        });
        if (!stillActive) hasActiveAnimation = false;
    }

    public static void resetAllDetectorsForPlayer(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(ModItems.getCivilDetector())) continue;
            ModComponents.setDisplay(stack, "default");
            ModComponents.removeAnimationEnd(stack);
        }
        var main = player.getMainHandItem();
        if (!main.isEmpty() && main.is(ModItems.getCivilDetector())) {
            ModComponents.setDisplay(main, "default");
            ModComponents.removeAnimationEnd(main);
        }
        var off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ModItems.getCivilDetector())) {
            ModComponents.setDisplay(off, "default");
            ModComponents.removeAnimationEnd(off);
        }
    }
}
