package civil.civilization.core;

import civil.civilization.CScore;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Civilization scoring service: returns civilization score (and neighborhood head types etc.) for any world position.
 * Convention: score is [0,1], 0 is completely wild, 1 is highly civilized;
 * monster head presence is indicated by {@link CScore#isForceAllow()} and {@link CScore#headTypes()},
 * not by the score value. CScore.headTypes is used for "matching type spawn bonus".
 */
public interface CivilizationService {

    /** Civilization score at this position, range [0,1]. */
    double getScoreAt(ServerWorld world, BlockPos pos);

    /** CScore at this position (civilization score + neighborhood head types etc.), for spawn policy judgment. */
    CScore getCScoreAt(ServerWorld world, BlockPos pos);
}
