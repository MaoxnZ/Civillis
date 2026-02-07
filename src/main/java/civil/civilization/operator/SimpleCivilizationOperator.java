package civil.civilization.operator;

import civil.CivilMod;
import civil.civilization.CivilValues;
import civil.civilization.structure.VoxelRegion;
import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Baseline civilization operator implementation: single pass through region, outputs civilization score and writes to context (head types).
 * If any monster head exists, returns {@link CivilValues#FORCE_ALLOW_SCORE} and writes head types to context;
 * otherwise returns normalized block weights [0,1]. Block judgment is delegated to {@link BlockCivilization}, normalization is defined in this class.
 */
public final class SimpleCivilizationOperator implements CivilizationOperator {

    private static final Logger OPERATOR_LOG = LoggerFactory.getLogger("civil-operator");
    private static final double NORMALIZATION_FACTOR = 5.0;

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
        boolean anyMonsterHead = false;
        int visited = 0;  // Number of blocks visited (computation cost)

        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    pos.set(x, y, z);
                    BlockState state = region.getBlock(pos);
                    visited++;

                    totalWeight += BlockCivilization.getBlockWeight(state.getBlock());

                    if (BlockCivilization.isMonsterHead(state)) {
                        anyMonsterHead = true;
                        if (context != null && state.getBlock() instanceof AbstractSkullBlock skull) {
                            EntityType<?> et = BlockCivilization.skullTypeToEntityType(skull.getSkullType());
                            if (et != null) context.addHeadType(et);
                        }
                    }
                }
            }
        }

        // Output operator log
        if (CivilMod.DEBUG) {
            int cx = min.getX() >> 4;
            int cz = min.getZ() >> 4;
            int sy = Math.floorDiv(min.getY(), 16);
            OPERATOR_LOG.info("[civil] operator_visited {} {} {} {}", cx, cz, sy, visited);
        }

        if (anyMonsterHead) {
            return CivilValues.FORCE_ALLOW_SCORE;
        }
        if (totalWeight <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, totalWeight / NORMALIZATION_FACTOR);
    }
}
