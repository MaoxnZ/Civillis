package civil.civilization.structure;

import java.util.List;

/**
 * Registry of structure block-change listeners. Each listener handles {@code setBlock}
 * transitions that may invalidate its structure (e.g. undying anchor, farm shrine).
 */
public final class StructureBlockChangeListeners {

    private StructureBlockChangeListeners() {}

    /** All registered listeners. Called from CivilLevelBlockChangeMixin after each successful setBlock. */
    public static final List<StructureBlockChangeListener> LISTENERS = List.of(
            new UndyingAnchorBlockChangeListener(),
            new FarmShrineBlockChangeListener(),
            new TownCenterBlockChangeListener()
    );
}
