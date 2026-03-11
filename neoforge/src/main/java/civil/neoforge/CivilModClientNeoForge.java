package civil.neoforge;

import civil.aura.AuraWallRenderer;
import civil.config.CivilConfigScreen;
import civil.item.CivilDetectorClientParticles;
import civil.respawn.UndyingAnchorCinematicEffect;
import civil.respawn.UndyingAnchorParticleEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client initialization. Called from {@link CivilModNeoForge}
 * only when running on the client distribution.
 */
final class CivilModClientNeoForge {

    private CivilModClientNeoForge() {
    }

    static void init(IEventBus modBus, ModContainer modContainer) {
        CivilDetectorClientParticles.register();
        NeoForge.EVENT_BUS.addListener(CivilModClientNeoForge::onAfterTranslucent);
        NeoForge.EVENT_BUS.addListener(CivilModClientNeoForge::onRenderGuiPost);
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (mc, parent) -> CivilConfigScreen.create(parent));
    }

    private static void onAfterTranslucent(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        var poseStack = event.getPoseStack();
        UndyingAnchorCinematicEffect.tickAndApplyShake(poseStack);
        UndyingAnchorParticleEffect.tick();
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var entity = camera.entity();
        if (entity != null) {
            Vec3 cam = entity.getEyePosition();
            AuraWallRenderer.onRender(cam);
        }
    }

    private static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.BOSS_OVERLAY.equals(event.getName())) return;
        if (!UndyingAnchorCinematicEffect.isActive()) return;
        UndyingAnchorCinematicEffect.renderOverlay(
                event.getGuiGraphics(),
                event.getPartialTick().getGameTimeDeltaPartialTick(true));
    }
}
