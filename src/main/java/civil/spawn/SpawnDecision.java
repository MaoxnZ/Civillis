package civil.spawn;

import net.minecraft.entity.EntityType;

import java.util.List;

/**
 * Single spawn decision result, for Mixin logging and head-based conversion.
 *
 * <p>branch: LOW / MID / HIGH (civilization score thresholds);
 * HEAD_NEARBY (enabled heads within VC box → bypass civilization suppression);
 * HEAD_SUPPRESS (distant heads → probabilistic block).
 *
 * <p>For HEAD_NEARBY:
 * <ul>
 *   <li>{@code nearbyHeadCount}: total enabled heads in the VC box (for conversion threshold)</li>
 *   <li>{@code headTypes}: conversion pool (enabled + convert=true entity types, with duplicates
 *       for weighted sampling)</li>
 * </ul>
 */
public record SpawnDecision(boolean block, double score, String branch,
                            int nearbyHeadCount, List<EntityType<?>> headTypes) {

    public static final String BRANCH_LOW = "LOW";
    public static final String BRANCH_MID = "MID";
    public static final String BRANCH_HIGH = "HIGH";
    public static final String BRANCH_HEAD_NEARBY = "HEAD_NEARBY";
    public static final String BRANCH_HEAD_SUPPRESS = "HEAD_SUPPRESS";

    /** Convenience constructor for non-head branches (no head info). */
    public SpawnDecision(boolean block, double score, String branch) {
        this(block, score, branch, 0, List.of());
    }
}
