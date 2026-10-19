package civil.respawn;

import civil.CivilMod;
import civil.network.CivilS2CPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client packet that starts the pre-teleport phase (Phase 0).
 * Player is still at death location; teleport happens after phase0Ticks.
 * Anchor position is sent so client can play totem sound/particles at anchor (stable, no desync).
 */
public record UndyingAnchorPreTeleportPayload(int phase0Ticks, int anchorX, int anchorY, int anchorZ)
        implements CivilS2CPacket {

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "undying_anchor_pre_teleport");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(phase0Ticks);
        buf.writeVarInt(anchorX);
        buf.writeVarInt(anchorY);
        buf.writeVarInt(anchorZ);
    }

    public static UndyingAnchorPreTeleportPayload read(FriendlyByteBuf buf) {
        return new UndyingAnchorPreTeleportPayload(
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
