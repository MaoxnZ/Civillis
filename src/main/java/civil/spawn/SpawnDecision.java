package civil.spawn;

import net.minecraft.entity.EntityType;

import java.util.List;

/**
 * Single spawn decision result, for Mixin logging and "convert by head" usage.
 *
 * <p>branch: LOW / MID / HIGH (civilization score thresholds); HEAD_MATCH (current mob matches neighborhood head); HEAD_ANY (neighborhood has any monster head).
 * <p>headTypes: Only has value for HEAD_MATCH / HEAD_ANY (list, includes duplicates), used by Mixin to replace mobs by head type, weighted sampling by repetition count.
 */
public record SpawnDecision(boolean block, double score, String branch, List<EntityType<?>> headTypes) {

    public static final String BRANCH_LOW = "LOW";
    public static final String BRANCH_MID = "MID";
    public static final String BRANCH_HIGH = "HIGH";

    /** Use empty list when no head information. */
    public SpawnDecision(boolean block, double score, String branch) {
        this(block, score, branch, List.of());
    }
}
