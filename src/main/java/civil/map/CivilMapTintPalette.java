package civil.map;

import civil.CivilServices;
import civil.config.CivilConfig;
import civil.civilization.CScore;
import civil.civilization.HeadTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * Encodes civil map tint bands as bytes for network + client blending.
 * <p>
 * Map overlay policy (simple): only {@link #HIGH} (white tint) and {@link #MONSTER} (purple tint) are sent;
 * all other cases use {@link #UNKNOWN} (no overlay). Legacy bytes {@link #LOW} / {@link #MEDIUM} /
 * {@link #DIM_DISABLED} may still exist in old saves; the client ignores them for blending.
 */
public final class CivilMapTintPalette {

    /** Legacy / unused for new map tint output; client does not blend. */
    public static final byte DIM_DISABLED = 0;
    public static final byte LOW = 1;
    public static final byte MEDIUM = 2;
    public static final byte HIGH = 3;
    public static final byte MONSTER = 4;
    public static final byte UNKNOWN = 5;

    /** When {@link #evaluateTintForChunk} returns {@link #UNKNOWN} from civilization lookup. */
    public enum ScoreUnknownReason {
        NULL_CSCORE,
        LOOKUP_EXCEPTION
    }

    public record ChunkTintEval(byte band, ScoreUnknownReason scoreUnknownReason) {
    }

    private CivilMapTintPalette() {
    }

    /**
     * Same as {@link #tintForChunk} but records why civilization path yielded {@link #UNKNOWN}.
     */
    public static ChunkTintEval evaluateTintForChunk(ServerLevel level, int cx, int cz, int sy) {
        if (!civil.registry.DimensionPolicyRegistry.policyFor(level).civilization()) {
            return new ChunkTintEval(UNKNOWN, null);
        }
        int dimMinY = level.dimensionType().minY();
        int dimMaxY = dimMinY + level.dimensionType().height() - 1;
        int yIdeal = sy * 16 + 8;
        int y = Math.max(dimMinY, Math.min(dimMaxY, yIdeal));
        BlockPos pos = new BlockPos(cx * 16 + 8, y, cz * 16 + 8);

        HeadTracker registry = CivilServices.getHeadTracker();
        if (registry != null && registry.isInitialized()) {
            String dim = level.dimension().identifier().toString();
            List<EntityType<?>> headTypes = registry.getHeadTypesNear(
                    dim, pos,
                    CivilConfig.headRangeX,
                    CivilConfig.headRangeZ,
                    CivilConfig.headRangeY);
            if (!headTypes.isEmpty()) {
                return new ChunkTintEval(MONSTER, null);
            }
        }

        CScore cScore;
        try {
            cScore = CivilServices.getCivilizationService().getCScoreAt(level, pos);
        } catch (Exception e) {
            return new ChunkTintEval(UNKNOWN, ScoreUnknownReason.LOOKUP_EXCEPTION);
        }
        if (cScore == null) {
            return new ChunkTintEval(UNKNOWN, ScoreUnknownReason.NULL_CSCORE);
        }
        double score = cScore.score();
        if (score < CivilConfig.spawnThresholdMid) {
            return new ChunkTintEval(UNKNOWN, null);
        }
        return new ChunkTintEval(HIGH, null);
    }

    public static byte tintForChunk(ServerLevel level, int cx, int cz, int sy) {
        return evaluateTintForChunk(level, cx, cz, sy).band();
    }
}
