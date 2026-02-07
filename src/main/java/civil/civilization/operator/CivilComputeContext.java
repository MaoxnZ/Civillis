package civil.civilization.operator;

import net.minecraft.entity.EntityType;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Optional context for a single civilization computation, allows operators to write monster head types in the neighborhood (preserves duplicates in order of appearance, for weighted sampling).
 *
 * <p>After computation, written to {@link civil.civilization.CScore}; operators write via computeScore(region, context).
 */
public final class CivilComputeContext {

    /** Preserves duplicates in order of appearance: if the same type appears multiple times, add it multiple times, used for weighted selection of conversion targets by repetition count. */
    private final List<EntityType<?>> headTypes = new CopyOnWriteArrayList<>();

    /** List of monster head types appearing in the neighborhood (including duplicates). */
    public List<EntityType<?>> getHeadTypes() {
        return Collections.unmodifiableList(headTypes);
    }

    public void addHeadType(EntityType<?> type) {
        headTypes.add(type);
    }

    public void clear() {
        headTypes.clear();
    }
}
