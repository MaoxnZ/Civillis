package civil.mixin;

import civil.CivilServices;
import civil.civilization.cache.CivilizationCache;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Listens to world block changes, marks corresponding voxel chunks as "dirty" in civilization cache.
 * All block operations (placement, destruction, replacement) go through {@link Level#setBlock}, so monster head placement/removal also triggers.
 * Only effective on server side; after marking as dirty, lazy recalculation (lazy loading) occurs on next access (spawn/detector).
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
        CivilizationCache cache = CivilServices.getCivilizationCache();
        if (cache != null) {
            cache.markChunkDirtyAt(serverWorld, pos);
        }
    }
}
