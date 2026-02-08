package civil.civilization;

import net.minecraft.entity.EntityType;

import java.util.List;

/**
 * Civilization score + context: result of single civilization computation,
 * contains score [0,1] and list of monster head types in neighborhood.
 *
 * <p>{@link #isForceAllow()} returns true when any monster head exists in the neighborhood,
 * signaling that spawn should be allowed regardless of civilization score.
 * <p>headTypes is a list (includes duplicates): if same type appears multiple times, add it
 * multiple times, used for "weighted by repetition count" selection of conversion targets;
 * use {@link #hasHeadFor} when need to check "whether contains a type" (internally list.contains).
 */
public record CScore(double score, List<EntityType<?>> headTypes) {

    /** Whether any monster head exists in the neighborhood (force allow spawn). */
    public boolean isForceAllow() {
        return headTypes != null && !headTypes.isEmpty();
    }

    /** Whether neighborhood contains head of this entity type (matching type bonus). */
    public boolean hasHeadFor(EntityType<?> entityType) {
        return headTypes != null && headTypes.contains(entityType);
    }
}
