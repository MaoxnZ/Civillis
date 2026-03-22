package civil.neoforge;

import civil.aura.AuraWallRenderer;
import civil.civilization.ZoneTransitionHud;
import civil.config.CivilConfigScreen;
import civil.item.CivilDetectorClientParticles;
import civil.respawn.UndyingAnchorCinematicEffect;
import civil.respawn.UndyingAnchorParticleEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

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
        NeoForge.EVENT_BUS.addListener(CivilModClientNeoForge::onRenderGuiPostZone);
        NeoForge.EVENT_BUS.addListener(CivilModClientNeoForge::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(CivilModClientNeoForge::onClientPlayerLoggingIn);
        NeoForge.EVENT_BUS.addListener(CivilModClientNeoForge::onClientPlayerLoggingOut);
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (mc, parent) -> CivilConfigScreen.create(parent));
    }

    private static void onAfterTranslucent(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        var poseStack = event.getPoseStack();
        UndyingAnchorCinematicEffect.tickAndApplyShake(poseStack);
        UndyingAnchorParticleEffect.tick();
        // Match Fabric WorldRenderEvents (cameraRenderState.pos): per-frame interpolated eye position.
        // entity.getEyePosition() without partial tick snaps to tick boundaries → walls jitter vs smooth world.
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Entity entity = mc.gameRenderer.getMainCamera().entity();
        if (entity != null) {
            Vec3 cam = entity.getEyePosition(pt);
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

    private static void onRenderGuiPostZone(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.CROSSHAIR.equals(event.getName())) return;
        ZoneTransitionHud.render(
                event.getGuiGraphics(),
                event.getPartialTick().getGameTimeDeltaPartialTick(true));
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        ZoneTransitionHud.tick();
    }

    private static void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ZoneTransitionHud.resetForWorldSession();
    }

    private static void onClientPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ZoneTransitionHud.resetForWorldSession();
    }
}
