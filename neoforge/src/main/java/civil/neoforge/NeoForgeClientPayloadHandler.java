package civil.neoforge;

import civil.aura.AuraWallRenderer;
import civil.aura.SonarBoundaryPayload;
import civil.aura.SonarChargePayload;
import civil.aura.SonarShockwaveEffect;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side payload handlers for NeoForge networking.
 * Methods in this class are only invoked on the client by NeoForge's networking layer.
 */
final class NeoForgeClientPayloadHandler {

    private NeoForgeClientPayloadHandler() {
    }

    static void handleSonarCharge(SonarChargePayload payload, IPayloadContext context) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            SonarShockwaveEffect.startCharge(
                    player.getX(), player.getY(), player.getZ(),
                    payload.playerInHigh(), payload.playerInHeadZone());
        }
    }

    static void handleSonarBoundary(SonarBoundaryPayload payload, IPayloadContext context) {
        AuraWallRenderer.updateBoundaries(payload);
        var player = Minecraft.getInstance().player;
        if (player != null) {
            Map<Long, float[]> headZoneYMap = buildHeadZoneYMap(
                    payload.headZone2D(), payload.headZoneMinY(), payload.headZoneMaxY());
            Set<Long> civHighZone2DSet = buildLongSet(payload.civHighZone2D());

            SonarShockwaveEffect.startRing(
                    payload.playerInHigh(), headZoneYMap, civHighZone2DSet);
        }
    }

    private static Set<Long> buildLongSet(long[] array) {
        if (array.length == 0) return Set.of();
        Set<Long> set = new HashSet<>(array.length);
        for (long v : array) set.add(v);
        return set;
    }

    private static Map<Long, float[]> buildHeadZoneYMap(long[] keys, float[] minY, float[] maxY) {
        if (keys.length == 0) return Map.of();
        Map<Long, float[]> map = new HashMap<>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            map.put(keys[i], new float[]{minY[i], maxY[i]});
        }
        return map;
    }
}
