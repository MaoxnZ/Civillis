package civil.forge;

import civil.CivilMod;
import civil.network.CivilC2SPacket;
import civil.network.CivilS2CPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Forge implementation of {@link civil.CivilPlatform}.
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

    public static void sendToPlayer(ServerPlayer player, CivilS2CPacket packet) {
        CivilModForge.sendToPlayer(player, packet);
    }

    public static boolean isPhysicalClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    public static void sendToServer(CivilC2SPacket packet) {
        if (!isPhysicalClient()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        CivilModForge.sendToServer(packet);
    }
}
