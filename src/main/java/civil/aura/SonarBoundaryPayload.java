package civil.aura;

import civil.CivilMod;
import civil.network.CivilS2CPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client: sonar scan boundary; civilization faces, farm shrine faces, and structure-policy zone faces.
 */
public record SonarBoundaryPayload(
        byte playerRegionKindId,
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
        List<ShrineFaceData> zoneFaces,
        long[] zoneZone2D,
        float[] zoneZoneMinY,
        float[] zoneZoneMaxY,
        long[] civHighZone2D,
        byte sonarType
) implements CivilS2CPacket {

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "sonar_boundary");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeByte(playerRegionKindId);
        buf.writeDouble(centerX);
        buf.writeDouble(centerY);
        buf.writeDouble(centerZ);
        buf.writeDouble(wallMinY);
        buf.writeDouble(wallMaxY);
        buf.writeVarInt(faces.size());
        for (BoundaryFaceData face : faces) {
            face.write(buf);
        }
        buf.writeVarInt(shrineFaces.size());
        for (ShrineFaceData hf : shrineFaces) {
            hf.write(buf);
        }
        buf.writeVarInt(shrineZone2D.length);
        for (int i = 0; i < shrineZone2D.length; i++) {
            buf.writeLong(shrineZone2D[i]);
            buf.writeFloat(shrineZoneMinY[i]);
            buf.writeFloat(shrineZoneMaxY[i]);
        }
        buf.writeVarInt(zoneFaces.size());
        for (ShrineFaceData zf : zoneFaces) {
            zf.write(buf);
        }
        buf.writeVarInt(zoneZone2D.length);
        for (int i = 0; i < zoneZone2D.length; i++) {
            buf.writeLong(zoneZone2D[i]);
            buf.writeFloat(zoneZoneMinY[i]);
            buf.writeFloat(zoneZoneMaxY[i]);
        }
        buf.writeVarInt(civHighZone2D.length);
        for (long v : civHighZone2D) {
            buf.writeLong(v);
        }
        buf.writeByte(sonarType);
    }

    public static SonarBoundaryPayload read(FriendlyByteBuf buf) {
        byte playerRegionKindId = buf.readByte();
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
        List<ShrineFaceData> shrineFacesList = new ArrayList<>(shrineFaceCount);
        for (int i = 0; i < shrineFaceCount; i++) {
            shrineFacesList.add(ShrineFaceData.read(buf));
        }
        int szCount = buf.readVarInt();
        long[] shrineZone2DArray = new long[szCount];
        float[] shrineZoneMinYArray = new float[szCount];
        float[] shrineZoneMaxYArray = new float[szCount];
        for (int i = 0; i < szCount; i++) {
            shrineZone2DArray[i] = buf.readLong();
            shrineZoneMinYArray[i] = buf.readFloat();
            shrineZoneMaxYArray[i] = buf.readFloat();
        }
        int zfCount = buf.readVarInt();
        List<ShrineFaceData> zoneFacesList = new ArrayList<>(zfCount);
        for (int i = 0; i < zfCount; i++) {
            zoneFacesList.add(ShrineFaceData.read(buf));
        }
        int zzCount = buf.readVarInt();
        long[] zoneZone2DArray = new long[zzCount];
        float[] zoneZoneMinYArray = new float[zzCount];
        float[] zoneZoneMaxYArray = new float[zzCount];
        for (int i = 0; i < zzCount; i++) {
            zoneZone2DArray[i] = buf.readLong();
            zoneZoneMinYArray[i] = buf.readFloat();
            zoneZoneMaxYArray[i] = buf.readFloat();
        }
        int chCount = buf.readVarInt();
        long[] civHighZone2DArray = new long[chCount];
        for (int i = 0; i < chCount; i++) {
            civHighZone2DArray[i] = buf.readLong();
        }
        byte sonarType = buf.readByte();
        return new SonarBoundaryPayload(playerRegionKindId, cx, cy, cz, wMinY, wMaxY,
                faces, shrineFacesList, shrineZone2DArray, shrineZoneMinYArray, shrineZoneMaxYArray,
                zoneFacesList, zoneZone2DArray, zoneZoneMinYArray, zoneZoneMaxYArray,
                civHighZone2DArray, sonarType);
    }
}
