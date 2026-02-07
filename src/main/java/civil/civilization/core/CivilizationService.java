package civil.civilization.core;

import civil.civilization.CScore;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Civilization scoring service: returns civilization score (and neighborhood head types etc.) for any world position.
 * Convention: 0 is completely wild, 1 is highly civilized;
 * {@link civil.civilization.CivilValues#FORCE_ALLOW_SCORE} indicates monster heads exist, force allow spawn;
 * CScore.headTypes is used for "matching type spawn bonus".
 */
public interface CivilizationService {

    /** Civilization score at this position (&gt;1 indicates force allow spawn). */
    double getScoreAt(ServerWorld world, BlockPos pos);

    /** CScore at this position (civilization score + neighborhood head types etc.), for spawn policy judgment. */
    CScore getCScoreAt(ServerWorld world, BlockPos pos);
}
