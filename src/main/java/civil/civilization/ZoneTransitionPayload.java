package civil.civilization;

import civil.CivilMod;
import civil.network.CivilS2CPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client: zone semantic transition for HUD (epoch ordering + state id),
 * optionally with a server-chosen label such as a town center name.
 */
public record ZoneTransitionPayload(long epoch, int stateId, String labelOverride) implements CivilS2CPacket {

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "zone_transition");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeLong(epoch);
        buf.writeByte(stateId);
        buf.writeUtf(truncateLabel(labelOverride), 64);
    }

    public static ZoneTransitionPayload read(FriendlyByteBuf buf) {
        return new ZoneTransitionPayload(buf.readLong(), buf.readByte() & 0xFF, buf.readUtf(64));
    }

    public ZoneSemanticState state() {
        return ZoneSemanticState.fromId(stateId);
    }

    private static String truncateLabel(String label) {
        if (label == null || label.isEmpty()) {
            return "";
        }
        return label.length() <= 64 ? label : label.substring(0, 64);
    }
}
