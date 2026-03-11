package civil.civilization.structure;

import java.util.List;

/**
 * Registry of structure block-change listeners. Each listener handles block removals
 * that may invalidate its structure (e.g. undying anchor, future beacon respawn).
 */
public final class StructureBlockChangeListeners {

    private StructureBlockChangeListeners() {}

    /** All registered listeners. Called from CivilLevelBlockChangeMixin on each block removal. */
    public static final List<StructureBlockChangeListener> LISTENERS = List.of(
            new UndyingAnchorBlockChangeListener()
            // Future: new BeaconRespawnBlockChangeListener(), etc.
    );
}
