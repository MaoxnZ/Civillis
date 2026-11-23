package civil.towncenter.network;

import civil.registry.TownCenterLevelRegistry.PaymentTier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-to-client town center profile sync (strings + member list + offer previews). */
public record TownCenterGuiSyncPayload(
        String creatorName,
        String displayName,
        List<MemberEntry> members,
        int pendingTargetLevel,
        List<OfferPreview> offerPreviews,
        List<AppliedBuffPreview> appliedBuffs) implements CustomPacketPayload {

    public record MemberEntry(UUID uuid, String name) {}

    public record OfferPreview(
            PaymentTier paymentTier,
            String translationKey,
            String effectId,
            int amplifier) {}

    public record AppliedBuffPreview(String effectId, int amplifier, String translationKey) {}

    public static final Type<TownCenterGuiSyncPayload> ID =
            new Type<>(Identifier.fromNamespaceAndPath("civil", "town_center_gui_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TownCenterGuiSyncPayload> CODEC =
            StreamCodec.ofMember(TownCenterGuiSyncPayload::encode, TownCenterGuiSyncPayload::decode);

    private static void encode(TownCenterGuiSyncPayload p, RegistryFriendlyByteBuf buf) {
        buf.writeUtf(p.creatorName == null ? "" : p.creatorName, 64);
        buf.writeUtf(p.displayName == null ? "" : p.displayName, 64);
        buf.writeVarInt(p.members.size());
        for (MemberEntry m : p.members) {
            buf.writeUUID(m.uuid);
            buf.writeUtf(m.name == null ? "" : m.name, 64);
        }
        buf.writeVarInt(p.pendingTargetLevel);
        buf.writeVarInt(p.offerPreviews.size());
        for (OfferPreview o : p.offerPreviews) {
            buf.writeByte(o.paymentTier().ordinal());
            buf.writeUtf(o.translationKey == null ? "" : o.translationKey, 128);
            buf.writeUtf(o.effectId == null ? "" : o.effectId, 64);
            buf.writeVarInt(o.amplifier());
        }
        buf.writeVarInt(p.appliedBuffs.size());
        for (AppliedBuffPreview b : p.appliedBuffs) {
            buf.writeUtf(b.effectId == null ? "" : b.effectId, 64);
            buf.writeVarInt(b.amplifier());
            buf.writeUtf(b.translationKey == null ? "" : b.translationKey, 128);
        }
    }

    private static TownCenterGuiSyncPayload decode(RegistryFriendlyByteBuf buf) {
        String creator = buf.readUtf(64);
        String display = buf.readUtf(64);
        int n = buf.readVarInt();
        List<MemberEntry> members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            members.add(new MemberEntry(buf.readUUID(), buf.readUtf(64)));
        }
        int pending = buf.readVarInt();
        int offerCount = buf.readVarInt();
        List<OfferPreview> offers = new ArrayList<>(offerCount);
        for (int i = 0; i < offerCount; i++) {
            PaymentTier tier = PaymentTier.values()[buf.readByte() & 0xFF];
            String key = buf.readUtf(128);
            String effectId = buf.readUtf(64);
            int amp = buf.readVarInt();
            offers.add(new OfferPreview(tier, key, effectId, amp));
        }
        int buffCount = buf.readVarInt();
        List<AppliedBuffPreview> applied = new ArrayList<>(buffCount);
        for (int i = 0; i < buffCount; i++) {
            String effectId = buf.readUtf(64);
            int amp = buf.readVarInt();
            String key = buf.readUtf(128);
            applied.add(new AppliedBuffPreview(effectId, amp, key));
        }
        return new TownCenterGuiSyncPayload(creator, display, members, pending, offers, applied);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
