package civil.civilization.storage;

import civil.civilization.TownCenterTracker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read/write {@link TownCenterTracker.TownCenterEntry} for {@code town_centers.nbt}. */
public final class TownCenterNbtCodec {

    private static final String SCHEMA = "schema";
    /** First release format for town center profile entries. */
    public static final int SCHEMA_VERSION = 1;

    private TownCenterNbtCodec() {}

    public static CompoundTag writeEntry(String dim, TownCenterTracker.TownCenterEntry entry) {
        CompoundTag e = new CompoundTag();
        e.putString("dim", dim);
        e.putInt("x", entry.x());
        e.putInt("y", entry.y());
        e.putInt("z", entry.z());
        e.putInt("level", entry.level());
        e.putBoolean("activated", entry.activated());
        e.putLong("deactivateDeadlineTick", entry.deactivateDeadlineTick());

        CompoundTag identity = new CompoundTag();
        identity.putString("displayName", entry.displayName());
        if (entry.creatorUuid() != null) {
            putUuid(identity, "creatorUuid", entry.creatorUuid());
        }
        identity.putString("creatorName", entry.creatorName());
        e.put("identity", identity);

        CompoundTag build = new CompoundTag();
        ListTag applied = new ListTag();
        for (TownCenterTracker.AppliedEffect fx : entry.appliedEffects()) {
            CompoundTag t = new CompoundTag();
            t.putString("zoneBuffOfferId", fx.zoneBuffOfferId());
            t.putString("effect", fx.effectId());
            t.putInt("amplifier", fx.amplifier());
            t.putInt("chosenAtLevel", fx.chosenAtLevel());
            t.putBoolean("ambient", fx.ambient());
            t.putBoolean("showParticles", fx.showParticles());
            t.putBoolean("showIcon", fx.showIcon());
            applied.add(t);
        }
        build.put("appliedEffects", applied);
        CompoundTag levelState = new CompoundTag();
        for (var kv : entry.levelEffectState().entrySet()) {
            levelState.putString(String.valueOf(kv.getKey()), kv.getValue().name());
        }
        build.put("levelEffectState", levelState);
        e.put("build", build);

        CompoundTag membership = new CompoundTag();
        membership.putBoolean("openRegistration", entry.openRegistration());
        ListTag members = new ListTag();
        for (TownCenterTracker.Member m : entry.members()) {
            CompoundTag mt = new CompoundTag();
            putUuid(mt, "uuid", m.uuid());
            mt.putString("name", m.name());
            members.add(mt);
        }
        membership.put("members", members);
        e.put("membership", membership);
        return e;
    }

    public static TownCenterTracker.TownCenterEntry readEntry(CompoundTag e) {
        int x = e.getInt("x").orElse(0);
        int y = e.getInt("y").orElse(0);
        int z = e.getInt("z").orElse(0);
        int level = e.getInt("level").orElse(1);
        boolean activated = e.getBoolean("activated").orElse(false);
        long deadline = e.getLong("deactivateDeadlineTick").orElse(0L);

        if (!e.contains("identity")) {
            return new TownCenterTracker.TownCenterEntry(
                    x, y, z, level, activated, deadline,
                    "", null, "",
                    List.of(), Map.of(),
                    false, List.of());
        }

        CompoundTag identity = e.getCompound("identity").orElse(new CompoundTag());
        String displayName = identity.getString("displayName").orElse("");
        UUID creatorUuid = readUuid(identity, "creatorUuid");
        String creatorName = identity.getString("creatorName").orElse("");

        CompoundTag build = e.getCompound("build").orElse(new CompoundTag());
        List<TownCenterTracker.AppliedEffect> applied = new ArrayList<>();
        ListTag appliedTag = build.getList("appliedEffects").orElse(new ListTag());
        for (int i = 0; i < appliedTag.size(); i++) {
            CompoundTag t = appliedTag.getCompound(i).orElse(new CompoundTag());
            applied.add(new TownCenterTracker.AppliedEffect(
                    t.getString("zoneBuffOfferId").orElse(""),
                    t.getString("effect").orElse(""),
                    t.getInt("amplifier").orElse(0),
                    t.getInt("chosenAtLevel").orElse(0),
                    t.getBoolean("ambient").orElse(true),
                    t.getBoolean("showParticles").orElse(false),
                    t.getBoolean("showIcon").orElse(true)));
        }
        Map<Integer, TownCenterTracker.LevelEffectChoice> levelState = new HashMap<>();
        CompoundTag levelStateTag = build.getCompound("levelEffectState").orElse(new CompoundTag());
        for (String key : levelStateTag.keySet()) {
            try {
                int target = Integer.parseInt(key);
                String raw = levelStateTag.getString(key).orElse("");
                levelState.put(target, TownCenterTracker.LevelEffectChoice.valueOf(raw));
            } catch (Exception ignored) {
            }
        }

        CompoundTag membership = e.getCompound("membership").orElse(new CompoundTag());
        boolean openRegistration = membership.getBoolean("openRegistration").orElse(false);
        List<TownCenterTracker.Member> members = new ArrayList<>();
        ListTag membersTag = membership.getList("members").orElse(new ListTag());
        for (int i = 0; i < membersTag.size(); i++) {
            CompoundTag mt = membersTag.getCompound(i).orElse(new CompoundTag());
            UUID uuid = readUuid(mt, "uuid");
            if (uuid != null) {
                members.add(new TownCenterTracker.Member(uuid, mt.getString("name").orElse("")));
            }
        }

        return new TownCenterTracker.TownCenterEntry(
                x, y, z, level, activated, deadline,
                displayName, creatorUuid, creatorName,
                applied, levelState,
                openRegistration, List.copyOf(members));
    }

    /** Missing {@code schema} is treated as {@link #SCHEMA_VERSION} for local dev saves. */
    public static int readSchema(CompoundTag root) {
        return root.getInt(SCHEMA).orElse(SCHEMA_VERSION);
    }

    public static boolean isSupportedSchema(int schema) {
        return schema == SCHEMA_VERSION || schema == 2;
    }

    private static void putUuid(CompoundTag tag, String key, UUID uuid) {
        tag.putLong(key + "Most", uuid.getMostSignificantBits());
        tag.putLong(key + "Least", uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(CompoundTag tag, String key) {
        if (!tag.contains(key + "Most") || !tag.contains(key + "Least")) return null;
        return new UUID(tag.getLong(key + "Most").orElse(0L), tag.getLong(key + "Least").orElse(0L));
    }
}
