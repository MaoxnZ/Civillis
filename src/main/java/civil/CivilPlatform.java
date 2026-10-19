package civil;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * Platform abstraction for loader-specific operations.
 * Methods are resolved at runtime by the Architectury transformer:
 * fabric  → {@code civil.fabric.CivilPlatformImpl}
 * neoforge → {@code civil.neoforge.CivilPlatformImpl}
 */
public final class CivilPlatform {

    private CivilPlatform() {
    }

    @ExpectPlatform
    public static Path getConfigDir() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isModLoaded(String modId) {
        throw new AssertionError();
    }

    /**
     * Released mod version string from loader metadata (same as {@code mod_version} / Modrinth jar), e.g.
     * {@code 1.3.3-release}. Used for simple-settings schema migration in {@link civil.config.CivilConfig}.
     */
    @ExpectPlatform
    public static String getReleasedModVersion() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        throw new AssertionError();
    }
}
