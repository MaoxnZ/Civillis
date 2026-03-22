package civil.civilization.cache;

import civil.CivilMod;
import civil.CivilPlatform;
import civil.CivilServices;
import civil.config.CivilConfig;
import civil.civilization.ServerClock;
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
    private static final long STATIC_ENQUEUE_SEC = 30L;

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
                long intervalSec = moved ? MOVING_ENQUEUE_SEC : STATIC_ENQUEUE_SEC;
                if (state.lastResultEnqueueSec == 0L || nowSec - state.lastResultEnqueueSec >= intervalSec) {
                    this.enqueueArea(currentEpoch, dim, center, playerId,
                            resultRadiusX, resultRadiusZ, resultRadiusY, this.resultDedupe);
                    state.lastResultEnqueueSec = nowSec;
                    this.activePlayersThisEpoch.add(playerId);
                }
                this.applyFastCautionTransition(server, currentEpoch, world, dim, center, state, playerId, dimChanged);
                state.lastSeenVC = center;
                state.lastDim = dim;
            }
        }
        this.consumeResultQueue(currentEpoch, worldByDim, resultCache, civilizationService,
                CivilConfig.resultBudgetPerTick, CivilConfig.resultEpochTtlSec);
        this.flushResultReceipts(currentEpoch, server);
    }

    private int applyFastCautionTransition(MinecraftServer server, long epoch, ServerLevel world, String dim,
                                           VoxelChunkKey center, PlayerState state, UUID playerId, boolean dimChanged) {
        ZoneSemanticState newState;
        boolean vcChangedForHud = dimChanged || state.lastHudVc == null || !state.lastHudVc.equals(center);
        if (!vcChangedForHud) {
            return 0;
        }
        boolean cautionNow = false;
        ZonePolicyService zonePolicyService = CivilServices.getZonePolicyService();
        if (zonePolicyService != null) {
            cautionNow = zonePolicyService.treatAsNonCivilized(world, center);
        }
        state.fastCaution = cautionNow;
        state.lastHudVc = center;
        if (!state.zoneInitialized) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[zone][fast] skip send: zone not initialized yet player={} dim={} vc={} cautionNow={}",
                        playerId, dim, center, cautionNow);
            }
            return 0;
        }
        ZoneSemanticState oldState = state.zoneState != null ? state.zoneState : ZoneSemanticState.WILDERNESS;
        ZoneSemanticState baseState = state.baseState != null ? state.baseState : ZoneSemanticState.WILDERNESS;
        state.zoneState = newState = cautionNow ? ZoneSemanticState.CAUTION : baseState;
        if (newState == oldState) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[zone][fast] no state change player={} epoch={} state={} cautionNow={} base={}",
                        playerId, epoch, newState, cautionNow, baseState);
            }
            return 0;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.warn("[zone][fast] skip send: player offline playerId={}", playerId);
            }
            return 0;
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[zone][fast] SEND epoch={} {} -> {} (cautionNow={} base={}) player={}",
                    epoch, oldState, newState, cautionNow, baseState, playerId);
        }
        CivilPlatform.sendToPlayer(player, new ZoneTransitionPayload(epoch, newState.id()));
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
        boolean civilized = score >= CivilConfig.spawnThresholdMid;
        UUID playerId = Objects.requireNonNull(task.playerId());
        PlayerEpochKey key = new PlayerEpochKey(playerId, task.epoch());
        ResultReceiptAgg agg = this.resultReceipts.computeIfAbsent(key, unused -> new ResultReceiptAgg());
        agg.sampleCount++;
        if (civilized) {
            agg.civilizedCount++;
        }
        if (task.centerSample()) {
            agg.centerSeen = true;
            agg.centerCivilized = civilized;
            agg.centerDim = task.dim();
            agg.centerVc = task.vc();
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
                boolean inCautionByZonePolicy = false;
                ZonePolicyService zonePolicyService = CivilServices.getZonePolicyService();
                ServerLevel centerWorld;
                if (!state.zoneInitialized && zonePolicyService != null && agg.centerDim != null && agg.centerVc != null
                        && (centerWorld = worldByDim.get(agg.centerDim)) != null) {
                    inCautionByZonePolicy = zonePolicyService.treatAsNonCivilized(centerWorld, agg.centerVc);
                }
                ZoneSemanticState oldState = state.zoneState != null ? state.zoneState : ZoneSemanticState.WILDERNESS;
                ZoneSemanticState oldBase = state.baseState != null ? state.baseState : ZoneSemanticState.WILDERNESS;
                boolean wasCivilized = oldBase == ZoneSemanticState.CIVILIZED;
                ZoneSemanticState candidateBase = !wasCivilized && enter ? ZoneSemanticState.CIVILIZED
                        : wasCivilized && leave ? ZoneSemanticState.WILDERNESS : oldBase;
                state.baseState = candidateBase;
                boolean cautionForCompose = state.zoneInitialized ? state.fastCaution : inCautionByZonePolicy;
                ZoneSemanticState newState = cautionForCompose ? ZoneSemanticState.CAUTION : candidateBase;
                if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.info(
                            "[zone][receipt] keyEpoch={} currentEpoch={} player={} samples={} civCount={} centerCiv={} enter={} leave={} enterTh={} leaveTh={} wasCivBase={} candidateBase={} cautionCompose={} newState={} oldState={} zoneInit={}",
                            key.epoch(), currentEpoch, playerId, agg.sampleCount, agg.civilizedCount, agg.centerCivilized,
                            enter, leave, enterThreshold, leaveThreshold, wasCivilized, candidateBase, cautionForCompose,
                            newState, oldState, state.zoneInitialized);
                }
                if (!state.zoneInitialized) {
                    state.zoneState = newState;
                    state.fastCaution = inCautionByZonePolicy;
                    state.zoneInitialized = true;
                    if (CivilMod.DEBUG) {
                        CivilMod.LOGGER.info("[zone][receipt] first init (no HUD send) newState={} inCautionPolicy={} player={}",
                                newState, inCautionByZonePolicy, playerId);
                    }
                    it.remove();
                    continue;
                }
                state.zoneState = newState;
                if (newState != oldState) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        if (CivilMod.DEBUG) {
                            CivilMod.LOGGER.info("[zone][receipt] SEND receiptEpoch={} currentEpoch={} {} -> {} player={}",
                                    key.epoch(), currentEpoch, oldState, newState, playerId);
                        }
                        // Window id (receipt epoch), not wall-clock second — matches CFR / design.
                        CivilPlatform.sendToPlayer(player, new ZoneTransitionPayload(key.epoch(), newState.id()));
                        stats.transitions++;
                    }
                } else if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.info("[zone][receipt] no send: newState==oldState ({}) player={}", newState, playerId);
                }
            } else if (CivilMod.DEBUG && agg.sampleCount > 0 && !agg.centerSeen && debugNoCenterLogsLeft-- > 0) {
                CivilMod.LOGGER.info("[zone][receipt] waiting center sample: keyEpoch={} samples={} civCount={}",
                        key.epoch(), agg.sampleCount, agg.civilizedCount);
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
        long lastResultEnqueueSec;
        String lastDim;
        ZoneSemanticState zoneState;
        ZoneSemanticState baseState;
        boolean fastCaution;
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
        int civilizedCount;
        boolean centerSeen;
        boolean centerCivilized;
        String centerDim;
        VoxelChunkKey centerVc;
    }
}
