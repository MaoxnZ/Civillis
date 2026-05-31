package civil.towncenter;

import civil.CivilPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/** Server-side town center interaction sounds (vanilla events only). */
final class TownCenterSounds {

    private static final long ASCEND_CHIME_STEP_MS = 300L;
    private static final long START_CHIME_STEP2_MS = 250L;
    private static final long START_CHIME_STEP3_MS = 450L;

    private TownCenterSounds() {
    }

    static void playBeaconActivate(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    private static final double ACTIVATION_BURST_NOTIFY_DIST_SQ = 48.0 * 48.0;

    /** 首次手持绿宝石激活：信标 + 上扬二连 chime + 客户端激波粒子。 */
    static void playFirstEmeraldActivate(ServerLevel level, BlockPos pos) {
        playBeaconActivate(level, pos);
        playAscendChimeDouble(level, pos);
        broadcastActivationBurst(level, pos);
    }

    private static void broadcastActivationBurst(ServerLevel level, BlockPos lecternPos) {
        TownCenterActivationBurstPayload payload =
                new TownCenterActivationBurstPayload(lecternPos.getX(), lecternPos.getY(), lecternPos.getZ());
        String dim = level.dimension().identifier().toString();
        double ax = lecternPos.getX() + 0.5;
        double ay = lecternPos.getY() + 0.5;
        double az = lecternPos.getZ() + 0.5;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (!player.level().dimension().identifier().toString().equals(dim)) {
                continue;
            }
            if (player.distanceToSqr(ax, ay, az) > ACTIVATION_BURST_NOTIFY_DIST_SQ) {
                continue;
            }
            CivilPlatform.sendToPlayer(player, payload);
        }
    }

    static void playActivationFail(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.5f, 0.8f);
    }

    static void playBeaconDeactivate(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.85f);
    }

    static void playShutdownFinalize(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    static void playUpgradeSuccess(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0f, 1.02f);
    }

    /** 五度上扬二连 chime（取消倒计时 / GUI 再激活 / 首次激活）。 */
    static void playAscendChimeDouble(ServerLevel level, BlockPos soundPos) {
        BlockPos pos = soundPos.immutable();
        MinecraftServer server = level.getServer();
        float root = 1.0f;
        float fifth = (float) Math.pow(2.0, 7.0 / 12.0);

        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.38f, root);

        Executor pool = ForkJoinPool.commonPool();
        CompletableFuture.runAsync(
                () -> server.execute(() -> level.playSound(
                        null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.34f, root * fifth)),
                CompletableFuture.delayedExecutor(ASCEND_CHIME_STEP_MS, TimeUnit.MILLISECONDS, pool));
    }

    /** 小三度向下三连 chime（开始关激活倒计时）。 */
    static void playShutdownStartChimeTriple(ServerLevel level, BlockPos soundPos) {
        BlockPos pos = soundPos.immutable();
        MinecraftServer server = level.getServer();
        float root = 1.0f;
        float minorThirdDown = 1f / (float) Math.pow(2.0, 3.0 / 12.0);
        float p2 = root * minorThirdDown;
        float p3 = p2 * minorThirdDown;

        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.40f, root);

        Executor pool = ForkJoinPool.commonPool();
        CompletableFuture.runAsync(
                () -> server.execute(() -> level.playSound(
                        null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.36f, p2)),
                CompletableFuture.delayedExecutor(START_CHIME_STEP2_MS, TimeUnit.MILLISECONDS, pool));
        CompletableFuture.runAsync(
                () -> server.execute(() -> level.playSound(
                        null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.30f, p3)),
                CompletableFuture.delayedExecutor(
                        START_CHIME_STEP2_MS + START_CHIME_STEP3_MS, TimeUnit.MILLISECONDS, pool));
    }
}
