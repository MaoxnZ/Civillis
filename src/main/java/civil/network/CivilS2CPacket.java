package civil.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Common S2C packet contract for 1.20.x: channel id + binary body on {@link FriendlyByteBuf}.
 * Fabric sends one channel per id; Forge registers decoders on {@link net.minecraftforge.network.simple.SimpleChannel}.
 */
public interface CivilS2CPacket {

    ResourceLocation id();

    void write(FriendlyByteBuf buf);
}
