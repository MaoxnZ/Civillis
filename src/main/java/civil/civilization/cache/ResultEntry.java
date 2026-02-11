package civil.civilization.cache;

import civil.config.CivilConfig;

/**
 * Pre-aggregated result shard for a single Voxel Chunk.
 *
 * <p>Stores the weighted sum of L1 scores split into core (non-decaying)
 * and outer (decaying) components. This enables O(1) spawn-check queries
 * by applying time-based decay at read time rather than re-aggregating
 * hundreds of L1 shards each time.
 *
 * <p>Each entry also records the detection range under which it was computed.
 * If the user changes detection range, entries lazily recompute on next access
 * rather than invalidating the entire cache.
 *
 * @see ResultCache
 */
public final class ResultEntry {

    // ── Spatial aggregation (written on compute / delta propagation) ──

    /** Weighted sum of L1 scores within coreRadius (does not decay). */
    volatile double coreSum;

    /** Weighted sum of L1 scores outside coreRadius (decays over time). */
    volatile double outerSum;

    // ── Temporal decay (read-time application) ──

    /** ServerClock millis when the area was last "active" (player nearby). */
    volatile long presenceTime;

    /** ServerClock millis of the last recovery advancement (rate-limiting). */
    volatile long lastRecoveryTime;

    // ── TTL management ──

    /** Wall-clock millis when this entry was created or last recomputed. */
    volatile long createTime;

    // ── Config version tracking (lazy recompute on range change) ──

    int computedRangeX;
    int computedRangeZ;
    int computedRangeY;
    int computedCoreRadiusX;
    int computedCoreRadiusZ;
    int computedCoreRadiusY;

    /**
     * Create a freshly computed result entry (no persisted presence data).
     */
    public ResultEntry(double coreSum, double outerSum, long presenceTime) {
        this(coreSum, outerSum, presenceTime, presenceTime);
    }

    /**
     * Create a result entry with explicit presenceTime and lastRecoveryTime.
     * Used when restoring from H2-persisted decay state.
     */
    public ResultEntry(double coreSum, double outerSum, long presenceTime, long lastRecoveryTime) {
        this.coreSum = coreSum;
        this.outerSum = outerSum;
        this.presenceTime = presenceTime;
        this.lastRecoveryTime = lastRecoveryTime;
        this.createTime = System.currentTimeMillis();

        // Snapshot current config
        this.computedRangeX = CivilConfig.detectionRadiusX;
        this.computedRangeZ = CivilConfig.detectionRadiusZ;
        this.computedRangeY = CivilConfig.detectionRadiusY;
        this.computedCoreRadiusX = CivilConfig.coreRadiusX;
        this.computedCoreRadiusZ = CivilConfig.coreRadiusZ;
        this.computedCoreRadiusY = CivilConfig.coreRadiusY;
    }

    // ── Config validation ──

    /**
     * Whether this entry was computed with the current detection range config.
     * If false, the entry must be recomputed before use.
     */
    public boolean isConfigValid() {
        return computedRangeX == CivilConfig.detectionRadiusX
            && computedRangeZ == CivilConfig.detectionRadiusZ
            && computedRangeY == CivilConfig.detectionRadiusY
            && computedCoreRadiusX == CivilConfig.coreRadiusX
            && computedCoreRadiusZ == CivilConfig.coreRadiusZ
            && computedCoreRadiusY == CivilConfig.coreRadiusY;
    }

    // ── Score computation ──

    /**
     * Compute the effective civilization score applying time-based decay.
     *
     * <pre>
     * raw = coreSum + outerSum × freshness
     * score = sigmoid(raw)
     * </pre>
     *
     * coreSum never decays (minimum civilization guarantee near the player's base).
     * outerSum decays when the player is absent for longer than the grace period.
     *
     * @param serverNow current ServerClock.now()
     * @return civilization score in [0, 1]
     */
    public double getEffectiveScore(long serverNow) {
        double freshness = computeDecayFactor(serverNow, presenceTime);
        double raw = coreSum + outerSum * freshness;
        return sigmoid(raw);
    }

    /**
     * Get the raw (pre-sigmoid) score for debugging / logging.
     */
    public double getRawScore(long serverNow) {
        double freshness = computeDecayFactor(serverNow, presenceTime);
        return coreSum + outerSum * freshness;
    }

    // ── TTL ──

    /**
     * Whether this entry has expired based on wall-clock time.
     */
    public boolean isExpired(long ttlMillis) {
        return System.currentTimeMillis() - createTime > ttlMillis;
    }

    // ── Player presence recovery ──

    /**
     * Advance presenceTime when a player is nearby (same logic as L2Entry.onVisit).
     * Rate-limited by recoveryCooldownMs to avoid per-tick updates.
     */
    public void onPlayerNearby(long serverNow) {
        if (serverNow - lastRecoveryTime < CivilConfig.recoveryCooldownMs) return;

        lastRecoveryTime = serverNow;
        long gap = serverNow - presenceTime;
        if (gap <= 0) return;

        long recovery = (long) (gap * CivilConfig.recoveryFraction);
        recovery = Math.max(recovery, CivilConfig.minRecoveryMs);
        presenceTime += Math.min(recovery, gap);
    }

    // ── Getters ──

    public double getCoreSum() { return coreSum; }
    public double getOuterSum() { return outerSum; }
    public long getPresenceTime() { return presenceTime; }
    public long getCreateTime() { return createTime; }

    // ── Static helpers ──

    /**
     * Decay factor computation (mirrors L2Entry.computeDecayFactor).
     */
    public static double computeDecayFactor(long now, long presenceTime) {
        double hoursAbsent = (now - presenceTime) / 3_600_000.0;
        if (hoursAbsent <= CivilConfig.gracePeriodHours) return 1.0;
        double effective = hoursAbsent - CivilConfig.gracePeriodHours;
        return Math.max(CivilConfig.minDecayFloor, Math.exp(-CivilConfig.decayLambda * effective));
    }

    /**
     * Sigmoid function for score normalization (mirrors ScalableCivilizationService).
     */
    private static double sigmoid(double totalRaw) {
        double s0 = 1.0 / (1.0 + Math.exp(CivilConfig.sigmoidSteepness * CivilConfig.sigmoidMid));
        double s = rawSigmoid(totalRaw);
        double scale = 1.0 - s0;
        if (scale <= 1e-10) return totalRaw <= 0.0 ? 0.0 : 1.0;
        return Math.max(0.0, Math.min(1.0, (s - s0) / scale));
    }

    private static double rawSigmoid(double x) {
        double t = CivilConfig.sigmoidSteepness * (x - CivilConfig.sigmoidMid);
        if (t >= 20.0) return 1.0;
        if (t <= -20.0) return 0.0;
        return 1.0 / (1.0 + Math.exp(-t));
    }
}
