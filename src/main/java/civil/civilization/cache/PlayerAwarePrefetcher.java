package civil.civilization.cache;

import civil.CivilMod;
import civil.CivilPlatform;
import civil.CivilServices;
import civil.config.CivilConfig;
import civil.civilization.ServerClock;
import civil.civilization.CivilRegionClassifier;
import civil.civilization.CivilRegionKind;
import civil.civilization.TownCenterTracker;
import civil.civilization.TownCenterTracker.TownCenterEntry;
import civil.civilization.VoxelChunkKey;
import civil.civilization.ZonePolicyService;
import civil.civilization.ZoneSemanticState;
import civil.civilization.ZoneTransitionPayload;
import civil.civilization.scoring.CivilizationService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * CFR 1.2.2: per-tick result prefetch queue, round-robin budget, epoch receipts, zone HUD pipeline.
 * No L1 touching here — that belongs elsewhere if the product needs it.
 */
public final class PlayerAwarePrefetcher {

    private static final long MOVING_ENQUEUE_SEC = 1L;
    private static final long WARM_IDLE_SECONDS = 10L;
    private static final long WARM_IDLE_ENQUEUE_SEC = 1L;
    private static final long STATIC_ENQUEUE_SEC = 30L;
    /** Skip prefetch enqueue when Chebyshev block step from last tick exceeds this (teleport / elytra dive, etc.). */
    private static final int PREFETCH_ENQUEUE_MAX_BLOCK_STEP = 2;

    private final Map<UUID, PlayerState> playerStates = new HashMap<>();
    private final Map<UUID, ArrayDeque<PrefetchTask>> resultQueuesByPlayer = new HashMap<>();
    private final ArrayList<UUID> rrPlayers = new ArrayList<>();
    private final HashSet<String> resultDedupe = new HashSet<>();
    private final Map<PlayerEpochKey, ResultReceiptAgg> resultReceipts = new HashMap<>();
    private final HashSet<UUID> activePlayersThisEpoch = new HashSet<>();
    private long activeEpoch = Long.MIN_VALUE;
    private int rrCursor = 0;
    private long worldSessionId = 1L;

    public PlayerAwarePrefetcher() {
    }

    public void onServerTick(MinecraftServer server) {
        long nowSec = ServerClock.now() / 1000L;
        long currentEpoch = nowSec;
        if (this.activeEpoch != currentEpoch) {
            this.activeEpoch = currentEpoch;
            this.activePlayersThisEpoch.clear();
        }
        ResultCache resultCache = CivilServices.getResultCache();
        CivilizationService civilizationService = CivilServices.getCivilizationService();
        int resultRadiusX = CivilConfig.patrolRadiusX;
        int resultRadiusZ = CivilConfig.patrolRadiusZ;
        int resultRadiusY = CivilConfig.patrolRadiusY;

        HashMap<String, ServerLevel> worldByDim = new HashMap<>();
        this.enqueuePlayerPrefetch(server, currentEpoch, nowSec, resultRadiusX, resultRadiusZ, resultRadiusY, worldByDim);

        this.consumeResultQueue(currentEpoch, worldByDim, resultCache, civilizationService,
                CivilConfig.resultBudgetPerTick, CivilConfig.resultEpochTtlSec);
        this.flushResultReceipts(currentEpoch, server);
    }

