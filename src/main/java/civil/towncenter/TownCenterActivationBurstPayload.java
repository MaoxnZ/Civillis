package civil.towncenter;

import civil.CivilMod;
import civil.network.CivilS2CPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * One-shot client burst when a town center is first activated with an emerald.
 */
public record TownCenterActivationBurstPayload(int x, int y, int z) implements CivilS2CPacket {

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "town_center_activation_burst");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
    }

    public static TownCenterActivationBurstPayload read(FriendlyByteBuf buf) {
        return new TownCenterActivationBurstPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
