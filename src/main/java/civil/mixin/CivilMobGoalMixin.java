package civil.mixin;

import civil.mob.FleeCivilizationGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects civilization flee goals into all hostile {@link PathfinderMob} mobs.
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
@Mixin(Mob.class)
public abstract class CivilMobGoalMixin {

    @Shadow @Final protected GoalSelector goalSelector;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void civil$injectFleeGoals(EntityType<?> type, Level world, CallbackInfo ci) {
        if (type.getCategory() == MobCategory.MONSTER
                && (Object) this instanceof PathfinderMob pae
                && world instanceof ServerLevel) {
            this.goalSelector.addGoal(5, new FleeCivilizationGoal(pae, FleeCivilizationGoal.Mode.IDLE));
            this.goalSelector.addGoal(1, new FleeCivilizationGoal(pae, FleeCivilizationGoal.Mode.COMBAT_PANIC));
        }
    }
}
