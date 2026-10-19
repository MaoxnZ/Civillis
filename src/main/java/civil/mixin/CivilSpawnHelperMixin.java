package civil.mixin;

import civil.CivilMod;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the natural mob spawning pipeline so that the spawn gate mixin
 * ({@link CivilServerLevelSpawnGateMixin}) can distinguish natural spawns
 * from spawn eggs, spawners, commands, etc.
 *
 * <p><b>Forge 双轨</b>：与 {@code private/port/mc1.20.1} 一致 —— Mojmap {@code spawnForChunk}
 * 与 SRG {@code m_47029_}，{@code require = 0}。
 */
@Mixin(NaturalSpawner.class)
public abstract class CivilSpawnHelperMixin {

    @Inject(method = "spawnForChunk", at = @At("HEAD"), require = 0)
    private static void civil$onNaturalSpawnStartMoj(CallbackInfo ci) {
        civil$onNaturalSpawnStart(ci);
    }

    @Inject(method = "m_47029_", at = @At("HEAD"), require = 0, remap = false)
    private static void civil$onNaturalSpawnStartSrg(CallbackInfo ci) {
        civil$onNaturalSpawnStart(ci);
    }

    private static void civil$onNaturalSpawnStart(CallbackInfo ci) {
        CivilMod.NATURAL_SPAWN_CONTEXT.set(Boolean.TRUE);
    }

    @Inject(method = "spawnForChunk", at = @At("RETURN"), require = 0)
    private static void civil$onNaturalSpawnEndMoj(CallbackInfo ci) {
        civil$onNaturalSpawnEnd(ci);
    }

    @Inject(method = "m_47029_", at = @At("RETURN"), require = 0, remap = false)
    private static void civil$onNaturalSpawnEndSrg(CallbackInfo ci) {
        civil$onNaturalSpawnEnd(ci);
    }

    private static void civil$onNaturalSpawnEnd(CallbackInfo ci) {
        CivilMod.NATURAL_SPAWN_CONTEXT.set(Boolean.FALSE);
    }
}
