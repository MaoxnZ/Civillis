package civil.fabric;

import civil.CivilMod;
import civil.civilization.ZoneTransitionHud;
import civil.civilization.ZoneTransitionPayload;
import civil.aura.AuraWallRenderer;
import civil.aura.SonarBoundaryPayload;
import civil.aura.SonarChargePayload;
import civil.respawn.UndyingAnchorCinematicEffect;
import civil.respawn.UndyingAnchorParticleEffect;
import civil.respawn.UndyingAnchorParticlePayload;
import civil.shrine.FarmShrineParticleEffect;
import civil.shrine.FarmShrineParticlePayload;
import civil.respawn.UndyingAnchorPreTeleportPayload;
import civil.aura.SonarShockwaveEffect;
import civil.aura.SonarType;
import civil.item.CivilDetectorClientParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

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

        // Zone HUD: same-JVM world switch — reset epoch gate + overlay state (common + integrated server).
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ZoneTransitionHud.resetForWorldSession());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ZoneTransitionHud.resetForWorldSession());

        ClientPlayNetworking.registerGlobalReceiver(SonarChargePayload.ID,
                (payload, context) -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        SonarShockwaveEffect.startCharge(
                                payload.centerX(), payload.centerY(), payload.centerZ(),
                                payload.playerInHigh(), payload.playerInShrineZone(),
                                SonarType.fromId(payload.sonarType()));
                    }
                });

        ClientPlayNetworking.registerGlobalReceiver(UndyingAnchorPreTeleportPayload.ID,
                (payload, context) -> UndyingAnchorCinematicEffect.startPreTeleport(payload.phase0Ticks(),
                        payload.anchorX(), payload.anchorY(), payload.anchorZ()));

        ClientPlayNetworking.registerGlobalReceiver(UndyingAnchorParticlePayload.ID,
                (payload, context) -> UndyingAnchorParticleEffect.updateFromPayload(payload));

        ClientPlayNetworking.registerGlobalReceiver(FarmShrineParticlePayload.ID,
                (payload, context) -> FarmShrineParticleEffect.updateFromPayload(payload));

        ClientPlayNetworking.registerGlobalReceiver(SonarBoundaryPayload.ID,
                (payload, context) -> {
                    AuraWallRenderer.updateBoundaries(payload);
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        Map<Long, float[]> shrineZoneYMap = buildShrineZoneYMap(
                                payload.shrineZone2D(), payload.shrineZoneMinY(), payload.shrineZoneMaxY());
                        Set<Long> civHighZone2DSet = buildLongSet(payload.civHighZone2D());

                        SonarShockwaveEffect.startRing(
                                payload.playerInHigh(), shrineZoneYMap, civHighZone2DSet,
                                SonarType.fromId(payload.sonarType()));
                    }
                });

        ClientPlayNetworking.registerGlobalReceiver(ZoneTransitionPayload.ID,
                (payload, context) -> ZoneTransitionHud.onPayload(payload));

        ClientTickEvents.END_CLIENT_TICK.register(client -> ZoneTransitionHud.tick());

        // v1.world API has no AFTER_TRANSLUCENT; END_MAIN runs after translucent terrain (Fabric javadoc).
        WorldRenderEvents.END_MAIN.register(context -> {
            UndyingAnchorCinematicEffect.tickAndApplyShake(null);
            UndyingAnchorParticleEffect.tick();
            FarmShrineParticleEffect.tick();
            // MC 1.21.11+: Camera no longer exposes getPosition(); match NeoForge (eye + partial tick).
            Minecraft mc = Minecraft.getInstance();
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            Entity entity = mc.gameRenderer.getMainCamera().entity();
            if (entity != null) {
                AuraWallRenderer.onRender(entity.getEyePosition(pt));
            }
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(CivilMod.MOD_ID, "undying_anchor_overlay"),
                (guiGraphics, tickCounter) -> UndyingAnchorCinematicEffect.renderOverlay(
                        guiGraphics, tickCounter.getGameTimeDeltaPartialTick(true)));

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath(CivilMod.MOD_ID, "zone_transition_overlay"),
                (guiGraphics, tickCounter) -> ZoneTransitionHud.render(
                        guiGraphics, tickCounter.getGameTimeDeltaPartialTick(true)));
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
