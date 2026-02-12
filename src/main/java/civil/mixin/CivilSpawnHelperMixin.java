package civil.mixin;

import civil.CivilMod;
import net.minecraft.world.SpawnHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the natural mob spawning pipeline so that the spawn gate mixin
 * ({@link CivilServerLevelSpawnGateMixin}) can distinguish natural spawns
 * from spawn eggs, spawners, commands, etc.
 *
 * <p>Only sets/clears a boolean flag on {@link CivilMod#NATURAL_SPAWN_CONTEXT};
 * no spawn logic runs here.  Cost: one {@code ThreadLocal.set()} at entry and
 * one at return — effectively zero overhead.
 */
@Mixin(SpawnHelper.class)
public abstract class CivilSpawnHelperMixin {

    @Inject(method = "spawnEntitiesInChunk", at = @At("HEAD"))
    private static void civil$onNaturalSpawnStart(CallbackInfo ci) {
        CivilMod.NATURAL_SPAWN_CONTEXT.set(Boolean.TRUE);
    }

    @Inject(method = "spawnEntitiesInChunk", at = @At("RETURN"))
    private static void civil$onNaturalSpawnEnd(CallbackInfo ci) {
        CivilMod.NATURAL_SPAWN_CONTEXT.set(Boolean.FALSE);
    }
}
