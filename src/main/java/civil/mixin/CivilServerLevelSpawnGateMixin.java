package civil.mixin;

import civil.CivilMod;
import civil.registry.SpawnGateEntityRegistry;
import civil.spawn.SpawnDecision;
import civil.spawn.SpawnPolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Natural hostile spawn gate: block, allow, or {@link SpawnDecision#BRANCH_SHRINE_NEARBY} conversion
 * per {@link SpawnPolicy}.
 *
 * <p>SHRINE_NEARBY: non-empty {@code headTypes} and {@code nearbyHeadCount > 0} → roll
 * {@code min(1, nearbyHeadCount/10)}; on success replace from pool (monster, uniform pick).
 *
 * <p><b>Forge 双轨</b>：与 {@code private/port/mc1.20.1} 一致 —— Mojmap
 * {@code addFreshEntity(Entity)} 与 SRG {@code m_7967_}，{@code require = 0}。
 */
@Mixin(ServerLevel.class)
public abstract class CivilServerLevelSpawnGateMixin {

    @Inject(
            method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void civil$gateHostileSpawnsMoj(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        civil$gateHostileSpawns(entity, cir);
    }

    @Inject(
            method = "m_7967_(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void civil$gateHostileSpawnsSrg(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        civil$gateHostileSpawns(entity, cir);
    }

    private void civil$gateHostileSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) {
            return;
        }
        EntityType<?> type = entity.getType();
        boolean gated = type.getCategory() == MobCategory.MONSTER || SpawnGateEntityRegistry.isBlacklist(type);
        if (!gated) {
            return;
        }

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

        // SHRINE_NEARBY: P(convert) = min(1, nearbyHeadCount/10) when convert pool non-empty
        if (SpawnDecision.BRANCH_SHRINE_NEARBY.equals(decision.branch())) {
            int headCount = decision.nearbyHeadCount();
            List<EntityType<?>> convertPool = decision.headTypes();

            if (headCount > 0 && convertPool != null && !convertPool.isEmpty()) {
                double convertProbability = Math.min(1.0, headCount / 10.0);

                if (world.getRandom().nextDouble() < convertProbability) {
                    EntityType<?> chosen = convertPool.get(world.getRandom().nextInt(convertPool.size()));
                    if (chosen != null && chosen != entity.getType() && chosen.getCategory() == MobCategory.MONSTER) {
                        CivilMod.NATURAL_SPAWN_CONTEXT.set(false);
                        Entity replacement;
                        try {
                            replacement = chosen.spawn(world, pos, MobSpawnType.NATURAL);
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
                                        "[civil] allow spawn pos=({}, {}, {}) branch=SHRINE_NEARBY convert {} -> {} (heads={} prob={})",
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