    /**
     * Walk online players and enqueue result-shard prefetch tasks when movement / interval gates allow.
     * Fills {@code worldByDim} for {@link #consumeResultQueue} in the same tick.
     */
    private void enqueuePlayerPrefetch(MinecraftServer server, long currentEpoch, long nowSec,
                                       int resultRadiusX, int resultRadiusZ, int resultRadiusY,
                                       HashMap<String, ServerLevel> worldByDim) {
        for (ServerLevel world : server.getAllLevels()) {
            String dim = world.dimension().identifier().toString();
            worldByDim.put(dim, world);
            for (ServerPlayer player : world.players()) {
                UUID playerId = player.getUUID();
                BlockPos pos = player.blockPosition();
                VoxelChunkKey center = VoxelChunkKey.from(pos);
                PlayerState state = this.playerStates.computeIfAbsent(playerId, id -> new PlayerState());
                boolean dimChanged = state.lastDim == null || !state.lastDim.equals(dim);
                boolean moved = dimChanged || state.lastSeenVC == null || !state.lastSeenVC.equals(center);
                if (moved) {
                    state.lastMovementSec = nowSec;
                }
                long idleSec = state.lastMovementSec == 0L ? Long.MAX_VALUE : nowSec - state.lastMovementSec;
                boolean warmIdle = !moved && idleSec <= WARM_IDLE_SECONDS;
                long intervalSec = moved ? MOVING_ENQUEUE_SEC
                        : warmIdle ? WARM_IDLE_ENQUEUE_SEC : STATIC_ENQUEUE_SEC;
                boolean shouldEnqueue = state.lastResultEnqueueSec == 0L
                        || nowSec - state.lastResultEnqueueSec >= intervalSec;
                int blockStepCheb = 0;
                boolean tooFastBlockStep = false;
                if (!dimChanged && state.lastTickBlockPos != null) {
                    BlockPos prev = state.lastTickBlockPos;
                    int dx = Math.abs(pos.getX() - prev.getX());
                    int dy = Math.abs(pos.getY() - prev.getY());
                    int dz = Math.abs(pos.getZ() - prev.getZ());
                    blockStepCheb = Math.max(dx, Math.max(dy, dz));
                    tooFastBlockStep = blockStepCheb > PREFETCH_ENQUEUE_MAX_BLOCK_STEP;
                }
                if (shouldEnqueue && !tooFastBlockStep) {
                    boolean firstSeen = state.lastResultEnqueueSec == 0L;
                    EnqueueStats enqueueStats = this.enqueueArea(currentEpoch, dim, center, playerId,
                            resultRadiusX, resultRadiusZ, resultRadiusY, this.resultDedupe);
                    state.lastResultEnqueueSec = nowSec;
                    this.activePlayersThisEpoch.add(playerId);
                    if (CivilMod.DEBUG) {
                        String reason = firstSeen ? "firstSeen"
                                : moved ? "movingInterval"
                                : warmIdle ? "warmIdleInterval"
                                : "staticInterval";
                        ArrayDeque<PrefetchTask> q = this.resultQueuesByPlayer.get(playerId);
                        int queueSize = q != null ? q.size() : 0;
                        CivilMod.LOGGER.info(
                                "[zone][enqueue] epoch={} player={} dim={} vc={} reason={} produced={} trimmed={} queueSize={} moved={} warmIdle={} idleSec={} intervalSec={}",
                                currentEpoch, playerId, dim, center, reason, enqueueStats.produced, enqueueStats.trimmed, queueSize,
                                moved, warmIdle, idleSec, intervalSec);
                    }
                } else if (CivilMod.DEBUG && shouldEnqueue && tooFastBlockStep) {
                    CivilMod.LOGGER.info(
                            "[zone][enqueue] skip tooFastBlockStep epoch={} player={} dim={} vc={} chebStep={} cap={}",
                            currentEpoch, playerId, dim, center, blockStepCheb, PREFETCH_ENQUEUE_MAX_BLOCK_STEP);
                }
                this.applyFastCautionTransition(server, currentEpoch, world, dim, center, state, playerId, dimChanged);
                state.lastSeenVC = center;
                state.lastDim = dim;
                state.lastTickBlockPos = pos.immutable();
            }
        }
    }

    private static ZoneSemanticState resolveFastOverrideState(ServerLevel world, VoxelChunkKey vc) {
        CivilRegionKind k = CivilRegionClassifier.classify(world, vc).kind();
        return switch (k) {
            case SHRINE -> ZoneSemanticState.SHRINE;
            case ZONE -> ZoneSemanticState.CAUTION;
            default -> null;
        };
    }

