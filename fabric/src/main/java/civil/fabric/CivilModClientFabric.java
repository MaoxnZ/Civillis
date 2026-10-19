package civil.fabric;

import civil.CivilMod;
import civil.ModItems;
import civil.ModMenuTypes;
import civil.civilization.ZoneTransitionHud;
import civil.civilization.ZoneTransitionPayload;
import civil.compat.bandsync.ChunkBandClientCache;
import civil.compat.bandsync.ChunkBandClientSession;
import civil.compat.bandsync.ChunkBandSubscriptionHub;
import civil.compat.xaeros.XaerosChunkBandHooks;
import civil.compat.bandsync.ChunkBandSyncPolicyPayload;
import civil.compat.bandsync.ChunkBandTintPayload;
import civil.component.ModComponents;
import civil.aura.AuraWallRenderer;
import civil.aura.SonarBoundaryPayload;
import civil.aura.SonarChargePayload;
import civil.respawn.UndyingAnchorCinematicEffect;
import civil.respawn.UndyingAnchorParticleEffect;
import civil.respawn.UndyingAnchorParticlePayload;
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
import civil.respawn.UndyingAnchorPreTeleportPayload;
import civil.aura.SonarShockwaveEffect;
import civil.aura.SonarType;
import civil.item.CivilDetectorClientParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fabric client entry point: registers client-side handlers for rendering and networking.
 */
@Environment(EnvType.CLIENT)
public class CivilModClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CivilDetectorClientParticles.register();
        XaerosChunkBandHooks.register();
        MenuScreens.register(ModMenuTypes.getTownCenter(), TownCenterMainScreen::new);
        registerModelPredicates();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ZoneTransitionHud.resetForWorldSession();
            client.execute(() -> ChunkBandClientSession.onPlayJoined(client, handler));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ChunkBandClientSession.onPlayDisconnect(client);
            ZoneTransitionHud.resetForWorldSession();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(ChunkBandClientSession::onPlayDisconnect);

        ClientPlayNetworking.registerGlobalReceiver(SonarChargePayload.ID,
                (client, handler, buf, responseSender) -> {
                    SonarChargePayload payload = SonarChargePayload.read(buf);
                    client.execute(() -> {
                        var player = Minecraft.getInstance().player;
                        if (player != null) {
                            SonarShockwaveEffect.startCharge(
                                    payload.centerX(), payload.centerY(), payload.centerZ(),
                                    payload.regionKindId(),
                                    SonarType.fromId(payload.sonarType()));
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(UndyingAnchorPreTeleportPayload.ID,
                (client, handler, buf, responseSender) -> {
                    UndyingAnchorPreTeleportPayload payload = UndyingAnchorPreTeleportPayload.read(buf);
                    client.execute(() -> UndyingAnchorCinematicEffect.startPreTeleport(payload.phase0Ticks(),
                            payload.anchorX(), payload.anchorY(), payload.anchorZ()));
                });

        ClientPlayNetworking.registerGlobalReceiver(UndyingAnchorParticlePayload.ID,
                (client, handler, buf, responseSender) -> {
                    UndyingAnchorParticlePayload payload = UndyingAnchorParticlePayload.read(buf);
                    client.execute(() -> UndyingAnchorParticleEffect.updateFromPayload(payload));
                });

        ClientPlayNetworking.registerGlobalReceiver(FarmShrineParticlePayload.ID,
                (client, handler, buf, responseSender) -> {
                    FarmShrineParticlePayload payload = FarmShrineParticlePayload.read(buf);
                    client.execute(() -> FarmShrineParticleEffect.updateFromPayload(payload));
                });

        ClientPlayNetworking.registerGlobalReceiver(TownCenterParticlePayload.ID,
                (client, handler, buf, responseSender) -> {
                    TownCenterParticlePayload payload = TownCenterParticlePayload.read(buf);
                    client.execute(() -> TownCenterParticleEffect.updateFromPayload(payload));
                });

        ClientPlayNetworking.registerGlobalReceiver(TownCenterActivationBurstPayload.ID,
                (client, handler, buf, responseSender) -> {
                    TownCenterActivationBurstPayload payload = TownCenterActivationBurstPayload.read(buf);
                    client.execute(() -> TownCenterActivationBurstEffect.start(
                            payload.x(), payload.y(), payload.z()));
                });

        ClientPlayNetworking.registerGlobalReceiver(TownCenterGuiSyncPayload.ID,
                (client, handler, buf, responseSender) -> {
                    TownCenterGuiSyncPayload payload = TownCenterGuiSyncPayload.read(buf);
                    client.execute(() -> {
                        TownCenterClientState.apply(payload);
                        if (Minecraft.getInstance().screen instanceof TownCenterScreenBase screen) {
                            screen.onProfileSync();
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(SonarBoundaryPayload.ID,
                (client, handler, buf, responseSender) -> {
                    SonarBoundaryPayload payload = SonarBoundaryPayload.read(buf);
                    client.execute(() -> {
                        AuraWallRenderer.updateBoundaries(payload);
                        var player = Minecraft.getInstance().player;
                        if (player != null) {
                            Map<Long, float[]> shrineZoneYMap = buildShrineZoneYMap(
                                    payload.shrineZone2D(), payload.shrineZoneMinY(), payload.shrineZoneMaxY());
                            Map<Long, float[]> policyZoneYMap = buildShrineZoneYMap(
                                    payload.zoneZone2D(), payload.zoneZoneMinY(), payload.zoneZoneMaxY());
                            Set<Long> civHighZone2DSet = buildLongSet(payload.civHighZone2D());
                            SonarShockwaveEffect.startRing(
                                    shrineZoneYMap, policyZoneYMap, civHighZone2DSet,
                                    SonarType.fromId(payload.sonarType()));
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(ZoneTransitionPayload.ID,
                (client, handler, buf, responseSender) -> {
                    ZoneTransitionPayload payload = ZoneTransitionPayload.read(buf);
                    client.execute(() -> ZoneTransitionHud.onPayload(payload));
                });

        ClientPlayNetworking.registerGlobalReceiver(ChunkBandTintPayload.ID,
                (client, handler, buf, responseSender) -> {
                    ChunkBandTintPayload payload = ChunkBandTintPayload.read(buf);
                    client.execute(() -> ChunkBandClientCache.apply(payload));
                });

        ClientPlayNetworking.registerGlobalReceiver(ChunkBandSyncPolicyPayload.ID,
                (client, handler, buf, responseSender) -> {
                    ChunkBandSyncPolicyPayload payload = ChunkBandSyncPolicyPayload.read(buf);
                    client.execute(
                            () -> ChunkBandSubscriptionHub.onServerChunkBandSyncPolicy(payload.syncEnabled(), payload.cacheScope()));
                });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ChunkBandClientSession.tick(client);
            ZoneTransitionHud.tick();
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            UndyingAnchorCinematicEffect.tickAndApplyShake(null);
            UndyingAnchorParticleEffect.tick();
            FarmShrineParticleEffect.tick();
            TownCenterParticleEffect.tick();
            TownCenterActivationBurstEffect.tick();
            AuraWallRenderer.onRender(context.matrixStack());
        });

        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            float pt = tickDelta;
            if (UndyingAnchorCinematicEffect.isActive()) {
                UndyingAnchorCinematicEffect.renderOverlay(guiGraphics, pt);
            }
            ZoneTransitionHud.render(guiGraphics, pt);
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
