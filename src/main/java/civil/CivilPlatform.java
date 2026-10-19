package civil;

import civil.network.CivilC2SPacket;
import civil.network.CivilS2CPacket;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

/**
 * Platform abstraction for loader-specific operations.
 * Methods are resolved at runtime by the Architectury transformer:
 * fabric → {@code civil.fabric.CivilPlatformImpl}
 * forge  → {@code civil.forge.CivilPlatformImpl}
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
    public static void sendToPlayer(ServerPlayer player, CivilS2CPacket packet) {
        throw new AssertionError();
    }

    /** {@code true} when this JVM is a client distribution (including integrated server host). */
    @ExpectPlatform
    public static boolean isPhysicalClient() {
        throw new AssertionError();
    }

    /** Sends a C2S packet from the logical client; no-op on dedicated server or when not in-game. */
    @ExpectPlatform
    public static void sendToServer(CivilC2SPacket packet) {
        throw new AssertionError();
    }
}
