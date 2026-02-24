package civil.mixin;

import civil.CivilMod;
import civil.spawn.SpawnDecision;
import civil.spawn.SpawnPolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Performs civilization judgment when entity actually joins world:
 * block / allow / HEAD_NEARBY conversion.
 *
 * <p>HEAD_NEARBY conversion logic (new design):
 * <ul>
 *   <li>1-2 nearby heads: allow spawn as-is (bypass civilization suppression, no conversion).</li>
 *   <li>3+ nearby heads: conversion probability scales linearly from ~12.5% (3 heads)
 *       to 100% (10+ heads). Conversion target is weighted by placed head proportions
 *       (from the convertPool).</li>
 * </ul>
 */
@Mixin(ServerLevel.class)
public abstract class CivilServerLevelSpawnGateMixin {

    /** Minimum nearby head count to trigger conversion. Below this, heads only bypass civilization. */
    private static final int MIN_CONVERT_COUNT = 3;

    /** At this head count, conversion probability reaches 1.0. Formula: min(1, (count-2)/8). */
    private static final int HEAD_COUNT_FOR_FULL_CONVERT = 10;

    @Inject(
            method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void civil$gateHostileSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null || entity.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }

        // Only intercept natural spawns (NaturalSpawner pipeline).
        // Spawn eggs, spawners, /summon, reinforcements etc. bypass civilization checks.
        if (!CivilMod.NATURAL_SPAWN_CONTEXT.get()) {
            return;
        }

        ServerLevel world = (ServerLevel) (Object) this;
        BlockPos pos = entity.blockPosition();

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

        // HEAD_NEARBY: conversion only when >= MIN_CONVERT_COUNT heads and convertPool is non-empty
        if (SpawnDecision.BRANCH_HEAD_NEARBY.equals(decision.branch())) {
            int headCount = decision.nearbyHeadCount();
            List<EntityType<?>> convertPool = decision.headTypes();

            if (headCount >= MIN_CONVERT_COUNT && convertPool != null && !convertPool.isEmpty()) {
                double convertProbability = Math.min(1.0,
                        (double) (headCount - (MIN_CONVERT_COUNT - 1)) / (HEAD_COUNT_FOR_FULL_CONVERT - (MIN_CONVERT_COUNT - 1)));

                if (world.getRandom().nextDouble() < convertProbability) {
                    EntityType<?> chosen = convertPool.get(world.getRandom().nextInt(convertPool.size()));
                    if (chosen != null && chosen != entity.getType() && chosen.getCategory() == MobCategory.MONSTER) {
                        // Clear natural-spawn context so the replacement entity
                        // bypasses this mixin entirely (prevents recursive conversion).
                        CivilMod.NATURAL_SPAWN_CONTEXT.set(false);
                        Entity replacement;
                        try {
                            replacement = chosen.spawn(world, pos, EntitySpawnReason.NATURAL);
                        } finally {
                            CivilMod.NATURAL_SPAWN_CONTEXT.set(true);
                        }
                        if (replacement != null) {
                            replacement.setYRot(entity.getYRot());
                            replacement.setXRot(entity.getXRot());
                            cir.setReturnValue(false);
                            cir.cancel();
                            if (CivilMod.DEBUG) {
                                CivilMod.LOGGER.info(
                                        "[civil] allow spawn pos=({}, {}, {}) branch=HEAD_NEARBY convert {} -> {} (heads={} prob={})",
                                        pos.getX(), pos.getY(), pos.getZ(),
                                        entity.getType().toString(), chosen.toString(),
                                        headCount, String.format("%.3f", convertProbability)
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
