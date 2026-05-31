package civil.towncenter.network;

import civil.registry.TownCenterLevelRegistry.PaymentTier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Client-to-server town center menu actions (containerId authoritative). */
public record TownCenterC2SPayload(int containerId, Action action, int intArg, boolean boolArg,
                                   String stringArg, UUID uuidArg, PaymentTier paymentTier) implements CustomPacketPayload {

    public enum Action {
        CONFIRM_UPGRADE,
        SET_NAME,
        SET_OPEN_REGISTRATION,
        REGISTER_MEMBER,
        UNREGISTER_MEMBER,
        KICK_MEMBER,
        SET_PAGE
    }

    public static final Type<TownCenterC2SPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("civil", "town_center_c2s"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TownCenterC2SPayload> CODEC =
            StreamCodec.ofMember(TownCenterC2SPayload::encode, TownCenterC2SPayload::decode);

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

    private static void encode(TownCenterC2SPayload p, RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(p.containerId);
        buf.writeByte(p.action.ordinal());
        buf.writeVarInt(p.intArg);
        buf.writeBoolean(p.boolArg);
        buf.writeUtf(p.stringArg == null ? "" : p.stringArg, 64);
        buf.writeBoolean(p.uuidArg != null);
        if (p.uuidArg != null) buf.writeUUID(p.uuidArg);
        buf.writeByte(p.paymentTier.ordinal());
    }

    private static TownCenterC2SPayload decode(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        Action action = Action.values()[buf.readByte() & 0xFF];
        int intArg = buf.readVarInt();
        boolean boolArg = buf.readBoolean();
        String stringArg = buf.readUtf(64);
        UUID uuidArg = buf.readBoolean() ? buf.readUUID() : null;
        PaymentTier tier = PaymentTier.values()[buf.readByte() & 0xFF];
        return new TownCenterC2SPayload(containerId, action, intArg, boolArg, stringArg, uuidArg, tier);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
