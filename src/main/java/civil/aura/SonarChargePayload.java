package civil.aura;

import civil.CivilMod;
import civil.network.CivilS2CPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client: sonar charge-up start; carries player {@link #regionKindId}
 * (see {@link civil.civilization.CivilRegionKind#id()}) and scan center.
 */
public record SonarChargePayload(
        double centerX,
        double centerY,
        double centerZ,
        byte regionKindId,
        byte sonarType
) implements CivilS2CPacket {

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "sonar_charge");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(centerX);
        buf.writeDouble(centerY);
        buf.writeDouble(centerZ);
        buf.writeByte(regionKindId);
        buf.writeByte(sonarType);
    }

    public static SonarChargePayload read(FriendlyByteBuf buf) {
        double cx = buf.readDouble();
        double cy = buf.readDouble();
        double cz = buf.readDouble();
        byte regionKindId = buf.readByte();
        byte sonarType = buf.readByte();
        return new SonarChargePayload(cx, cy, cz, regionKindId, sonarType);
    }
}
