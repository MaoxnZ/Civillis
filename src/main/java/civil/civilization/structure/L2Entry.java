package civil.civilization.structure;

/**
 * L2 cache entry: stores detailed state and aggregated values of 9 L1 cells.
 *
 * <p>Each cell has three states:
 * <ul>
 *   <li>EMPTY (0): Never filled with score</li>
 *   <li>VALID (1): Has valid score, participates in scoreSum/validCount</li>
 *   <li>DIRTY (2): Invalidated due to world block changes, needs recalculation when repairing</li>
 * </ul>
 */
public final class L2Entry {

    /** Cell state constants. */
    public static final byte STATE_EMPTY = 0;
    public static final byte STATE_VALID = 1;
    public static final byte STATE_DIRTY = 2;

    private final L2Key key;

    /** Score for each cell (only cells in VALID state are valid). */
    private final double[] scores;

    /** State for each cell. */
    private final byte[] states;

    /** Sum of scores for all VALID cells. */
    private double scoreSum;

    /** Number of VALID cells. */
    private int validCount;

    /** Number of DIRTY cells. */
    private int dirtyCount;

    /** Whether the entire node has dirty cells (equivalent to dirtyCount > 0). */
    private boolean dirty;

    public L2Entry(L2Key key) {
        this.key = key;
        this.scores = new double[L2Key.CELL_COUNT];
        this.states = new byte[L2Key.CELL_COUNT];
        this.scoreSum = 0;
        this.validCount = 0;
        this.dirtyCount = 0;
        this.dirty = false;
    }

    public L2Key getKey() {
        return key;
    }

    public double getScoreSum() {
        return scoreSum;
    }

    public int getValidCount() {
        return validCount;
    }

    public int getDirtyCount() {
        return dirtyCount;
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Get the state of a cell.
     */
    public byte getCellState(int idx) {
        return states[idx];
    }

    /**
     * Get the score of a cell (only meaningful when VALID).
     */
    public double getCellScore(int idx) {
        return scores[idx];
    }

    /**
     * Update a cell to VALID state (used for cascade updates after L1 put, or repairing DIRTY cells).
     * 
     * <p>Logic:
     * <ul>
     *   <li>If original state is DIRTY: dirtyCount--, if becomes 0 then dirty=false</li>
     *   <li>If original state is VALID: scoreSum subtract old value, validCount--</li>
     *   <li>Set to VALID, update score, scoreSum add new value, validCount++</li>
     * </ul>
     */
    public void updateCell(int idx, double newScore) {
        byte oldState = states[idx];

        if (oldState == STATE_DIRTY) {
            dirtyCount--;
            if (dirtyCount == 0) {
                dirty = false;
            }
        } else if (oldState == STATE_VALID) {
            scoreSum -= scores[idx];
            validCount--;
        }

        states[idx] = STATE_VALID;
        scores[idx] = newScore;
        scoreSum += newScore;
        validCount++;
    }

    /**
     * Update a cell to VALID state (via L1 coordinates).
     */
    public void updateCell(VoxelChunkKey l1, double newScore) {
        updateCell(key.l1ToIndex(l1), newScore);
    }

    /**
     * Mark a cell as DIRTY (used when world blocks change).
     * 
     * <p>Logic:
     * <ul>
     *   <li>If original state is VALID: scoreSum subtract old value, validCount--, set to DIRTY, dirtyCount++</li>
     *   <li>If original state is EMPTY: set to DIRTY, dirtyCount++</li>
     *   <li>If already DIRTY: do nothing (idempotent)</li>
     * </ul>
     */
    public void markCellDirty(int idx) {
        byte oldState = states[idx];

        if (oldState == STATE_VALID) {
            scoreSum -= scores[idx];
            validCount--;
            states[idx] = STATE_DIRTY;
            dirtyCount++;
            dirty = true;
        } else if (oldState == STATE_EMPTY) {
            states[idx] = STATE_DIRTY;
            dirtyCount++;
            dirty = true;
        }
        // If already DIRTY, do nothing
    }

    /**
     * Mark a cell as DIRTY (via L1 coordinates).
     */
    public void markCellDirty(VoxelChunkKey l1) {
        markCellDirty(key.l1ToIndex(l1));
    }

    /**
     * Calculate coarse score (for L2 contribution calculation).
     * 
     * @return scoreSum (accumulated value, range [0, 9]), equivalent to full scan accumulation result
     */
    public double getCoarseScore() {
        return scoreSum;
    }

    /**
     * Iterate over indices of all DIRTY cells.
     * 
     * @param consumer Processing for each DIRTY cell index
     */
    public void forEachDirtyCell(java.util.function.IntConsumer consumer) {
        for (int i = 0; i < L2Key.CELL_COUNT; i++) {
            if (states[i] == STATE_DIRTY) {
                consumer.accept(i);
            }
        }
    }

    // ========== Persistence Support ==========

    /**
     * Get score array (for persistence).
     */
    public double[] getScoresArray() {
        return scores.clone();
    }

    /**
     * Get state array (for persistence).
     */
    public byte[] getStatesArray() {
        return states.clone();
    }

    /**
     * Restore state from arrays (for persistence loading).
     */
    public void restoreFromArrays(double[] scoresArray, byte[] statesArray) {
        if (scoresArray.length != L2Key.CELL_COUNT || statesArray.length != L2Key.CELL_COUNT) {
            return;
        }
        System.arraycopy(scoresArray, 0, this.scores, 0, L2Key.CELL_COUNT);
        System.arraycopy(statesArray, 0, this.states, 0, L2Key.CELL_COUNT);
        
        // Recalculate aggregated values
        this.scoreSum = 0;
        this.validCount = 0;
        this.dirtyCount = 0;
        for (int i = 0; i < L2Key.CELL_COUNT; i++) {
            if (states[i] == STATE_VALID) {
                scoreSum += scores[i];
                validCount++;
            } else if (states[i] == STATE_DIRTY) {
                dirtyCount++;
            }
        }
        this.dirty = dirtyCount > 0;
    }
}
