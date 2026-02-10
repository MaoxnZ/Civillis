package civil.mixin;

import civil.CivilServices;
import civil.civilization.MobHeadRegistry;
import civil.civilization.cache.CivilizationCache;
import civil.civilization.operator.BlockCivilization;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Listens to world block changes:
 * <ol>
 *   <li>Marks corresponding voxel chunks as "dirty" in civilization cache.</li>
 *   <li>Tracks monster head placement/removal in {@link MobHeadRegistry} for the
 *       head attraction system. Uses the registry itself to determine if the old
 *       block was a head (no need to capture old BlockState).</li>
 * </ol>
 *
 * All block operations (placement, destruction, replacement) go through
 * {@code World.setBlockState}, so monster head placement/removal also triggers.
 * Only effective on server side.
 */
@Mixin(World.class)
public abstract class CivilLevelBlockChangeMixin {

    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
            at = @At("RETURN")
    )
    private void civil$onBlockSet(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        if (!((Object) this instanceof ServerWorld serverWorld)) {
            return;
        }

        // Mark civilization cache dirty (existing behavior)
        CivilizationCache cache = CivilServices.getCivilizationCache();
        if (cache != null) {
            cache.markChunkDirtyAt(serverWorld, pos);
        }

        // Track head placement/removal in MobHeadRegistry.
        // Uses the registry's own containsKey to determine old state — avoids
        // needing to capture the old BlockState at HEAD injection point.
        MobHeadRegistry registry = CivilServices.getMobHeadRegistry();
        if (registry != null && registry.isInitialized()) {
            String dim = serverWorld.getRegistryKey().toString();
            boolean isNowHead = BlockCivilization.isMonsterHead(state);

            if (isNowHead) {
                // Head placed: register (idempotent, no-op if already known)
                AbstractSkullBlock skull = (AbstractSkullBlock) state.getBlock();
                String skullType = skull.getSkullType().toString();
                registry.onHeadAdded(dim, pos.getX(), pos.getY(), pos.getZ(), skullType);
            } else {
                // Non-head block set: remove head if one was here (no-op if none)
                registry.onHeadRemoved(dim, pos.getX(), pos.getY(), pos.getZ());
            }
        }
    }
}
