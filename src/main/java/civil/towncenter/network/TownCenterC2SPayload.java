package civil.towncenter.network;

import civil.CivilMod;
import civil.network.CivilC2SPacket;
import civil.registry.TownCenterLevelRegistry.PaymentTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Client-to-server town center menu actions (containerId authoritative). */
public record TownCenterC2SPayload(int containerId, Action action, int intArg, boolean boolArg,
                                   String stringArg, UUID uuidArg, PaymentTier paymentTier) implements CivilC2SPacket {

    public enum Action {
        CONFIRM_UPGRADE,
        SET_NAME,
        SET_OPEN_REGISTRATION,
        REGISTER_MEMBER,
        UNREGISTER_MEMBER,
        KICK_MEMBER,
        SET_PAGE
    }

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "town_center_c2s");

    public static TownCenterC2SPayload confirmUpgrade(int containerId, int targetLevel, PaymentTier tier) {
        return new TownCenterC2SPayload(containerId, Action.CONFIRM_UPGRADE, targetLevel, false, "", null, tier);
    }

    public static TownCenterC2SPayload setName(int containerId, String name) {
        return new TownCenterC2SPayload(containerId, Action.SET_NAME, 0, false, name, null, PaymentTier.NONE);
    }

    public static TownCenterC2SPayload setOpenRegistration(int containerId, boolean open) {
        return new TownCenterC2SPayload(containerId, Action.SET_OPEN_REGISTRATION, 0, open, "", null, PaymentTier.NONE);
    }

    public static TownCenterC2SPayload registerMember(int containerId) {
        return new TownCenterC2SPayload(containerId, Action.REGISTER_MEMBER, 0, false, "", null, PaymentTier.NONE);
    }

    public static TownCenterC2SPayload unregisterMember(int containerId) {
        return new TownCenterC2SPayload(containerId, Action.UNREGISTER_MEMBER, 0, false, "", null, PaymentTier.NONE);
    }

    public static TownCenterC2SPayload kickMember(int containerId, UUID target) {
        return new TownCenterC2SPayload(containerId, Action.KICK_MEMBER, 0, false, "", target, PaymentTier.NONE);
    }

    public static TownCenterC2SPayload setPage(int containerId, int pageOrdinal) {
        return new TownCenterC2SPayload(containerId, Action.SET_PAGE, pageOrdinal, false, "", null, PaymentTier.NONE);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeByte(action.ordinal());
        buf.writeVarInt(intArg);
        buf.writeBoolean(boolArg);
        buf.writeUtf(stringArg == null ? "" : stringArg, 64);
        buf.writeBoolean(uuidArg != null);
        if (uuidArg != null) {
            buf.writeUUID(uuidArg);
        }
        buf.writeByte(paymentTier.ordinal());
    }

    public static TownCenterC2SPayload read(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        Action action = Action.values()[buf.readByte() & 0xFF];
        int intArg = buf.readVarInt();
        boolean boolArg = buf.readBoolean();
        String stringArg = buf.readUtf(64);
        UUID uuidArg = buf.readBoolean() ? buf.readUUID() : null;
        PaymentTier tier = PaymentTier.values()[buf.readByte() & 0xFF];
        return new TownCenterC2SPayload(containerId, action, intArg, boolArg, stringArg, uuidArg, tier);
    }
}
