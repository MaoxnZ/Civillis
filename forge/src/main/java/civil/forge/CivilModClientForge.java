package civil.forge;

import civil.CivilMod;
import civil.ModItems;
import civil.ModMenuTypes;
import civil.aura.AuraWallRenderer;
import civil.aura.SonarBoundaryPayload;
import civil.aura.SonarChargePayload;
import civil.aura.SonarShockwaveEffect;
import civil.aura.SonarType;
import civil.civilization.ZoneTransitionHud;
import civil.civilization.ZoneTransitionPayload;
import civil.compat.bandsync.ChunkBandClientCache;
import civil.compat.bandsync.ChunkBandClientSession;
import civil.compat.bandsync.ChunkBandSubscriptionHub;
import civil.compat.xaeros.XaerosChunkBandHooks;
import civil.compat.bandsync.ChunkBandSyncPolicyPayload;
import civil.compat.bandsync.ChunkBandTintPayload;
import civil.component.ModComponents;
import civil.item.CivilDetectorClientParticles;
import civil.respawn.UndyingAnchorCinematicEffect;
import civil.respawn.UndyingAnchorParticleEffect;
import civil.respawn.UndyingAnchorParticlePayload;
import civil.respawn.UndyingAnchorPreTeleportPayload;
import civil.shrine.FarmShrineParticleEffect;
import civil.shrine.FarmShrineParticlePayload;
import civil.towncenter.TownCenterActivationBurstEffect;
import civil.towncenter.TownCenterActivationBurstPayload;
import civil.towncenter.TownCenterParticleEffect;
import civil.towncenter.TownCenterParticlePayload;
import civil.towncenter.gui.TownCenterClientState;
import civil.towncenter.gui.TownCenterMainScreen;
import civil.towncenter.gui.TownCenterScreenBase;
import civil.towncenter.network.TownCenterGuiSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Forge client-side event handlers: render hooks and network packet handling.
 * Only loaded on the client via guarded calls from {@link CivilModForge}.
 */
@OnlyIn(Dist.CLIENT)
public class CivilModClientForge {

