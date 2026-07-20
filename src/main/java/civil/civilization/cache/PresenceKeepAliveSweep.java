package civil.civilization.cache;

import civil.CivilMod;
import civil.CivilServices;
import civil.config.CivilConfig;
import civil.civilization.ServerClock;
import civil.civilization.TownCenterAabb;
import civil.civilization.TownCenterTracker;
import civil.civilization.VoxelChunkKey;
import civil.civilization.scoring.CivilizationService;
import civil.registry.DimensionPolicyRegistry;
import civil.registry.PresenceKeepAliveRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Presence keepalive: datapack-driven entity types enqueue result shards on an interval,
 * then {@link #consumeResultQueue} drains with a per-tick budget — same core path as
 * {@link PlayerAwarePrefetcher} ({@link CivilizationService#getCScoreAt} +
 * {@link ResultCache#visitAt}), without zone HUD / receipts / round-robin players.
 */
public final class PresenceKeepAliveSweep {

    private final ArrayDeque<PrefetchTask> resultQueue = new ArrayDeque<>();
    private final HashSet<String> resultDedupe = new HashSet<>();
    private final HashMap<String, ServerLevel> worldByDim = new HashMap<>();
    private final ArrayList<Entity> scratchEntities = new ArrayList<>();

    private long worldSessionId = 1L;
    /** Next server tick at which we run an enqueue wave ({@code server.getTickCount()}). */
    private long nextEnqueueAtTick = 0L;

    public PresenceKeepAliveSweep() {
    }

    /**
     * Called once per server tick after {@link ServerClock#tick()} (via {@link TtlCacheService#onServerTick}).
     */
    public void onServerTick(MinecraftServer server) {
        if (!CivilConfig.presenceKeepaliveEnabled) {
            return;
        }
        CivilizationService civilizationService = CivilServices.getCivilizationService();
        ResultCache resultCache = CivilServices.getResultCache();
        if (civilizationService == null || resultCache == null) {
            return;
        }

        long tick = server.getTickCount();
        if (tick >= this.nextEnqueueAtTick) {
            this.enqueueKeepaliveWave(server, tick);
            this.nextEnqueueAtTick = tick + Math.max(1L, CivilConfig.keepaliveIntervalTicks);
        }

        int consumedThisTick = 0;
        if (CivilConfig.keepaliveBudgetPerTick > 0) {
            consumedThisTick = this.consumeResultQueue(civilizationService, resultCache);
        }

        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info(
                    "[keepalive][tick] tick={} queue={} consumed={}",
                    tick, this.resultQueue.size(), consumedThisTick);
        }
    }

    private void enqueueKeepaliveWave(MinecraftServer server, long tick) {
        this.resultDedupe.clear();
        this.worldByDim.clear();
        if (PresenceKeepAliveRegistry.isEmpty()) {
            return;
        }
        TownCenterTracker townCenters = CivilServices.getTownCenterTracker();
        if (townCenters == null || !townCenters.isInitialized()) {
            return;
        }

        this.fillWorldByDim(server);

        EnqueueStats stats = new EnqueueStats();
        Map<EntityType<?>, PresenceKeepAliveRegistry.RadiusVc> types = PresenceKeepAliveRegistry.snapshot();

        for (Map.Entry<EntityType<?>, PresenceKeepAliveRegistry.RadiusVc> typeEntry : types.entrySet()) {
            EntityType<?> entityType = typeEntry.getKey();
            PresenceKeepAliveRegistry.RadiusVc radiusVc = typeEntry.getValue();
            int radiusX = radiusVc.rx();
            int radiusZ = radiusVc.rz();
            int radiusY = radiusVc.ry();

            for (ServerLevel world : server.getAllLevels()) {
                if (!DimensionPolicyRegistry.policyFor(world).civilization()) {
                    continue;
                }
                String dim = world.dimension().identifier().toString();
                var dimensionType = world.dimensionType();
                int dimMinY = dimensionType.minY();
                int dimMaxY = dimMinY + dimensionType.height() - 1;
                HashSet<Integer> seenEntityIds = new HashSet<>();

                townCenters.forEachGameplayActive(dim, world.getGameTime(), entry -> {
                    TownCenterAabb region = TownCenterAabb.atLectern(
                            new BlockPos(entry.x(), entry.y(), entry.z()), entry.level());
                    AABB box = toBlockAabb(region, dimMinY, dimMaxY);
                    if (box == null) {
                        return;
                    }

                    this.scratchEntities.clear();
                    this.scratchEntities.addAll(
                            world.getEntitiesOfClass(Entity.class, box, e -> e.getType() == entityType));

                    for (Entity entity : this.scratchEntities) {
                        if (!seenEntityIds.add(entity.getId())) {
                            continue;
                        }
                        this.enqueueEntityRadius(entity, dim, world, dimMinY, dimMaxY,
                                radiusX, radiusZ, radiusY, stats);
                    }
                });
            }
        }

        shuffleKeepaliveQueue(this.resultQueue);

        if (this.resultQueue.size() > CivilConfig.keepaliveQueueCap) {
            stats.trimmed = trimQueueToCap(this.resultQueue, CivilConfig.keepaliveQueueCap);
        }

        if (CivilMod.DEBUG && (stats.produced > 0 || stats.trimmed > 0)) {
            CivilMod.LOGGER.info(
                    "[keepalive][enqueue] tick={} produced={} trimmed={} queueSize={}",
                    tick, stats.produced, stats.trimmed, this.resultQueue.size());
        }
    }

    private void fillWorldByDim(MinecraftServer server) {
        for (ServerLevel world : server.getAllLevels()) {
            String dim = world.dimension().identifier().toString();
            this.worldByDim.put(dim, world);
        }
    }

    private static String dedupeToken(String dim, VoxelChunkKey vc) {
        return dim + "|" + vc.getCx() + "|" + vc.getCz() + "|" + vc.getSy();
    }

    private static AABB toBlockAabb(TownCenterAabb region, int dimMinY, int dimMaxY) {
        VoxelChunkKey min = region.minVc();
        VoxelChunkKey max = region.maxVc();
        int minY = Math.max(min.getSy() * 16, dimMinY);
        int maxY = Math.min(max.getSy() * 16 + 15, dimMaxY);
        if (minY > maxY) {
            return null;
        }
        return new AABB(
                min.getCx() * 16, minY, min.getCz() * 16,
                max.getCx() * 16 + 16, maxY + 1, max.getCz() * 16 + 16);
    }

    private void enqueueEntityRadius(Entity entity, String dim, ServerLevel world,
            int dimMinY, int dimMaxY, int radiusX, int radiusZ, int radiusY, EnqueueStats stats) {
        BlockPos pos = entity.blockPosition();
        VoxelChunkKey center = VoxelChunkKey.from(pos);
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                for (int dy = -radiusY; dy <= radiusY; dy++) {
                    VoxelChunkKey vc = center.offset(dx, dz, dy);
                    if (!vc.isValidIn(world, dimMinY, dimMaxY)) {
                        continue;
                    }
                    String token = dedupeToken(dim, vc);
                    if (!this.resultDedupe.add(token)) {
                        continue;
                    }
                    this.resultQueue.addLast(new PrefetchTask(this.worldSessionId, dim, vc));
                    stats.produced++;
                }
            }
        }
    }

    /**
     * Uniform random permutation of queued tasks (Fisher–Yates). Run once per enqueue wave before
     * {@link #trimQueueToCap} so trim drops an effectively random excess subset when over cap.
     */
    private static void shuffleKeepaliveQueue(ArrayDeque<PrefetchTask> queue) {
        int n = queue.size();
        if (n <= 1) {
            return;
        }
        PrefetchTask[] arr = queue.toArray(PrefetchTask[]::new);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = n - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            PrefetchTask tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
        queue.clear();
        for (PrefetchTask t : arr) {
            queue.addLast(t);
        }
    }

    /**
     * Drop tasks from the deque head until {@code queue.size() <= cap}. After {@link #shuffleKeepaliveQueue},
     * head order is random, so excess drops are not strictly “oldest-first”.
     */
    private static int trimQueueToCap(ArrayDeque<PrefetchTask> queue, int cap) {
        int trimmed = 0;
        while (queue.size() > cap) {
            queue.pollFirst();
            trimmed++;
        }
        return trimmed;
    }

    /**
     * Drain up to {@link CivilConfig#keepaliveBudgetPerTick} queue polls per tick (each poll counts toward
     * budget so stale tasks cannot spin the server thread).
     */
    /**
     * @return how many queue heads were polled this tick (each poll counts toward budget, including stale skips)
     */
    private int consumeResultQueue(CivilizationService civilizationService, ResultCache resultCache) {
        int budgetPerTick = CivilConfig.keepaliveBudgetPerTick;
        if (budgetPerTick <= 0) {
            return 0;
        }
        long serverNow = ServerClock.now();
        int drained = 0;
        for (int n = 0; n < budgetPerTick && !this.resultQueue.isEmpty(); n++) {
            PrefetchTask task = this.resultQueue.pollFirst();
            drained++;
            if (task == null) {
                continue;
            }
            if (task.worldSessionId() != this.worldSessionId) {
                continue;
            }
            ServerLevel world = this.worldByDim.get(task.dim());
            if (world == null) {
                continue;
            }
            VoxelChunkKey vc = task.vc();
            BlockPos centerPos = new BlockPos(
                    (vc.getCx() << 4) + 8,
                    (vc.getSy() << 4) + 8,
                    (vc.getCz() << 4) + 8);
            civilizationService.getCScoreAt(world, centerPos);
            resultCache.visitAt(world, vc, serverNow);
        }
        return drained;
    }

    /**
     * Invalidate queued tasks and bump session id (world unload / cache shutdown), mirroring
     * {@link PlayerAwarePrefetcher#clear()}.
     */
    public void clear() {
        this.resultQueue.clear();
        this.resultDedupe.clear();
        this.worldByDim.clear();
        this.scratchEntities.clear();
        this.nextEnqueueAtTick = 0L;
        this.worldSessionId++;
    }

    private static final class EnqueueStats {
        int produced;
        int trimmed;
    }

    private record PrefetchTask(long worldSessionId, String dim, VoxelChunkKey vc) {}
}
