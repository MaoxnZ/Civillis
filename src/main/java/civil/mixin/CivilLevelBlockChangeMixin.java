package civil.mixin;

import civil.CivilServices;
import civil.civilization.BlockScanner;
import civil.civilization.HeadTracker;
import civil.civilization.structure.StructureBlockChangeListener;
import civil.civilization.structure.StructureBlockChangeListeners;
import civil.civilization.scoring.ScalableCivilizationService;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Block-change hook for civilization + heads + structure listeners.
 *
 * <p><b>Forge 双轨（与 {@code remotes/private/port/mc1.20.1} 一致）</b>：Mojmap 全描述符
 * 与 SRG（{@code remap = false}）各挂一套，{@code require = 0} 允许在某一轨不存在时静默跳过。
 */
@Mixin(Level.class)
public abstract class CivilLevelBlockChangeMixin {

    /**
     * Per-thread stack of old states at {@code setBlock} HEAD; nested calls must not share one slot.
     */
    @Unique
    private static final ThreadLocal<Deque<BlockState>> civil$oldStateStack =
            ThreadLocal.withInitial(ArrayDeque::new);

    // --- capture old state (HEAD): Mojmap 3-arg / 4-arg + SRG 3-arg / 4-arg ---

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            at = @At("HEAD"),
            require = 0
    )
    private void civil$captureOldStateMojV1(BlockPos pos, BlockState state, int flags,
            CallbackInfoReturnable<Boolean> cir) {
        civil$captureOldStateCore(pos);
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            require = 0
    )
    private void civil$captureOldStateMojV2(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir) {
        civil$captureOldStateCore(pos);
    }

    @Inject(
            method = "m_7731_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void civil$captureOldStateSrgV1(BlockPos pos, BlockState state, int flags,
            CallbackInfoReturnable<Boolean> cir) {
        civil$captureOldStateCore(pos);
    }

    @Inject(
            method = "m_6933_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void civil$captureOldStateSrgV2(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir) {
        civil$captureOldStateCore(pos);
    }

    @Unique
    @SuppressWarnings("null")
    private void civil$captureOldStateCore(BlockPos pos) {
        BlockPos safePos = Objects.requireNonNull(pos, "pos");
        if ((Object) this instanceof ServerLevel) {
            civil$oldStateStack.get().push(((Level) (Object) this).getBlockState(safePos));
        }
    }

    // --- after setBlock (RETURN): Mojmap + SRG ---

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            at = @At("RETURN"),
            require = 0
    )
    private void civil$onBlockSetMojV1(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        civil$afterSetBlock(pos, state, cir);
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"),
            require = 0
    )
    private void civil$onBlockSetMojV2(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir) {
        civil$afterSetBlock(pos, state, cir);
    }

    @Inject(
            method = "m_7731_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void civil$onBlockSetSrgV1(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        civil$afterSetBlock(pos, state, cir);
    }

    @Inject(
            method = "m_6933_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void civil$onBlockSetSrgV2(BlockPos pos, BlockState state, int flags, int maxUpdateDepth,
            CallbackInfoReturnable<Boolean> cir) {
        civil$afterSetBlock(pos, state, cir);
    }

    @Unique
    private void civil$afterSetBlock(BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerLevel serverWorld)) {
            return;
        }

        Deque<BlockState> stack = civil$oldStateStack.get();
        BlockState oldState = stack.isEmpty() ? null : stack.pop();
        if (stack.isEmpty()) {
            civil$oldStateStack.remove();
        }

        if (!cir.getReturnValueZ()) {
            return;
        }

        boolean isNowHead = BlockScanner.isSkullBlock(state);

        ScalableCivilizationService scalableService = CivilServices.getScalableService();
        boolean oldIsCivil = oldState != null && BlockScanner.isTargetBlock(oldState);
        boolean newIsCivil = BlockScanner.isTargetBlock(state);
        if (scalableService != null && (oldIsCivil || newIsCivil)) {
            scalableService.onCivilBlockChanged(serverWorld, pos);
        }

        HeadTracker registry = CivilServices.getHeadTracker();
        if (registry != null && registry.isInitialized()) {
            String dim = serverWorld.dimension().location().toString();

            if (isNowHead) {
                AbstractSkullBlock skull = (AbstractSkullBlock) state.getBlock();
                String skullType = skull.getType().toString();
                registry.onHeadAdded(dim, pos.getX(), pos.getY(), pos.getZ(), skullType);
            } else {
                registry.onHeadRemoved(dim, pos.getX(), pos.getY(), pos.getZ());
            }
        }

        if (oldState != null) {
            for (StructureBlockChangeListener listener : StructureBlockChangeListeners.LISTENERS) {
                listener.onBlockChanged(serverWorld, pos, oldState, state);
            }
        }
    }
}