    static void registerClientEvents() {
        CivilDetectorClientParticles.register();
        XaerosChunkBandHooks.register();
        MinecraftForge.EVENT_BUS.addListener(CivilModClientForge::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener(CivilModClientForge::onRenderGuiPost);
        MinecraftForge.EVENT_BUS.addListener(CivilModClientForge::onClientTickEnd);
        MinecraftForge.EVENT_BUS.addListener(CivilModClientForge::onClientPlayerLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(CivilModClientForge::onClientPlayerLoggingOut);
        /** Same role as Fabric {@code ClientLifecycleEvents.CLIENT_STOPPING}: flush bandsync when the JVM/game closes without a reliable {@link ClientPlayerNetworkEvent.LoggingOut}. */
        MinecraftForge.EVENT_BUS.addListener(CivilModClientForge::onGameShuttingDown);
    }

    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.getTownCenter(), TownCenterMainScreen::new);
            registerModelPredicates();
        });
    }

    private static void registerModelPredicates() {
        ItemProperties.register(
                ModItems.getCivilDetector(),
                new ResourceLocation(CivilMod.MOD_ID, "display_state"),
                (stack, world, entity, seed) -> {
                    String display = ModComponents.getDisplay(stack);
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

    static void handleCharge(SonarChargePayload msg) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            SonarShockwaveEffect.startCharge(
                    msg.centerX(), msg.centerY(), msg.centerZ(),
                    msg.regionKindId(),
                    SonarType.fromId(msg.sonarType()));
        }
    }

    static void handleBoundary(SonarBoundaryPayload msg) {
        AuraWallRenderer.updateBoundaries(msg);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            Map<Long, float[]> shrineZoneYMap = buildShrineZoneYMap(
                    msg.shrineZone2D(), msg.shrineZoneMinY(), msg.shrineZoneMaxY());
            Map<Long, float[]> policyZoneYMap = buildShrineZoneYMap(
                    msg.zoneZone2D(), msg.zoneZoneMinY(), msg.zoneZoneMaxY());
            Set<Long> civHighZone2DSet = buildLongSet(msg.civHighZone2D());
            SonarShockwaveEffect.startRing(
                    shrineZoneYMap, policyZoneYMap, civHighZone2DSet,
                    SonarType.fromId(msg.sonarType()));
        }
    }

    static void handlePreTeleport(UndyingAnchorPreTeleportPayload msg) {
        UndyingAnchorCinematicEffect.startPreTeleport(
                msg.phase0Ticks(), msg.anchorX(), msg.anchorY(), msg.anchorZ());
    }

    static void handleAnchorParticles(UndyingAnchorParticlePayload msg) {
        UndyingAnchorParticleEffect.updateFromPayload(msg);
    }

    static void handleFarmShrineParticles(FarmShrineParticlePayload msg) {
        FarmShrineParticleEffect.updateFromPayload(msg);
    }

    static void handleTownCenterParticles(TownCenterParticlePayload msg) {
        TownCenterParticleEffect.updateFromPayload(msg);
    }

    static void handleTownCenterBurst(TownCenterActivationBurstPayload msg) {
        TownCenterActivationBurstEffect.start(msg.x(), msg.y(), msg.z());
    }

    static void handleTownCenterGuiSync(TownCenterGuiSyncPayload msg) {
        TownCenterClientState.apply(msg);
        if (Minecraft.getInstance().screen instanceof TownCenterScreenBase screen) {
            screen.onProfileSync();
        }
    }

    static void handleZoneTransition(ZoneTransitionPayload msg) {
        ZoneTransitionHud.onPayload(msg);
    }

    static void handleChunkBandTint(ChunkBandTintPayload msg) {
        ChunkBandClientCache.apply(msg);
    }

    static void handleChunkBandSyncPolicy(ChunkBandSyncPolicyPayload msg) {
        ChunkBandSubscriptionHub.onServerChunkBandSyncPolicy(msg.syncEnabled(), msg.cacheScope());
    }

    private static void onClientPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ZoneTransitionHud.resetForWorldSession();
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> ChunkBandClientSession.onPlayJoined(mc, mc.getConnection()));
    }

    private static void onClientPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ChunkBandClientSession.onPlayDisconnect(Minecraft.getInstance());
        ZoneTransitionHud.resetForWorldSession();
    }

    private static void onGameShuttingDown(GameShuttingDownEvent event) {
        ChunkBandClientSession.onPlayDisconnect(Minecraft.getInstance());
    }

    private static void onClientTickEnd(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ChunkBandClientSession.tick(Minecraft.getInstance());
            ZoneTransitionHud.tick();
        }
    }

    private static void onRenderGuiPost(RenderGuiEvent.Post event) {
        float pt = event.getPartialTick();
        if (UndyingAnchorCinematicEffect.isActive()) {
            UndyingAnchorCinematicEffect.renderOverlay(event.getGuiGraphics(), pt);
        }
        ZoneTransitionHud.render(event.getGuiGraphics(), pt);
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            UndyingAnchorCinematicEffect.tickAndApplyShake(null);
            UndyingAnchorParticleEffect.tick();
            FarmShrineParticleEffect.tick();
            TownCenterParticleEffect.tick();
            TownCenterActivationBurstEffect.tick();
            AuraWallRenderer.onRender(event.getPoseStack());
        }
    }

    private static Set<Long> buildLongSet(long[] array) {
        if (array.length == 0) return Set.of();
        Set<Long> set = new HashSet<>(array.length);
        for (long v : array) set.add(v);
        return set;
    }

    private static Map<Long, float[]> buildShrineZoneYMap(long[] keys, float[] minY, float[] maxY) {
        if (keys.length == 0) return Map.of();
        Map<Long, float[]> map = new HashMap<>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            map.put(keys[i], new float[]{minY[i], maxY[i]});
        }
        return map;
    }
}
