package civil;

import civil.aura.AuraWallRenderer;
import civil.aura.SonarBoundaryPayload;
import civil.aura.SonarChargePayload;
import civil.aura.SonarShockwaveEffect;
import civil.item.CivilDetectorClientParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client entry point: registers client-side handlers for rendering and networking.
 */
@Environment(EnvType.CLIENT)
public class CivilModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CivilDetectorClientParticles.register();

        // Phase 1: Charge-up particles — syncs with the charge-up sound
        ClientPlayNetworking.registerGlobalReceiver(SonarChargePayload.ID,
                (payload, context) -> {
                    var player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        SonarShockwaveEffect.startCharge(
                                player.getX(), player.getY(), player.getZ(),
                                payload.playerInHigh(), payload.playerInHeadZone());
                    }
                });

        // Phase 2: Ring expansion — syncs with the boom sound (arrives later)
        ClientPlayNetworking.registerGlobalReceiver(SonarBoundaryPayload.ID,
                (payload, context) -> {
                    AuraWallRenderer.updateBoundaries(payload);
                    var player = MinecraftClient.getInstance().player;
                    if (player != null) {
                        Map<Long, float[]> headZoneYMap = buildHeadZoneYMap(
                                payload.headZone2D(), payload.headZoneMinY(), payload.headZoneMaxY());
                        Set<Long> civHighZone2DSet = buildLongSet(payload.civHighZone2D());

                        SonarShockwaveEffect.startRing(
                                payload.playerInHigh(), headZoneYMap, civHighZone2DSet);
                    }
                });
        AuraWallRenderer.register();
    }

    /** Build a {@code Set<Long>} from a packed long array. Returns {@code Set.of()} if empty. */
    private static Set<Long> buildLongSet(long[] array) {
        if (array.length == 0) return Set.of();
        Set<Long> set = new HashSet<>(array.length);
        for (long v : array) set.add(v);
        return set;
    }

    /** Build head zone Y-range map from parallel arrays. Returns {@code Map.of()} if empty. */
    private static Map<Long, float[]> buildHeadZoneYMap(long[] keys, float[] minY, float[] maxY) {
        if (keys.length == 0) return Map.of();
        Map<Long, float[]> map = new HashMap<>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            map.put(keys[i], new float[]{minY[i], maxY[i]});
        }
        return map;
    }
}
