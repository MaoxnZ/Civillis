package civil.item;

import civil.ModItems;
import civil.component.ModComponents;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;

/**
 * Civilization detector client particles: when held within 2 seconds after right-click detection, generates flickering particles
 * (normal white/light green ENCHANT, red FLAME when monster head).
 */
@Environment(EnvType.CLIENT)
public final class CivilDetectorClientParticles {

    private CivilDetectorClientParticles() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) {
                return;
            }
            ItemStack main = client.player.getMainHandStack();
            if (main.isEmpty() || !main.isOf(ModItems.getCivilDetector())) {
                return;
            }
            String display = main.get(ModComponents.DETECTOR_DISPLAY);
            Long endTick = main.get(ModComponents.DETECTOR_ANIMATION_END);
            if (display == null || "default".equals(display) || endTick == null) {
                return;
            }
            if (client.world.getTime() >= endTick) {
                return;
            }
            if (client.world.getTime() % 2 != 0) {
                return;
            }
            double x = client.player.getX();
            double y = client.player.getY() + client.player.getEyeHeight(EntityPose.STANDING) * 0.6;
            double z = client.player.getZ();
            double spread = 0.3;
            double vx = (client.world.getRandom().nextDouble() - 0.5) * 0.1;
            double vy = 0.02 + client.world.getRandom().nextDouble() * 0.02;
            double vz = (client.world.getRandom().nextDouble() - 0.5) * 0.1;
            double px = x + (client.world.getRandom().nextDouble() - 0.5) * spread;
            double py = y + (client.world.getRandom().nextDouble() - 0.5) * spread;
            double pz = z + (client.world.getRandom().nextDouble() - 0.5) * spread;
            var particleManager = MinecraftClient.getInstance().particleManager;
            if ("monster".equals(display)) {
                particleManager.addParticle(ParticleTypes.FLAME, px, py, pz, vx, vy, vz);
            } else {
                particleManager.addParticle(ParticleTypes.ENCHANT, px, py, pz, vx, vy, vz);
            }
        });
    }
}
