package civil.neoforge;

import civil.CivilMod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * NeoForge implementation of {@link civil.CivilPlatform}.
 * Resolved at runtime by the Architectury {@code @ExpectPlatform} transformer.
 */
public final class CivilPlatformImpl {

    private CivilPlatformImpl() {
    }

    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static String getReleasedModVersion() {
        return ModList.get()
                .getModContainerById(CivilMod.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
