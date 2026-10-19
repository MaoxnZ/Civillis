package civil.compat.bandsync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.storage.LevelResource;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Stable logical client session for bandsync L2 paths: integrated save root vs multiplayer endpoint + player id +
 * server-reported {@code mpBandCacheScope} (world-root derived on the server) so proxy front-ends do not collapse
 * unrelated backends into one client cache directory.
 */
public final class ChunkBandSessionKey {

    private final boolean integrated;
    /** Absolute normalized world root for integrated server; meaningless when {@link #integrated} is false. */
    private final Path integratedWorldRoot;
    /** Full SHA-256 hex (64 chars) of canonical string; used for snapshot header and MP directory name prefix. */
    private final String fingerprintHex;
    /** Filesystem-safe directory segment derived from {@link #fingerprintHex}. */
    private final String dirSegment;

    private ChunkBandSessionKey(boolean integrated, Path integratedWorldRoot, String fingerprintHex, String dirSegment) {
        this.integrated = integrated;
        this.integratedWorldRoot = integratedWorldRoot;
        this.fingerprintHex = fingerprintHex;
        this.dirSegment = dirSegment;
    }

    public boolean integrated() {
        return integrated;
    }

    public Path integratedWorldRoot() {
        return integratedWorldRoot;
    }

    /** 64 lowercase hex chars; stored in snapshot headers. */
    public String fingerprintHex() {
        return fingerprintHex;
    }

    /** Subdirectory name under {@code civil/chunk_band_cache/} for multiplayer clients. */
    public String dirSegment() {
        return dirSegment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChunkBandSessionKey that)) {
            return false;
        }
        return integrated == that.integrated
                && Objects.equals(fingerprintHex, that.fingerprintHex)
                && Objects.equals(integratedWorldRoot, that.integratedWorldRoot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(integrated, fingerprintHex, integratedWorldRoot);
    }

    /**
     * @param mpBandCacheScope from {@link ChunkBandSubscriptionHub#serverBandCacheScope()}; must be non-blank for
     *                           multiplayer or this method returns {@code null} (client waits for S2C policy).
     */
    public static ChunkBandSessionKey resolve(Minecraft mc, ClientPacketListener listener, String mpBandCacheScope) {
        Objects.requireNonNull(mc, "mc");
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            String canonical = "SP:" + root.toString().toLowerCase(Locale.ROOT);
            return fromCanonical(canonical, true, root);
        }
        if (listener == null || mc.player == null) {
            return null;
        }
        var connection = listener.getConnection();
        if (connection == null) {
            return null;
        }
        if (mpBandCacheScope == null || mpBandCacheScope.isEmpty()) {
            return null;
        }
        String scope = mpBandCacheScope.strip();
        if (scope.isEmpty()) {
            return null;
        }
        String endpoint = formatRemoteEndpoint(connection.getRemoteAddress());
        String canonical = "MP:" + endpoint + "#" + mc.player.getUUID() + "#" + scope;
        return fromCanonical(canonical, false, Path.of(""));
    }

    private static ChunkBandSessionKey fromCanonical(String canonical, boolean integrated, Path integratedWorldRoot) {
        String fp = sha256Hex(canonical);
        String dir = fp.substring(0, Math.min(24, fp.length()));
        return new ChunkBandSessionKey(integrated, integratedWorldRoot, fp, dir);
    }

    private static String formatRemoteEndpoint(SocketAddress addr) {
        if (addr instanceof InetSocketAddress isa) {
            String host = isa.getHostString();
            if (host != null && !host.isEmpty()) {
                return host.toLowerCase(Locale.ROOT) + ":" + isa.getPort();
            }
            if (isa.getAddress() != null) {
                return isa.getAddress().getHostAddress().toLowerCase(Locale.ROOT) + ":" + isa.getPort();
            }
        }
        return addr == null ? "unknown:0" : addr.toString();
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
