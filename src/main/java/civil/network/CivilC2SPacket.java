package civil.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server packet: channel id + binary body on {@link FriendlyByteBuf}.
 */
public interface CivilC2SPacket {

    ResourceLocation id();

    void write(FriendlyByteBuf buf);
}
