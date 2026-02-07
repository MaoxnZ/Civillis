package civil.mixin;

import civil.CivilMod;
import civil.spawn.SpawnDecision;
import civil.spawn.SpawnPolicy;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Performs civilization judgment when entity actually joins world: block / allow / HEAD_ANY probabilistically "replace with neighborhood head corresponding mob".
 *
 * <p>HEAD_MATCH: Current mob matches neighborhood head type, directly allow.
 * <p>HEAD_ANY: Neighborhood has any monster head; conversion probability linearly determined by head count ({@link #HEAD_COUNT_FOR_FULL_CONVERT} heads = probability 1.0),
 * which mob to convert to is randomly sampled from headTypes list weighted by repetition count (heads that appear more often correspond to mobs more likely to be selected).
 */
@Mixin(ServerWorld.class)
public abstract class CivilServerLevelSpawnGateMixin {

    /** When head count in neighborhood reaches this value, conversion probability is 1.0; less than this grows linearly (e.g., 1 head=0.2, 5 heads=1.0). */
    private static final int HEAD_COUNT_FOR_FULL_CONVERT = 5;

    @Inject(
            method = "spawnEntity(Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void civil$gateHostileSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null || entity.getType().getSpawnGroup() != SpawnGroup.MONSTER) {
            return;
        }

        ServerWorld world = (ServerWorld) (Object) this;
        BlockPos pos = entity.getBlockPos();

        SpawnDecision decision = SpawnPolicy.decide(world, pos, entity.getType());

        if (decision.block()) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info(
                        "[civil] block spawn pos=({}, {}, {}) score={} branch={}",
                        pos.getX(), pos.getY(), pos.getZ(),
                        String.format("%.2f", decision.score()),
                        decision.branch()
                );
            }
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }

        // HEAD_ANY: conversion probability = head count linear growth, full HEAD_COUNT_FOR_FULL_CONVERT heads = 1.0; which mob to convert to sampled from headTypes weighted
        if ("HEAD_ANY".equals(decision.branch())) {
            List<EntityType<?>> headTypes = decision.headTypes();
            if (headTypes != null && !headTypes.isEmpty()) {
                int headCount = headTypes.size();
                double convertProbability = Math.min(1.0, (double) headCount / HEAD_COUNT_FOR_FULL_CONVERT);
                if (world.getRandom().nextDouble() < convertProbability) {
                    EntityType<?> chosen = headTypes.get(world.getRandom().nextInt(headTypes.size()));
                    if (chosen != null && chosen != entity.getType() && chosen.getSpawnGroup() == SpawnGroup.MONSTER) {
                        Entity replacement = chosen.spawn(world, pos, SpawnReason.NATURAL);
                        if (replacement != null) {
                            replacement.setYaw(entity.getYaw());
                            replacement.setPitch(entity.getPitch());
                            cir.setReturnValue(false);
                            cir.cancel();
                            if (CivilMod.DEBUG) {
                                CivilMod.LOGGER.info(
                                        "[civil] allow spawn pos=({}, {}, {}) score={} branch=HEAD_ANY convert to {}",
                                        pos.getX(), pos.getY(), pos.getZ(),
                                        String.format("%.2f", decision.score()),
                                        chosen.toString()
                                );
                            }
                            return;
                        }
                    }
                }
            }
        }

        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info(
                    "[civil] allow spawn pos=({}, {}, {}) score={} branch={}",
                    pos.getX(), pos.getY(), pos.getZ(),
                    String.format("%.2f", decision.score()),
                    decision.branch()
            );
        }
    }
}
