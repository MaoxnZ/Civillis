package civil.civilization;

import civil.CivilMod;
import civil.CivilServices;
import civil.registry.ZonePolicyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-VC cache of zone policy matches (structure-based). Used for spawn bypass, HUD caution, and flee.
 *
 * <p>Cache is a {@link ConcurrentHashMap} so natural-spawn / structure queries from parallel server
 * work (e.g. threaded mob spawning) do not corrupt the map.
 */
public final class ZonePolicyService {

    private static final long CACHE_TTL_MS = 300_000L;
    private static final int CLEANUP_INTERVAL_TICKS = 100;

    private final Map<ZoneCacheKey, ZoneCacheEntry> cache = new ConcurrentHashMap<>();
    private long lastCleanupTick;

    public boolean allowsHostileSpawn(ServerLevel world, BlockPos pos, EntityType<?> entityType) {
        VoxelChunkKey vc = VoxelChunkKey.from(pos);
        if (isClaimedByMaxLevelTownCenter(world, vc)) {
            return false;
        }
        ZoneCacheEntry entry = getOrCompute(world, vc);
        for (ZonePolicyRegistry.ZonePolicyRule rule : entry.matchedRules()) {
            if (rule.rules().allows(entityType)) {
                return true;
            }
        }
        return false;
    }

    public boolean treatAsNonCivilized(ServerLevel world, VoxelChunkKey vc) {
        if (isClaimedByMaxLevelTownCenter(world, vc)) {
            return false;
        }
        ZoneCacheEntry entry = getOrCompute(world, vc);
        return entry.treatAsNonCivilized();
    }

    public void onServerTick(long tick) {
        if (tick - this.lastCleanupTick >= CLEANUP_INTERVAL_TICKS) {
            this.lastCleanupTick = tick;
            long now = ServerClock.now();
            int removed = 0;
            Iterator<Map.Entry<ZoneCacheKey, ZoneCacheEntry>> it = this.cache.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ZoneCacheKey, ZoneCacheEntry> e = it.next();
                if (e.getValue().expireAtMs() >= now) continue;
                it.remove();
                ++removed;
            }
        }
    }

    public void clear() {
        this.cache.clear();
    }

    private ZoneCacheEntry getOrCompute(ServerLevel world, VoxelChunkKey vc) {
        String dim = world.dimension().identifier().toString();
        ZoneCacheKey key = new ZoneCacheKey(dim, vc.getCx(), vc.getCz(), vc.getSy());
        long now = ServerClock.now();
        ZoneCacheEntry existing = this.cache.get(key);
        if (existing != null && existing.expireAtMs() >= now) {
            ZoneCacheEntry touched = new ZoneCacheEntry(
                    now + CACHE_TTL_MS,
                    existing.treatAsNonCivilized(),
                    existing.matchedRules(),
                    existing.matchedStructures());
            this.cache.put(key, touched);
            return touched;
        }
        ZoneCacheEntry computed = computeEntry(world, vc, now);
        this.cache.put(key, computed);
        return computed;
    }

    private static ZoneCacheEntry computeEntry(ServerLevel world, VoxelChunkKey vc, long now) {
        ArrayList<ZonePolicyRegistry.ZonePolicyRule> matched = new ArrayList<>();
        boolean treatAsNonCivilized = false;
        BlockPos centerPos = new BlockPos((vc.getCx() << 4) + 8, (vc.getSy() << 4) + 8, (vc.getCz() << 4) + 8);
        for (ZonePolicyRegistry.ZonePolicyRule rule : ZonePolicyRegistry.rules()) {
            if (!matchesAnyStructure(world, centerPos, rule.structures())) continue;
            matched.add(rule);
            if (rule.rules().allowHostileSpawn()) {
                treatAsNonCivilized = true;
            }
        }
        if (CivilMod.DEBUG) {
            debugLogStructureProbe(world, centerPos, treatAsNonCivilized);
        }
        return new ZoneCacheEntry(now + CACHE_TTL_MS, treatAsNonCivilized, List.copyOf(matched), List.of());
    }

    private static boolean isClaimedByMaxLevelTownCenter(ServerLevel world, VoxelChunkKey vc) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) {
            return false;
        }
        String dim = world.dimension().identifier().toString();
        return tracker.isClaimedByMaxLevelTownCenter(dim, vc);
    }

    /**
     * Extra structure probes for logging — only invoked when {@link CivilMod#DEBUG} is true
     * (call site is guarded; no work when debug is off).
     */
    private static void debugLogStructureProbe(ServerLevel world, BlockPos centerPos, boolean treatAsNonCivilized) {
        String dim = world.dimension().identifier().toString();
        StringBuilder buf = new StringBuilder();
        for (ZonePolicyRegistry.ZonePolicyRule rule : ZonePolicyRegistry.rules()) {
            for (net.minecraft.resources.Identifier sid : rule.structures()) {
                if (isInStructure(world, centerPos, sid)) {
                    if (buf.length() > 0) {
                        buf.append("; ");
                    }
                    buf.append(rule.id()).append(':').append(sid);
                }
            }
        }
        if (buf.isEmpty()) {
            CivilMod.LOGGER.info("[zone][structure] dim={} pos={} treatAsNonCivilized={} matched=<none>",
                    dim, centerPos, treatAsNonCivilized);
        } else {
            CivilMod.LOGGER.info("[zone][structure] dim={} pos={} treatAsNonCivilized={} matched=[{}]",
                    dim, centerPos, treatAsNonCivilized, buf);
        }
    }

    private static boolean matchesAnyStructure(ServerLevel world, BlockPos pos, Iterable<net.minecraft.resources.Identifier> structures) {
        for (net.minecraft.resources.Identifier structureId : structures) {
            if (isInStructure(world, pos, structureId)) return true;
        }
        return false;
    }

    private static boolean isInStructure(ServerLevel world, BlockPos pos, net.minecraft.resources.Identifier structureId) {
        var lookup = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, structureId);
        Optional<Holder.Reference<Structure>> holderOpt = lookup.get(key);
        if (holderOpt.isEmpty()) {
            return false;
        }
        Structure structure = holderOpt.get().value();
        // CFR: getStructureAt then validity — not merely non-null (invalid starts exist and would match all ids in logs).
        StructureStart start = world.structureManager().getStructureAt(pos, structure);
        return start != null && start.isValid();
    }

    private record ZoneCacheEntry(
            long expireAtMs,
            boolean treatAsNonCivilized,
            List<ZonePolicyRegistry.ZonePolicyRule> matchedRules,
            List<net.minecraft.resources.Identifier> matchedStructures) {}

    private record ZoneCacheKey(String dim, int cx, int cz, int sy) {}
}
