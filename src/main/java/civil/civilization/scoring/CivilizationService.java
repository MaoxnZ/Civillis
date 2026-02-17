package civil.civilization.scoring;

import civil.civilization.CScore;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Civilization scoring service: returns civilization score for any world position.
 *
 * <p>Convention: score is [0, 1], 0 is completely wild, 1 is highly civilized.
 * Monster head presence is handled separately by HeadTracker.
 */
public interface CivilizationService {

    /** Convenience: returns just the numeric score. */
    double getScoreAt(ServerWorld world, BlockPos pos);

    /** Full CScore at this position. */
    CScore getCScoreAt(ServerWorld world, BlockPos pos);
}
