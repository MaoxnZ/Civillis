package civil.neoforge;

import civil.CivilMod;
import civil.ModMenuTypes;
import civil.compat.bandsync.ChunkBandClientSession;
import civil.compat.xaeros.XaerosChunkBandHooks;
import civil.ModItems;
import civil.aura.AuraWallRenderer;
import civil.towncenter.gui.TownCenterMainScreen;
import civil.towncenter.gui.TownCenterMenu;
import civil.civilization.ZoneTransitionHud;
import civil.component.ModComponents;
import civil.config.CivilConfigScreen;
import civil.item.CivilDetectorClientParticles;
import civil.respawn.UndyingAnchorCinematicEffect;
import civil.respawn.UndyingAnchorParticleEffect;
import civil.shrine.FarmShrineParticleEffect;
import civil.towncenter.TownCenterActivationBurstEffect;
import civil.towncenter.TownCenterParticleEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * NeoForge client initialization. Called from {@link CivilModNeoForge}
 * only when running on the client distribution.
 */
final class CivilModClientNeoForge {

    private CivilModClientNeoForge() {
    }

    static void init(IEventBus modBus, ModContainer modContainer) {
        CivilDetectorClientParticles.register();
        XaerosChunkBandHooks.register();
        modBus.addListener(CivilModClientNeoForge::registerMenuScreens);
        modBus.addListener(CivilModClientNeoForge::onClientSetup);
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

    private static void registerMenuScreens(RegisterMenuScreensEvent event) {
        MenuType<TownCenterMenu> type = CivilModNeoForge.townCenterMenuType();
        ModMenuTypes.setTownCenter(type);
        event.register(type, TownCenterMainScreen::new);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(CivilModClientNeoForge::registerModelPredicates);
    }

    private static void registerModelPredicates() {
        ItemProperties.register(
                ModItems.getCivilDetector(),
                ResourceLocation.fromNamespaceAndPath(CivilMod.MOD_ID, "display_state"),
                (stack, world, entity, seed) -> {
                    String display = stack.get(ModComponents.DETECTOR_DISPLAY);
                    if (display == null) return 0.0f;
                    return switch (display) {
                        case "low" -> 0.25f;
                        case "medium" -> 0.5f;
                        case "high" -> 0.75f;
                        case "zone" -> 0.875f;
                        case "monster" -> 1.0f;
                        default -> 0.0f;
                    };
                });
    }

    private static void onAfterTranslucent(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        UndyingAnchorCinematicEffect.tickAndApplyShake(event.getPoseStack());
        UndyingAnchorParticleEffect.tick();
        FarmShrineParticleEffect.tick();
        TownCenterParticleEffect.tick();
        TownCenterActivationBurstEffect.tick();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Entity entity = event.getCamera().getEntity();
        if (entity != null) {
            Vec3 cam = entity.getEyePosition(pt);
            AuraWallRenderer.onRender(cam);
        }
    }

    private static void onRenderGuiPost(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.BOSS_OVERLAY.equals(event.getName())) return;
        if (!UndyingAnchorCinematicEffect.isActive()) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        UndyingAnchorCinematicEffect.renderOverlay(event.getGuiGraphics(), pt);
    }

    private static void onRenderGuiPostZone(RenderGuiLayerEvent.Post event) {
        if (!VanillaGuiLayers.CROSSHAIR.equals(event.getName())) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        ZoneTransitionHud.render(event.getGuiGraphics(), pt);
    }

    private static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ChunkBandClientSession.tick(mc);
        ZoneTransitionHud.tick();
    }

    private static void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ZoneTransitionHud.resetForWorldSession();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            ChunkBandClientSession.onPlayJoined(mc, mc.getConnection());
        }
    }

    private static void onClientPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ChunkBandClientSession.onPlayDisconnect(Minecraft.getInstance());
        ZoneTransitionHud.resetForWorldSession();
    }
}
