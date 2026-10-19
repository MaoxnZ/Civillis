package civil.fabric;

import civil.CivilMod;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * Fabric implementation of {@link civil.CivilPlatform}.
 * Resolved at runtime by the Architectury {@code @ExpectPlatform} transformer.
 */
public final class CivilPlatformImpl {

    private CivilPlatformImpl() {
    }

    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static String getReleasedModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(CivilMod.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
