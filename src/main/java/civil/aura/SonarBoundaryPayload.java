package civil.aura;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client packet carrying protection zone boundary data.
 *
 * <p>Sent once when a sonar scan completes. Contains all vertical boundary
 * faces (X and Z axes only) discovered by the 2D BFS, the scan center,
 * wall vertical extent, and whether the player started inside a HIGH zone.
 *
 * <p>Also carries farm shrine bypass boundary faces with per-face vertical extents
 * for rendering amethyst-colored envelopes, and the 2D (XZ) footprint of all bypass
 * VCs for sonar particle effects.
 *
 * <p>The client uses this data to render translucent wall quads via {@link AuraWallRenderer}.
 */
public record SonarBoundaryPayload(
        boolean playerInHigh,
        double centerX,
        double centerY,
        double centerZ,
        double wallMinY,
        double wallMaxY,
        List<BoundaryFaceData> faces,
        List<ShrineFaceData> shrineFaces,
        long[] shrineZone2D,
        float[] shrineZoneMinY,
        float[] shrineZoneMaxY,
        long[] civHighZone2D,
        byte sonarType
) implements CustomPacketPayload {

    public static final Type<SonarBoundaryPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("civil", "sonar_boundary"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SonarBoundaryPayload> CODEC =
            StreamCodec.ofMember(SonarBoundaryPayload::encode, SonarBoundaryPayload::decode);

    private static void encode(SonarBoundaryPayload payload, RegistryFriendlyByteBuf buf) {
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
        buf.writeVarInt(payload.shrineFaces.size());
        for (ShrineFaceData hf : payload.shrineFaces) {
            hf.write(buf);
        }
        buf.writeVarInt(payload.shrineZone2D.length);
        for (int i = 0; i < payload.shrineZone2D.length; i++) {
            buf.writeLong(payload.shrineZone2D[i]);
            buf.writeFloat(payload.shrineZoneMinY[i]);
            buf.writeFloat(payload.shrineZoneMaxY[i]);
        }
        buf.writeVarInt(payload.civHighZone2D.length);
        for (long v : payload.civHighZone2D) {
            buf.writeLong(v);
        }
        buf.writeByte(payload.sonarType);
    }

    private static SonarBoundaryPayload decode(RegistryFriendlyByteBuf buf) {
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
        int shrineFaceCount = buf.readVarInt();
        List<ShrineFaceData> shrineFaces = new ArrayList<>(shrineFaceCount);
        for (int i = 0; i < shrineFaceCount; i++) {
            shrineFaces.add(ShrineFaceData.read(buf));
        }
        int szCount = buf.readVarInt();
        long[] shrineZone2D = new long[szCount];
        float[] shrineZoneMinY = new float[szCount];
        float[] shrineZoneMaxY = new float[szCount];
        for (int i = 0; i < szCount; i++) {
            shrineZone2D[i] = buf.readLong();
            shrineZoneMinY[i] = buf.readFloat();
            shrineZoneMaxY[i] = buf.readFloat();
        }
        int chCount = buf.readVarInt();
        long[] civHighZone2D = new long[chCount];
        for (int i = 0; i < chCount; i++) {
            civHighZone2D[i] = buf.readLong();
        }
        byte sonarType = buf.readByte();
        return new SonarBoundaryPayload(playerInHigh, cx, cy, cz, wMinY, wMaxY,
                faces, shrineFaces, shrineZone2D, shrineZoneMinY, shrineZoneMaxY, civHighZone2D, sonarType);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