    private int applyFastCautionTransition(MinecraftServer server, long epoch, ServerLevel world, String dim,
                                           VoxelChunkKey center, PlayerState state, UUID playerId, boolean dimChanged) {
        boolean vcChangedForHud = dimChanged || state.lastHudVc == null || !state.lastHudVc.equals(center);
        if (!vcChangedForHud) {
            return 0;
        }
        ZoneSemanticState previousOverride = state.fastOverrideState;
        ZoneSemanticState overrideNow = resolveFastOverrideState(world, center);
        state.fastOverrideState = overrideNow;
        state.lastHudVc = center;
        if (!state.zoneInitialized) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[zone][fast] skip send: zone not initialized yet player={} dim={} vc={} overrideNow={}",
                        playerId, dim, center, overrideNow);
            }
            return 0;
        }
        ZoneSemanticState oldState = state.zoneState;
        if (overrideNow == null) {
            if (previousOverride != null) {
                state.zoneState = null;
                state.lastHudTcKey = null;
                state.lastHudTcLabel = null;
                if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.info("[zone][fast] exit override -> unknown player={} epoch={} oldState={} vc={}",
                            playerId, epoch, oldState, center);
                }
            }
            return 0;
        }

        ZoneSemanticState newState = overrideNow;
        if (newState == oldState) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[zone][fast] no override change player={} epoch={} state={} overrideNow={}",
                        playerId, epoch, newState, overrideNow);
            }
            return 0;
        }
        state.zoneState = newState;
        state.lastHudTcKey = null;
        state.lastHudTcLabel = null;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.warn("[zone][fast] skip send: player offline playerId={}", playerId);
            }
            return 0;
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[zone][fast] SEND epoch={} {} -> {} (overrideNow={}) player={}",
                    epoch, oldState, newState, overrideNow, playerId);
        }
        CivilPlatform.sendToPlayer(player, new ZoneTransitionPayload(epoch, newState.id(), ""));
        return 1;
    }

    private EnqueueStats enqueueArea(long epoch, String dim, VoxelChunkKey center, UUID playerId,
                                     int radiusX, int radiusZ, int radiusY, HashSet<String> dedupe) {
        EnqueueStats stats = new EnqueueStats();
        ArrayDeque<PrefetchTask> queue = this.getOrCreatePlayerQueue(playerId);
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                for (int dy = -radiusY; dy <= radiusY; dy++) {
                    VoxelChunkKey vc = center.offset(dx, dz, dy);
                    boolean centerSample = dx == 0 && dz == 0 && dy == 0;
                    String token = dedupeToken(epoch, dim, vc, playerId);
                    if (!dedupe.add(token)) {
                        continue;
                    }
                    queue.addLast(new PrefetchTask(this.worldSessionId, epoch, dim, vc, playerId, centerSample, token));
                    stats.produced++;
                }
            }
        }
        int perPlayerCap = this.computePerPlayerQueueCap();
        if (queue.size() > perPlayerCap) {
            stats.trimmed = trimQueueToCap(queue, dedupe, perPlayerCap);
        }
        return stats;
    }

    private int computePerPlayerQueueCap() {
        int volume = (CivilConfig.patrolRadiusX * 2 + 1) * (CivilConfig.patrolRadiusZ * 2 + 1)
                * (CivilConfig.patrolRadiusY * 2 + 1);
        int ttlWindows = CivilConfig.resultEpochTtlSec + 1;
        long cap = (long) volume * (long) ttlWindows;
        if (cap < 1L) {
            return 1;
        }
        return (int) Math.min(cap, Integer.MAX_VALUE);
    }

    private static int trimQueueToCap(ArrayDeque<PrefetchTask> queue, HashSet<String> dedupe, int cap) {
        int trimmed = 0;
        PrefetchTask dropped;
        while (queue.size() > cap && (dropped = queue.pollFirst()) != null) {
            dedupe.remove(dropped.dedupeToken());
            trimmed++;
        }
        return trimmed;
    }

    private ArrayDeque<PrefetchTask> getOrCreatePlayerQueue(UUID playerId) {
        ArrayDeque<PrefetchTask> queue = this.resultQueuesByPlayer.get(playerId);
        if (queue != null) {
            return queue;
        }
        ArrayDeque<PrefetchTask> created = new ArrayDeque<>();
        this.resultQueuesByPlayer.put(playerId, created);
        this.rrPlayers.add(playerId);
        return created;
    }

    private static String dedupeToken(long epoch, String dim, VoxelChunkKey vc, UUID playerId) {
        return epoch + "|" + playerId + "|" + dim + "|" + vc.getCx() + "|" + vc.getCz() + "|" + vc.getSy();
    }

    private ConsumeStats consumeResultQueue(long currentEpoch, Map<String, ServerLevel> worldByDim,
                                            ResultCache resultCache, CivilizationService civilizationService,
                                            int budgetPerTick, int epochTtlSec) {
        ConsumeStats stats = new ConsumeStats();
        if (budgetPerTick <= 0) {
            return stats;
        }
        long serverNow = ServerClock.now();
        BudgetPlan plan = this.buildBudgetPlan(budgetPerTick);
        HashMap<UUID, Integer> remainingByPlayer = new HashMap<>(plan.quotaByPlayer);
        if (remainingByPlayer.isEmpty()) {
            return stats;
        }
        int attemptsWithoutProgress = 0;
        int maxAttempts = Math.max(64, this.rrPlayers.size() * 8 + budgetPerTick * 4);
        while (stats.consumed < budgetPerTick && !this.rrPlayers.isEmpty() && attemptsWithoutProgress < maxAttempts) {
            if (this.rrCursor >= this.rrPlayers.size()) {
                this.rrCursor = 0;
            }
            UUID currentPlayer = this.rrPlayers.get(this.rrCursor);
            Integer remain = remainingByPlayer.get(currentPlayer);
            if (remain == null || remain <= 0) {
                this.rrCursor = (this.rrCursor + 1) % this.rrPlayers.size();
                attemptsWithoutProgress++;
                continue;
            }
            ArrayDeque<PrefetchTask> queue = this.resultQueuesByPlayer.get(currentPlayer);
            if (queue == null || queue.isEmpty()) {
                this.removePlayerQueueAtCursor();
                remainingByPlayer.remove(currentPlayer);
                attemptsWithoutProgress++;
                continue;
            }
            PrefetchTask task = queue.pollFirst();
            remainingByPlayer.put(currentPlayer, remain - 1);
            if (queue.isEmpty()) {
                this.removePlayerQueueAtCursor();
                remainingByPlayer.remove(currentPlayer);
            } else {
                this.rrCursor = (this.rrCursor + 1) % this.rrPlayers.size();
            }
            if (task == null) {
                continue;
            }
            this.resultDedupe.remove(task.dedupeToken());
            if (!this.isTaskValid(task, currentEpoch, epochTtlSec)) {
                stats.dropped++;
                continue;
            }
            ServerLevel world = worldByDim.get(task.dim());
            if (world == null || resultCache == null || civilizationService == null) {
                continue;
            }
            VoxelChunkKey vc = task.vc();
            BlockPos centerPos = new BlockPos((vc.getCx() << 4) + 8, (vc.getSy() << 4) + 8, (vc.getCz() << 4) + 8);
            civilizationService.getCScoreAt(world, centerPos);
            resultCache.visitAt(world, vc, serverNow);
            this.collectResultReceipt(task, resultCache, world, serverNow);
            stats.consumed++;
            attemptsWithoutProgress = 0;
        }
        return stats;
    }

    private BudgetPlan buildBudgetPlan(int globalBudgetPerTick) {
        if (globalBudgetPerTick <= 0) {
            return new BudgetPlan(Map.of(), 0, 0, 0);
        }
        ArrayList<UUID> budgetPlayers = new ArrayList<>();
        for (UUID pid : this.rrPlayers) {
            if (!this.activePlayersThisEpoch.contains(pid)) {
                continue;
            }
            ArrayDeque<PrefetchTask> q = this.resultQueuesByPlayer.get(pid);
            if (q == null || q.isEmpty()) {
                continue;
            }
            budgetPlayers.add(pid);
        }
        if (budgetPlayers.isEmpty()) {
            for (UUID pid : this.rrPlayers) {
                ArrayDeque<PrefetchTask> q = this.resultQueuesByPlayer.get(pid);
                if (q == null || q.isEmpty()) {
                    continue;
                }
                budgetPlayers.add(pid);
            }
        }
        if (budgetPlayers.isEmpty()) {
            return new BudgetPlan(Map.of(), 0, 0, 0);
        }
        int n = budgetPlayers.size();
        int q2 = globalBudgetPerTick / n;
        int r = globalBudgetPerTick % n;
        HashSet<UUID> budgetSet = new HashSet<>(budgetPlayers);
        HashMap<UUID, Integer> quota = new HashMap<>(n);
        int bonusLeft = r;
        for (UUID pid : this.rrPlayers) {
            if (!budgetSet.contains(pid)) {
                continue;
            }
            int share = q2;
            if (bonusLeft > 0) {
                share++;
                bonusLeft--;
            }
            quota.put(Objects.requireNonNull(pid), share);
        }
        return new BudgetPlan(quota, n, q2, r);
    }

    private void removePlayerQueueAtCursor() {
        if (this.rrPlayers.isEmpty()) {
            this.rrCursor = 0;
            return;
        }
        UUID removed = this.rrPlayers.remove(this.rrCursor);
        this.resultQueuesByPlayer.remove(removed);
        if (this.rrPlayers.isEmpty()) {
            this.rrCursor = 0;
        } else if (this.rrCursor >= this.rrPlayers.size()) {
            this.rrCursor = 0;
        }
    }

    private void collectResultReceipt(PrefetchTask task, ResultCache resultCache, ServerLevel world, long serverNow) {
        if (task.playerId() == null) {
            return;
        }
        ResultEntry entry = resultCache.getIfPresent(world, task.vc());
        if (entry == null || !entry.isConfigValid()) {
            return;
        }
        double score = entry.getEffectiveScore(serverNow);
        double strongMin = CivilConfig.spawnThresholdMid
                + (1.0 - CivilConfig.spawnThresholdMid) * CivilConfig.zoneReceiptStrongCivilizedRatio;
        boolean strongCivilized = score >= strongMin;
        boolean centerBand = score >= CivilConfig.spawnThresholdMid;
        UUID playerId = Objects.requireNonNull(task.playerId());
        PlayerEpochKey key = new PlayerEpochKey(playerId, task.epoch());
        ResultReceiptAgg agg = this.resultReceipts.computeIfAbsent(key, unused -> new ResultReceiptAgg());
        agg.sampleCount++;
        if (strongCivilized) {
            agg.civilizedCount++;
        }
        if (task.centerSample()) {
            agg.centerSeen = true;
            agg.centerCivilized = centerBand;
            agg.centerDim = task.dim();
            agg.centerVc = task.vc();
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info(
                        "[zone][receipt-center] epoch={} player={} dim={} vc={} score={} centerCiv={} strongMin={} mid={}",
                        task.epoch(), playerId, task.dim(), task.vc(),
                        String.format("%.4f", score), centerBand,
                        String.format("%.4f", strongMin),
                        String.format("%.4f", CivilConfig.spawnThresholdMid));
            }
        }
    }

    private ReceiptFlushStats flushResultReceipts(long currentEpoch, MinecraftServer server) {
        ReceiptFlushStats stats = new ReceiptFlushStats();
        int debugNoCenterLogsLeft = CivilMod.DEBUG ? 8 : 0;
        HashMap<String, ServerLevel> worldByDim = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            worldByDim.put(level.dimension().identifier().toString(), level);
        }
        Iterator<Map.Entry<PlayerEpochKey, ResultReceiptAgg>> it = this.resultReceipts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PlayerEpochKey, ResultReceiptAgg> e = it.next();
            PlayerEpochKey key = e.getKey();
            if (key.epoch() >= currentEpoch) {
                continue;
            }
            stats.flushed++;
            ResultReceiptAgg agg = e.getValue();
            if (agg.sampleCount > 0 && agg.centerSeen) {
                int requiredCountEnter = Math.max(1, CivilConfig.requiredCountEnter);
                int requiredCountLeave = Math.max(1, CivilConfig.requiredCountLeave);
                int enterThreshold = Math.max(1, Math.min(requiredCountEnter, agg.sampleCount));
                int leaveThreshold = Math.max(1, agg.sampleCount - requiredCountLeave);
                boolean enter = agg.centerCivilized && agg.civilizedCount >= enterThreshold;
                boolean leave = !agg.centerCivilized && agg.civilizedCount < leaveThreshold;
                UUID playerId = Objects.requireNonNull(key.playerId());
                PlayerState state = this.playerStates.computeIfAbsent(playerId, unused -> new PlayerState());
                ServerLevel receiptWorld = agg.centerDim != null ? worldByDim.get(agg.centerDim) : null;
                boolean inCautionByZonePolicy = false;
                ZonePolicyService zonePolicyService = CivilServices.getZonePolicyService();
                if (!state.zoneInitialized && zonePolicyService != null && agg.centerDim != null && agg.centerVc != null
                        && receiptWorld != null) {
                    inCautionByZonePolicy = zonePolicyService.treatAsNonCivilized(receiptWorld, agg.centerVc);
                }
                ZoneSemanticState oldState = state.zoneState;
                ZoneSemanticState oldBase = state.baseState;
                ZoneSemanticState candidateBase = enter ? ZoneSemanticState.CIVILIZED
                        : leave ? ZoneSemanticState.WILDERNESS : oldBase;
                ServerPlayer livePlayer = server.getPlayerList().getPlayer(playerId);
                ServerLevel currentWorld = livePlayer != null && livePlayer.level() instanceof ServerLevel sl
                        ? sl
                        : worldByDim.get(state.lastDim);
                VoxelChunkKey currentVc = livePlayer != null
                        ? VoxelChunkKey.from(livePlayer.blockPosition())
                        : state.lastHudVc;
                ZoneSemanticState currentOverride = currentWorld != null && currentVc != null
                        ? resolveFastOverrideState(currentWorld, currentVc)
                        : null;
                state.baseState = candidateBase;
                ZoneSemanticState newState = currentOverride != null ? currentOverride : candidateBase;
                if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.info(
                            "[zone][receipt] keyEpoch={} currentEpoch={} player={} samples={} civCount={} centerCiv={} enter={} leave={} enterTh={} leaveTh={} oldBase={} candidateBase={} inCautionPolicy={} currentVc={} currentOverride={} newState={} oldState={} zoneInit={}",
                            key.epoch(), currentEpoch, playerId, agg.sampleCount, agg.civilizedCount, agg.centerCivilized,
                            enter, leave, enterThreshold, leaveThreshold, oldBase, candidateBase, inCautionByZonePolicy,
                            currentVc, currentOverride, newState, oldState, state.zoneInitialized);
                }
                if (!state.zoneInitialized) {
                    state.fastOverrideState = newState == candidateBase ? null : newState;
                    state.zoneInitialized = true;
                    if (CivilMod.DEBUG) {
                        CivilMod.LOGGER.info("[zone][receipt] first init newState={} override={} inCautionPolicy={} player={}",
                                newState, state.fastOverrideState, inCautionByZonePolicy, playerId);
                    }
                }
                state.zoneState = newState;
                TcHudLabel tcHudLabel = null;
                if (newState == ZoneSemanticState.CIVILIZED
                        && receiptWorld != null && agg.centerDim != null && agg.centerVc != null) {
                    tcHudLabel = resolveTownCenterHudLabel(
                            agg.centerDim, agg.centerVc, playerId, receiptWorld.getGameTime());
                }
                String nextTcKey = tcHudLabel != null ? tcHudLabel.key() : null;
                String nextTcLabel = tcHudLabel != null ? tcHudLabel.label() : null;
                boolean tcLabelChanged = newState == ZoneSemanticState.CIVILIZED
                        && nextTcKey != null
                        && (!Objects.equals(nextTcKey, state.lastHudTcKey)
                        || !Objects.equals(nextTcLabel, state.lastHudTcLabel));

                if (newState != null && (newState != oldState || tcLabelChanged)) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        if (CivilMod.DEBUG) {
                            CivilMod.LOGGER.info("[zone][receipt] SEND receiptEpoch={} currentEpoch={} {} -> {} player={} tcLabel={}",
                                    key.epoch(), currentEpoch, oldState, newState, playerId,
                                    tcHudLabel != null ? tcHudLabel.label() : "");
                        }
                        // Window id = completed receipt bucket second (prior wall second); not same as fast's current-epoch send.
                        CivilPlatform.sendToPlayer(player, new ZoneTransitionPayload(
                                key.epoch(), newState.id(), nextTcLabel != null ? nextTcLabel : ""));
                        state.lastHudTcKey = nextTcKey;
                        state.lastHudTcLabel = nextTcLabel;
                        stats.transitions++;
                    }
                } else if (newState != ZoneSemanticState.CIVILIZED) {
                    state.lastHudTcKey = null;
                    state.lastHudTcLabel = null;
                } else if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.info("[zone][receipt] no send: newState==oldState ({}) player={}", newState, playerId);
                }
            } else if (CivilMod.DEBUG && agg.sampleCount > 0 && !agg.centerSeen && debugNoCenterLogsLeft-- > 0) {
                CivilMod.LOGGER.info("[zone][receipt] waiting center sample: keyEpoch={} currentEpoch={} player={} samples={} civCount={}",
                        key.epoch(), currentEpoch, key.playerId(), agg.sampleCount, agg.civilizedCount);
            }
            it.remove();
        }
        return stats;
    }

    private boolean isTaskValid(PrefetchTask task, long currentEpoch, int epochTtlSec) {
        if (task.worldSessionId() != this.worldSessionId) {
            return false;
        }
        if (epochTtlSec < 0) {
            return true;
        }
        return currentEpoch - task.epoch() <= (long) epochTtlSec;
    }

    private static TcHudLabel resolveTownCenterHudLabel(String dim, VoxelChunkKey vc, UUID playerId, long gameTime) {
        TownCenterTracker tracker = CivilServices.getTownCenterTracker();
        if (tracker == null || !tracker.isInitialized()) {
            return null;
        }
        TownCenterEntry best = null;
        for (TownCenterEntry entry : tracker.entriesCoveringVc(dim, vc)) {
            if (!entry.isGameplayActive(gameTime)) {
                continue;
            }
            if (normalizedTownCenterName(entry).isEmpty()) {
                continue;
            }
            if (best == null || compareTownCentersForHud(entry, best, playerId) > 0) {
                best = entry;
            }
        }
        if (best == null) {
            return null;
        }
        return new TcHudLabel(townCenterKey(dim, best), normalizedTownCenterName(best));
    }

    private static int compareTownCentersForHud(TownCenterEntry a, TownCenterEntry b, UUID playerId) {
        int cmp = Boolean.compare(a.hasBenefit(playerId), b.hasBenefit(playerId));
        if (cmp != 0) return cmp;
        cmp = Integer.compare(a.level(), b.level());
        if (cmp != 0) return cmp;
        cmp = Integer.compare(a.appliedEffects().size(), b.appliedEffects().size());
        if (cmp != 0) return cmp;
        cmp = Integer.compare(sumChosenAtLevel(a), sumChosenAtLevel(b));
        if (cmp != 0) return cmp;
        cmp = Integer.compare(sumAmplifier(a), sumAmplifier(b));
        if (cmp != 0) return cmp;
        return townCenterPosKey(b).compareTo(townCenterPosKey(a));
    }

    private static String normalizedTownCenterName(TownCenterEntry entry) {
        String name = entry.displayName();
        return name == null ? "" : name.trim();
    }

    private static int sumChosenAtLevel(TownCenterEntry entry) {
        int sum = 0;
        for (var fx : entry.appliedEffects()) {
            sum += fx.chosenAtLevel();
        }
        return sum;
    }

    private static int sumAmplifier(TownCenterEntry entry) {
        int sum = 0;
        for (var fx : entry.appliedEffects()) {
            sum += fx.amplifier();
        }
        return sum;
    }

    private static String townCenterKey(String dim, TownCenterEntry entry) {
        return dim + "|" + townCenterPosKey(entry);
    }

    private static String townCenterPosKey(TownCenterEntry entry) {
        return entry.x() + "|" + entry.y() + "|" + entry.z();
    }

    public void consumePendingRestores(MinecraftServer server) {
    }

    public void removePlayer(UUID playerId) {
        UUID pid = Objects.requireNonNull(playerId, "playerId");
        this.playerStates.remove(pid);
        this.resultQueuesByPlayer.remove(pid);
        this.rrPlayers.remove(pid);
        this.activePlayersThisEpoch.remove(pid);
        if (this.rrCursor >= this.rrPlayers.size()) {
            this.rrCursor = 0;
        }
        this.resultReceipts.keySet().removeIf(k -> k.playerId().equals(playerId));
    }

    public void clear() {
        this.playerStates.clear();
        this.resultQueuesByPlayer.clear();
        this.rrPlayers.clear();
        this.resultDedupe.clear();
        this.resultReceipts.clear();
        this.activePlayersThisEpoch.clear();
        this.activeEpoch = Long.MIN_VALUE;
        this.rrCursor = 0;
        this.worldSessionId++;
    }

    public int getPendingQueueSize() {
        int total = 0;
        for (ArrayDeque<PrefetchTask> q : this.resultQueuesByPlayer.values()) {
            total += q.size();
        }
        return total;
    }

    private static final class PlayerState {
        VoxelChunkKey lastSeenVC;
        VoxelChunkKey lastHudVc;
        BlockPos lastTickBlockPos;
        long lastResultEnqueueSec;
        long lastMovementSec;
        String lastDim;
        ZoneSemanticState zoneState;
        ZoneSemanticState baseState;
        ZoneSemanticState fastOverrideState;
        String lastHudTcKey;
        String lastHudTcLabel;
        boolean zoneInitialized;
    }

    private static final class EnqueueStats {
        int produced;
        int trimmed;
    }

    private static final class BudgetPlan {
        final Map<UUID, Integer> quotaByPlayer;
        final int activePlayers;
        final int baseShare;
        final int bonusPlayers;

        BudgetPlan(Map<UUID, Integer> quotaByPlayer, int activePlayers, int baseShare, int bonusPlayers) {
            this.quotaByPlayer = quotaByPlayer;
            this.activePlayers = activePlayers;
            this.baseShare = baseShare;
            this.bonusPlayers = bonusPlayers;
        }
    }

    private static final class ConsumeStats {
        int consumed;
        int dropped;
    }

    private static final class ReceiptFlushStats {
        int flushed;
        int transitions;
    }

    private record PrefetchTask(long worldSessionId, long epoch, String dim, VoxelChunkKey vc, UUID playerId,
                                boolean centerSample, String dedupeToken) {}

    private record PlayerEpochKey(UUID playerId, long epoch) {}

    private static final class ResultReceiptAgg {
        int sampleCount;
        /** Samples with score &gt;= mid + (1-mid)*ratio; see {@link CivilConfig#zoneReceiptStrongCivilizedRatio}. */
        int civilizedCount;
        boolean centerSeen;
        boolean centerCivilized;
        String centerDim;
        VoxelChunkKey centerVc;
    }

    private record TcHudLabel(String key, String label) {}
}
