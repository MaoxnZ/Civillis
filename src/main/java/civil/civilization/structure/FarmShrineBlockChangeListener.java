package civil.civilization.structure;

import civil.CivilServices;
import civil.civilization.FarmShrineTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Farm shrine anchor lifecycle: crying obsidian below removed, soul campfire removed/replaced,
 * or soul campfire re-lit at a registered anchor (deactivate).
 */
public final class FarmShrineBlockChangeListener implements StructureBlockChangeListener {

    @Override
    public void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        FarmShrineTracker tracker = CivilServices.getFarmShrineTracker();
        if (tracker == null || !tracker.isInitialized()) {
            return;
        }

        String dim = level.dimension().identifier().toString();
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        if (oldState.is(Blocks.SOUL_CAMPFIRE) && newState.is(Blocks.SOUL_CAMPFIRE)) {
            boolean wasLit = oldState.getValue(CampfireBlock.LIT);
            boolean nowLit = newState.getValue(CampfireBlock.LIT);
            if (!wasLit && nowLit && tracker.isShrineAt(dim, x, y, z)) {
                tracker.onShrineRemoved(dim, x, y, z);
                playShrineDeactivated(level, pos);
            }
            return;
        }

        if (oldState.is(Blocks.SOUL_CAMPFIRE) && !newState.is(Blocks.SOUL_CAMPFIRE) && tracker.isShrineAt(dim, x, y, z)) {
            tracker.onShrineRemoved(dim, x, y, z);
            playShrineDeactivated(level, pos);
            return;
        }

        if (oldState.is(Blocks.CRYING_OBSIDIAN) && !newState.is(Blocks.CRYING_OBSIDIAN)) {
            BlockPos above = pos.above();
            if (tracker.isShrineAt(dim, above.getX(), above.getY(), above.getZ())) {
                tracker.onShrineRemoved(dim, above.getX(), above.getY(), above.getZ());
                playShrineDeactivated(level, above);
            }
        }
    }

    private static final long DEACTIVATE_MELODY_STEP1_MS = 300L;
    private static final long DEACTIVATE_MELODY_STEP2_MS = 500L;

    private static void playShrineDeactivated(ServerLevel level, BlockPos soundPos) {
        BlockPos pos = soundPos.immutable();
        MinecraftServer server = level.getServer();
        float root = 1.0f;
        float minorThird = (float) Math.pow(2.0, 3.0 / 12.0);
        float fifth = (float) Math.pow(2.0, 7.0 / 12.0);

        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.38f, root);
        level.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.44f, 1.02f);

        Executor pool = ForkJoinPool.commonPool();
        CompletableFuture.runAsync(
                () -> server.execute(() -> level.playSound(
                        null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.34f, root * minorThird)),
                CompletableFuture.delayedExecutor(DEACTIVATE_MELODY_STEP1_MS, TimeUnit.MILLISECONDS, pool));
        CompletableFuture.runAsync(
                () -> server.execute(() -> level.playSound(
                        null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.26f, root * fifth)),
                CompletableFuture.delayedExecutor(
                        DEACTIVATE_MELODY_STEP1_MS + DEACTIVATE_MELODY_STEP2_MS, TimeUnit.MILLISECONDS, pool));
    }
}
