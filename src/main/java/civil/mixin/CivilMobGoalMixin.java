package civil.mixin;

import civil.mob.FleeCivilizationGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects civilization flee goals into all hostile {@link PathAwareEntity} mobs.
 *
 * <p>Two goal instances per mob:
 * <ul>
 *   <li>IDLE (priority 5): flee when idle in civilized area, below attack goals</li>
 *   <li>COMBAT_PANIC (priority 1): panic during combat when civilization is extreme,
 *       above attack goals — can interrupt fighting</li>
 * </ul>
 *
 * <p>Server-side only. Goals are dormant ({@code canStart()=false}) outside
 * civilized areas, adding negligible overhead (~10ns per tick per goal for
 * the throttle check).
 */
@Mixin(MobEntity.class)
public abstract class CivilMobGoalMixin {

    @Shadow @Final protected GoalSelector goalSelector;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void civil$injectFleeGoals(EntityType<?> type, World world, CallbackInfo ci) {
        if (type.getSpawnGroup() == SpawnGroup.MONSTER
                && (Object) this instanceof PathAwareEntity pae
                && world instanceof ServerWorld) {
            this.goalSelector.add(5, new FleeCivilizationGoal(pae, FleeCivilizationGoal.Mode.IDLE));
            this.goalSelector.add(1, new FleeCivilizationGoal(pae, FleeCivilizationGoal.Mode.COMBAT_PANIC));
        }
    }
}
