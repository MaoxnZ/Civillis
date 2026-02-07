package civil.civilization;

/**
 * Civilization/spawn related constants.
 *
 * <p>When any monster head exists in neighborhood, operator can return {@link #FORCE_ALLOW_SCORE},
 * outer layer judgment treats this position as "force allow spawn"; matching type heads can further be used for "matching type spawn bonus".
 */
public final class CivilValues {

    /** Civilization value greater than 1, indicates this neighborhood has monster heads, force allow spawn (bypasses civilization threshold). */
    public static final double FORCE_ALLOW_SCORE = 2.0;

    private CivilValues() {
    }
}
