package civil.aura;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client packet carrying protection zone boundary data.
 *
 * <p>Sent once when a sonar scan completes. Contains all vertical boundary
 * faces (X and Z axes only) discovered by the 2D BFS, the scan center,
 * wall vertical extent, and whether the player started inside a HIGH zone.
 *
 * <p>Also carries head zone ("Force Allow") boundary faces with per-face
 * vertical extents for rendering amethyst-colored mob head territory envelopes,
 * and the 2D (XZ) footprint of all force-allow VCs for sonar particle effects.
 *
 * <p>The client uses this data to render translucent wall quads via
 * {@link AuraWallRenderer}.
 */
public record SonarBoundaryPayload(
        boolean playerInHigh,
        double centerX,
        double centerY,
        double centerZ,
        double wallMinY,
        double wallMaxY,
        List<BoundaryFaceData> faces,
        List<HeadZoneFaceData> headFaces,
        long[] headZone2D,
        long[] civHighZone2D
) implements CustomPayload {

    public static final Id<SonarBoundaryPayload> ID =
            new Id<>(Identifier.of("civil", "sonar_boundary"));

    public static final PacketCodec<RegistryByteBuf, SonarBoundaryPayload> CODEC =
            PacketCodec.of(SonarBoundaryPayload::encode, SonarBoundaryPayload::decode);

    /** ValueFirstEncoder: (value, buf) order — required by PacketCodec.of in 1.21.11. */
    private static void encode(SonarBoundaryPayload payload, RegistryByteBuf buf) {
        buf.writeBoolean(payload.playerInHigh);
        buf.writeDouble(payload.centerX);
        buf.writeDouble(payload.centerY);
        buf.writeDouble(payload.centerZ);
        buf.writeDouble(payload.wallMinY);
        buf.writeDouble(payload.wallMaxY);
        buf.writeVarInt(payload.faces.size());
        for (BoundaryFaceData face : payload.faces) {
            face.write(buf);
        }
        buf.writeVarInt(payload.headFaces.size());
        for (HeadZoneFaceData hf : payload.headFaces) {
            hf.write(buf);
        }
        buf.writeVarInt(payload.headZone2D.length);
        for (long v : payload.headZone2D) {
            buf.writeLong(v);
        }
        buf.writeVarInt(payload.civHighZone2D.length);
        for (long v : payload.civHighZone2D) {
            buf.writeLong(v);
        }
    }

    private static SonarBoundaryPayload decode(RegistryByteBuf buf) {
        boolean playerInHigh = buf.readBoolean();
        double cx = buf.readDouble();
        double cy = buf.readDouble();
        double cz = buf.readDouble();
        double wMinY = buf.readDouble();
        double wMaxY = buf.readDouble();
        int count = buf.readVarInt();
        List<BoundaryFaceData> faces = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            faces.add(BoundaryFaceData.read(buf));
        }
        int headCount = buf.readVarInt();
        List<HeadZoneFaceData> headFaces = new ArrayList<>(headCount);
        for (int i = 0; i < headCount; i++) {
            headFaces.add(HeadZoneFaceData.read(buf));
        }
        int hzCount = buf.readVarInt();
        long[] headZone2D = new long[hzCount];
        for (int i = 0; i < hzCount; i++) {
            headZone2D[i] = buf.readLong();
        }
        int chCount = buf.readVarInt();
        long[] civHighZone2D = new long[chCount];
        for (int i = 0; i < chCount; i++) {
            civHighZone2D[i] = buf.readLong();
        }
        return new SonarBoundaryPayload(playerInHigh, cx, cy, cz, wMinY, wMaxY,
                faces, headFaces, headZone2D, civHighZone2D);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
