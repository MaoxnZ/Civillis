package civil.civilization.operator;

import civil.config.CivilConfig;
import civil.civilization.structure.VoxelRegion;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;

/**
 * Baseline civilization operator: single pass through region, outputs civilization score [0,1]
 * and writes monster head types to context.
 *
 * <p>Monster heads do NOT inflate the civilization score; their influence is orthogonal
 * and flows through {@link CivilComputeContext#getHeadTypes()} instead.
 * Block weight judgment is delegated to {@link BlockCivilization}.
 */
public final class SimpleCivilizationOperator implements CivilizationOperator {

    // Normalization factor is in CivilConfig.normalizationFactor

    @Override
    public double computeScore(VoxelRegion region) {
        return computeScore(region, null);
    }

    @Override
    public double computeScore(VoxelRegion region, CivilComputeContext context) {
        BlockPos min = region.getMin();
        BlockPos max = region.getMax();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        double totalWeight = 0.0;

        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    pos.set(x, y, z);
                    BlockState state = region.getBlock(pos);

                    totalWeight += BlockCivilization.getBlockWeight(state.getBlock());

                    if (context != null && BlockCivilization.isMonsterHead(state)
                            && state.getBlock() instanceof AbstractSkullBlock skull) {
                        EntityType<?> et = BlockCivilization.skullTypeToEntityType(skull.getSkullType());
                        if (et != null) context.addHeadType(et);
                    }
                }
            }
        }

        // Head types are already written to context; no need for a sentinel score.
        // Return the actual civilization score regardless of whether heads exist.
        if (totalWeight <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, totalWeight / CivilConfig.normalizationFactor);
    }
}
