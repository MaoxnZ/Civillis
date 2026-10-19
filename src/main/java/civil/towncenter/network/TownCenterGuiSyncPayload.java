package civil.towncenter.network;

import civil.CivilMod;
import civil.network.CivilS2CPacket;
import civil.registry.TownCenterLevelRegistry.PaymentTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

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
        List<AppliedBuffPreview> appliedBuffs) implements CivilS2CPacket {

    public record MemberEntry(UUID uuid, String name) {}

    public record OfferPreview(
            PaymentTier paymentTier,
            String translationKey,
            String effectId,
            int amplifier) {}

    public record AppliedBuffPreview(String effectId, int amplifier, String translationKey) {}

    public static final ResourceLocation ID = new ResourceLocation(CivilMod.MOD_ID, "town_center_gui_sync");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(creatorName == null ? "" : creatorName, 64);
        buf.writeUtf(displayName == null ? "" : displayName, 64);
        buf.writeVarInt(members.size());
        for (MemberEntry m : members) {
            buf.writeUUID(m.uuid());
            buf.writeUtf(m.name() == null ? "" : m.name(), 64);
        }
        buf.writeVarInt(pendingTargetLevel);
        buf.writeVarInt(offerPreviews.size());
        for (OfferPreview o : offerPreviews) {
            buf.writeByte(o.paymentTier().ordinal());
            buf.writeUtf(o.translationKey() == null ? "" : o.translationKey(), 128);
            buf.writeUtf(o.effectId() == null ? "" : o.effectId(), 64);
            buf.writeVarInt(o.amplifier());
        }
        buf.writeVarInt(appliedBuffs.size());
        for (AppliedBuffPreview b : appliedBuffs) {
            buf.writeUtf(b.effectId() == null ? "" : b.effectId(), 64);
            buf.writeVarInt(b.amplifier());
            buf.writeUtf(b.translationKey() == null ? "" : b.translationKey(), 128);
        }
    }

    public static TownCenterGuiSyncPayload read(FriendlyByteBuf buf) {
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
}
