package civil.config;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Centralized tunable parameters for the Civil mod.
 *
 * <p>Two layers of configuration:
 * <ol>
 *   <li><b>Simple params</b> ({@code simple.*}): 6 user-friendly values shown in the GUI.
 *       Forward-mapped to internal params via {@link #computeInternalFromSimple()}.</li>
 *   <li><b>Raw params</b> (e.g. {@code decay.lambda}): advanced overrides. If present
 *       in properties AND different from computed values, they take priority.
 *       The GUI warns the user when raw overrides are detected.</li>
 * </ol>
 *
 * <p>Load order: simple → compute → raw override.
 * Save writes both simple and computed raw values.
 */
public final class CivilConfig {

    private static final String FILE_NAME = "civil.properties";

    // ══════════════════════════════════════════════════════════
    //  User-facing simple params (GUI sliders)
    // ══════════════════════════════════════════════════════════

    /** Freshness duration: hours before decay starts. Range [1, 48], default 6. */
    public static int simpleFreshnessDuration = 6;

    /** Decay speed: 1 (very slow) to 10 (very fast), default 5. */
    public static int simpleDecaySpeed = 5;

    /** Decay floor: minimum remaining score %, range [0, 50], default 25. */
    public static int simpleDecayFloor = 25;

    /** Recovery speed: 1 (slow) to 10 (fast), default 5. */
    public static int simpleRecoverySpeed = 5;

    /** Spawn suppression strength: 1 (weak) to 10 (strong), default 5. */
    public static int simpleSpawnSuppression = 5;

    /** Detection range: box side in blocks, range [112, 496] step 32, default 240. */
    public static int simpleDetectionRange = 240;

    /** Patrol influence range: 2-8 slider (VC radius), val×16 = blocks. Default 4 → 64 blocks. */
    public static int simplePatrolRange = 4;

    /** Head attraction strength: 1 (weak) to 10 (strong), default 5. Maps to headAttractLambda. */
    public static int simpleHeadAttractStrength = 5;

    /** Head attraction range: 3-10 slider, val×16 = blocks. Default 8 → 128 blocks. Maps to headAttractMaxRadius. */
    public static int simpleHeadAttractRange = 8;

    /** Whether the protection aura visualization is enabled (sonar scan, particles, walls, sounds). */
    public static boolean auraEffectEnabled = true;

    // ══════════════════════════════════════════════════════════
    //  Raw override detection
    // ══════════════════════════════════════════════════════════

    /** Param group indices for override checking. */
    public static final int PARAM_FRESHNESS     = 0;
    public static final int PARAM_DECAY_SPEED   = 1;
    public static final int PARAM_DECAY_FLOOR   = 2;
    public static final int PARAM_RECOVERY      = 3;
    public static final int PARAM_SPAWN         = 4;
    public static final int PARAM_RANGE         = 5;
    public static final int PARAM_HEAD_ATTRACT  = 6;
    private static final boolean[] rawOverrides = new boolean[7];

    /** Whether a raw override was detected for the given simple param group. */
    public static boolean hasRawOverride(int param) {
        return param >= 0 && param < rawOverrides.length && rawOverrides[param];
    }

    // Snapshot of simple values at load time, used to detect which sliders actually changed.
    private static int loadedSimpleFreshness;
    private static int loadedSimpleDecaySpeed;
    private static int loadedSimpleDecayFloor;
    private static int loadedSimpleRecoverySpeed;
    private static int loadedSimpleSpawnSuppression;
    private static int loadedSimpleDetectionRange;
    private static int loadedSimpleHeadAttractStrength;
    private static int loadedSimpleHeadAttractRange;

    /**
     * Compare current simple values to the snapshot taken at load time.
     * Clear raw overrides ONLY for groups whose simple param actually changed.
     * Must be called before {@link #computeInternalFromSimple()} in the GUI save flow.
     */
    public static void clearOverridesForChangedSimple() {
        if (simpleFreshnessDuration != loadedSimpleFreshness) {
            rawOverrides[PARAM_FRESHNESS] = false;
            loadedSimpleFreshness = simpleFreshnessDuration;
        }
        if (simpleDecaySpeed != loadedSimpleDecaySpeed) {
            rawOverrides[PARAM_DECAY_SPEED] = false;
            loadedSimpleDecaySpeed = simpleDecaySpeed;
        }
        if (simpleDecayFloor != loadedSimpleDecayFloor) {
            rawOverrides[PARAM_DECAY_FLOOR] = false;
            loadedSimpleDecayFloor = simpleDecayFloor;
        }
        if (simpleRecoverySpeed != loadedSimpleRecoverySpeed) {
            rawOverrides[PARAM_RECOVERY] = false;
            loadedSimpleRecoverySpeed = simpleRecoverySpeed;
        }
        if (simpleSpawnSuppression != loadedSimpleSpawnSuppression) {
            rawOverrides[PARAM_SPAWN] = false;
            loadedSimpleSpawnSuppression = simpleSpawnSuppression;
        }
        if (simpleDetectionRange != loadedSimpleDetectionRange) {
            rawOverrides[PARAM_RANGE] = false;
            loadedSimpleDetectionRange = simpleDetectionRange;
        }
        if (simpleHeadAttractStrength != loadedSimpleHeadAttractStrength
                || simpleHeadAttractRange != loadedSimpleHeadAttractRange) {
            rawOverrides[PARAM_HEAD_ATTRACT] = false;
            loadedSimpleHeadAttractStrength = simpleHeadAttractStrength;
            loadedSimpleHeadAttractRange = simpleHeadAttractRange;
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Diagnostics
    // ══════════════════════════════════════════════════════════

    private static boolean tpsLogEnabled = true;
    private static int tpsLogIntervalTicks = 20;

    // ══════════════════════════════════════════════════════════
    //  Internal params (computed from simple, or raw-overridden)
    // ══════════════════════════════════════════════════════════

    // -- Decay & Recovery --
    public static double gracePeriodHours = 6.0;
    public static double decayLambda = 0.008;
    public static double minDecayFloor = 0.25;
    public static long   recoveryCooldownMs = 60_000L;
    public static double recoveryFraction = 0.20;
    public static long   minRecoveryMs = 30L * 60_000L;

    // -- Spawn Thresholds --
    public static double spawnThresholdLow = 0.1;
    public static double spawnThresholdMid = 0.3;

    // -- Scoring / Aggregation --
    public static double sigmoidMid = 0.8;
    public static double sigmoidSteepness = 3.0;
    public static double distanceAlphaSq = 0.5;
    public static double normalizationFactor = 5.0;

    // -- Detection Ranges (voxel chunks) --
    public static int detectionRadiusX = 7;
    public static int detectionRadiusZ = 7;
    public static int detectionRadiusY = 1;
    public static int coreRadiusX = 1;
    public static int coreRadiusZ = 1;
    public static int coreRadiusY = 0;
    public static int headRangeX = 1;
    public static int headRangeZ = 1;
    public static int headRangeY = 0;

    // -- Patrol Influence (player presence keeping area alive) --
    /** Patrol radius (voxel chunks). User slider sets X/Z together; Y defaults to 1. */
    public static int patrolRadiusX = 4;
    public static int patrolRadiusZ = 4;
    public static int patrolRadiusY = 1;

    // -- Head Attraction (totem suppression of distant spawns) --
    public static boolean headAttractEnabled = true;
    public static double headAttractNearBlocks = 32.0;
    public static double headAttractMaxRadius = 128.0;
    public static double headAttractLambda = 0.15;

    // -- Cache & Performance --
    /** L1 information shard TTL. Short-lived: only needs to survive until ResultEntry is computed.
     *  Prefetcher refreshes on VC change; stationary players rely on ResultEntry for spawn checks. */
    public static long l1TtlMs = 5 * 60 * 1000L;         // 5 minutes
    /** ResultEntry TTL. Long-lived: user-facing cache for O(1) spawn checks + decay tracking. */
    public static long resultTtlMs = 60 * 60 * 1000L;    // 60 minutes
    public static int  clockPersistTicks = 6000;

    // -- UI / Items --
    public static int detectorAnimationTicks = 40;
    public static int detectorCooldownTicks = 10;

    // ══════════════════════════════════════════════════════════
    //  Construction
    // ══════════════════════════════════════════════════════════

    private CivilConfig() {
    }

    // ══════════════════════════════════════════════════════════
    //  Getters (diagnostics)
    // ══════════════════════════════════════════════════════════

    public static boolean isTpsLogEnabled() { return tpsLogEnabled; }
    public static int getTpsLogIntervalTicks() { return tpsLogIntervalTicks; }

    // ══════════════════════════════════════════════════════════
    //  Forward mapping: simple → internal
    // ══════════════════════════════════════════════════════════

    /**
     * Compute internal params from the 8 simple user-facing values.
     * <p>Groups with active raw overrides are skipped to preserve the user's
     * manual configuration. During {@link #load()} Phase 3, all override flags
     * are false so everything is computed; the GUI save flow calls
     * {@link #clearOverridesForChangedSimple()} first to selectively clear flags.
     */
    public static void computeInternalFromSimple() {
        // 1. Freshness → gracePeriodHours
        if (!rawOverrides[PARAM_FRESHNESS]) {
            gracePeriodHours = simpleFreshnessDuration;
        }

        // 2. Decay speed (1-10) → decayLambda
        if (!rawOverrides[PARAM_DECAY_SPEED]) {
            decayLambda = 0.001 * Math.exp(0.52 * (simpleDecaySpeed - 1));
        }

        // 3. Decay floor (0-50%) → minDecayFloor
        if (!rawOverrides[PARAM_DECAY_FLOOR]) {
            minDecayFloor = simpleDecayFloor / 100.0;
        }

        // 4. Recovery speed (1-10) → cooldown, fraction, minRecovery
        if (!rawOverrides[PARAM_RECOVERY]) {
            double tRec = (simpleRecoverySpeed - 1.0) / 9.0;
            recoveryCooldownMs = Math.round(lerp(120_000, 15_000, tRec));
            recoveryFraction   = lerp(0.05, 0.50, tRec);
            minRecoveryMs      = Math.round(lerp(3_600_000, 600_000, tRec));
        }

        // 5. Spawn suppression (1-10) → thresholdLow, thresholdMid
        if (!rawOverrides[PARAM_SPAWN]) {
            double tSpawn = (simpleSpawnSuppression - 1.0) / 9.0;
            spawnThresholdMid = lerp(0.70, 0.05, tSpawn);
            spawnThresholdLow = spawnThresholdMid / 3.0;
        }

        // 6. Detection range (blocks, box side) → detectionRadiusX/Z
        if (!rawOverrides[PARAM_RANGE]) {
            int sideChunks = simpleDetectionRange / 16;
            int radius = (sideChunks - 1) / 2;
            detectionRadiusX = radius;
            detectionRadiusZ = radius;
        }

        // 7-8. Head attraction strength & range
        if (!rawOverrides[PARAM_HEAD_ATTRACT]) {
            headAttractLambda    = 0.03 * simpleHeadAttractStrength;
            headAttractMaxRadius = simpleHeadAttractRange * 16.0;
        }

        // 9. Patrol influence range (slider sets XZ together, Y fixed)
        patrolRadiusX = simplePatrolRange;
        patrolRadiusZ = simplePatrolRange;
        // patrolRadiusY left at default (1) — not exposed to user slider
    }

    // ══════════════════════════════════════════════════════════
    //  Loading
    // ══════════════════════════════════════════════════════════

    /** Called during mod initialization, loads from config/civil.properties. */
    public static void load() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        Path file = dir.resolve(FILE_NAME);
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (var is = Files.newInputStream(file)) {
                p.load(is);
            } catch (IOException e) {
                // Ignore, use default values
            }
        }

        // ── Phase 1: Diagnostics (standalone) ──
        tpsLogEnabled = parseBoolean(p.getProperty("tpsLog.enabled"), true);
        tpsLogIntervalTicks = parseInt(p.getProperty("tpsLog.intervalTicks"), 20);
        tpsLogIntervalTicks = Math.max(1, Math.min(1000, tpsLogIntervalTicks));

        // ── Phase 2: Load simple params ──
        simpleFreshnessDuration = parseInt(p.getProperty("simple.freshnessDuration"), simpleFreshnessDuration);
        simpleFreshnessDuration = Math.max(1, Math.min(48, simpleFreshnessDuration));

        simpleDecaySpeed = parseInt(p.getProperty("simple.decaySpeed"), simpleDecaySpeed);
        simpleDecaySpeed = Math.max(1, Math.min(10, simpleDecaySpeed));

        simpleDecayFloor = parseInt(p.getProperty("simple.decayFloor"), simpleDecayFloor);
        simpleDecayFloor = Math.max(0, Math.min(50, simpleDecayFloor));

        simpleRecoverySpeed = parseInt(p.getProperty("simple.recoverySpeed"), simpleRecoverySpeed);
        simpleRecoverySpeed = Math.max(1, Math.min(10, simpleRecoverySpeed));

        simpleSpawnSuppression = parseInt(p.getProperty("simple.spawnSuppression"), simpleSpawnSuppression);
        simpleSpawnSuppression = Math.max(1, Math.min(10, simpleSpawnSuppression));

        simpleDetectionRange = parseInt(p.getProperty("simple.detectionRange"), simpleDetectionRange);
        // Snap to nearest valid step (odd chunk count): 112, 144, 176, ..., 496
        simpleDetectionRange = snapDetectionRange(simpleDetectionRange);

        simpleHeadAttractStrength = parseInt(p.getProperty("simple.headAttractStrength"), simpleHeadAttractStrength);
        simpleHeadAttractStrength = Math.max(1, Math.min(10, simpleHeadAttractStrength));

        simpleHeadAttractRange = parseInt(p.getProperty("simple.headAttractRange"), simpleHeadAttractRange);
        simpleHeadAttractRange = Math.max(3, Math.min(10, simpleHeadAttractRange));

        simplePatrolRange = parseInt(p.getProperty("simple.patrolRange"), simplePatrolRange);
        simplePatrolRange = Math.max(2, Math.min(8, simplePatrolRange));

        auraEffectEnabled = parseBoolean(p.getProperty("aura.enabled"), auraEffectEnabled);

        // Snapshot simple values for change detection in GUI save flow
        loadedSimpleFreshness           = simpleFreshnessDuration;
        loadedSimpleDecaySpeed          = simpleDecaySpeed;
        loadedSimpleDecayFloor          = simpleDecayFloor;
        loadedSimpleRecoverySpeed       = simpleRecoverySpeed;
        loadedSimpleSpawnSuppression    = simpleSpawnSuppression;
        loadedSimpleDetectionRange      = simpleDetectionRange;
        loadedSimpleHeadAttractStrength = simpleHeadAttractStrength;
        loadedSimpleHeadAttractRange    = simpleHeadAttractRange;

        // Reset override flags (important if load() is called more than once)
        java.util.Arrays.fill(rawOverrides, false);

        // ── Phase 3: Forward-compute internal params from simple ──
        computeInternalFromSimple();

        // Save computed values for override detection
        double compGracePeriod        = gracePeriodHours;
        double compDecayLambda        = decayLambda;
        double compMinDecayFloor      = minDecayFloor;
        long   compRecoveryCd         = recoveryCooldownMs;
        double compRecoveryFrac       = recoveryFraction;
        long   compRecoveryMin        = minRecoveryMs;
        double compSpawnLow           = spawnThresholdLow;
        double compSpawnMid           = spawnThresholdMid;
        int    compDetRadX            = detectionRadiusX;
        int    compDetRadZ            = detectionRadiusZ;
        double compHeadAttractLambda  = headAttractLambda;
        double compHeadAttractMaxRad  = headAttractMaxRadius;

        // ── Phase 4: Load raw overrides (advanced users) ──
        gracePeriodHours   = parseDouble(p.getProperty("decay.gracePeriodHours"), gracePeriodHours);
        decayLambda        = parseDouble(p.getProperty("decay.lambda"), decayLambda);
        minDecayFloor      = parseDouble(p.getProperty("decay.minFloor"), minDecayFloor);
        recoveryCooldownMs = parseLong(p.getProperty("recovery.cooldownMs"), recoveryCooldownMs);
        recoveryFraction   = parseDouble(p.getProperty("recovery.fraction"), recoveryFraction);
        minRecoveryMs      = parseLong(p.getProperty("recovery.minMs"), minRecoveryMs);
        spawnThresholdLow  = parseDouble(p.getProperty("spawn.thresholdLow"), spawnThresholdLow);
        spawnThresholdMid  = parseDouble(p.getProperty("spawn.thresholdMid"), spawnThresholdMid);

        sigmoidMid         = parseDouble(p.getProperty("scoring.sigmoidMid"), sigmoidMid);
        sigmoidSteepness   = parseDouble(p.getProperty("scoring.sigmoidSteepness"), sigmoidSteepness);
        distanceAlphaSq    = parseDouble(p.getProperty("scoring.distanceAlphaSq"), distanceAlphaSq);
        normalizationFactor = parseDouble(p.getProperty("scoring.normalizationFactor"), normalizationFactor);

        detectionRadiusX   = parseInt(p.getProperty("range.detectionRadiusX"), detectionRadiusX);
        detectionRadiusZ   = parseInt(p.getProperty("range.detectionRadiusZ"), detectionRadiusZ);
        detectionRadiusY   = parseInt(p.getProperty("range.detectionRadiusY"), detectionRadiusY);
        coreRadiusX        = parseInt(p.getProperty("range.coreRadiusX"), coreRadiusX);
        coreRadiusZ        = parseInt(p.getProperty("range.coreRadiusZ"), coreRadiusZ);
        coreRadiusY        = parseInt(p.getProperty("range.coreRadiusY"), coreRadiusY);
        headRangeX         = parseInt(p.getProperty("range.headRangeX"), headRangeX);
        headRangeZ         = parseInt(p.getProperty("range.headRangeZ"), headRangeZ);
        headRangeY         = parseInt(p.getProperty("range.headRangeY"), headRangeY);

        headAttractEnabled    = parseBoolean(p.getProperty("headAttract.enabled"), headAttractEnabled);
        headAttractNearBlocks = parseDouble(p.getProperty("headAttract.nearBlocks"), headAttractNearBlocks);
        headAttractMaxRadius  = parseDouble(p.getProperty("headAttract.maxRadius"), headAttractMaxRadius);
        headAttractLambda     = parseDouble(p.getProperty("headAttract.lambda"), headAttractLambda);

        l1TtlMs            = parseLong(p.getProperty("cache.l1TtlMs"), l1TtlMs);
        resultTtlMs        = parseLong(p.getProperty("cache.resultTtlMs"), resultTtlMs);
        clockPersistTicks  = parseInt(p.getProperty("cache.clockPersistTicks"), clockPersistTicks);

        detectorAnimationTicks = parseInt(p.getProperty("ui.detectorAnimationTicks"), detectorAnimationTicks);
        detectorCooldownTicks  = parseInt(p.getProperty("ui.detectorCooldownTicks"), detectorCooldownTicks);

        // ── Phase 5: Detect raw overrides ──
        rawOverrides[PARAM_FRESHNESS]   = !approxEq(gracePeriodHours, compGracePeriod);
        rawOverrides[PARAM_DECAY_SPEED] = !approxEq(decayLambda, compDecayLambda);
        rawOverrides[PARAM_DECAY_FLOOR] = !approxEq(minDecayFloor, compMinDecayFloor);
        rawOverrides[PARAM_RECOVERY]    = recoveryCooldownMs != compRecoveryCd
                                        || !approxEq(recoveryFraction, compRecoveryFrac)
                                        || minRecoveryMs != compRecoveryMin;
        rawOverrides[PARAM_SPAWN]       = !approxEq(spawnThresholdLow, compSpawnLow)
                                        || !approxEq(spawnThresholdMid, compSpawnMid);
        rawOverrides[PARAM_RANGE]       = detectionRadiusX != compDetRadX
                                        || detectionRadiusZ != compDetRadZ;
        rawOverrides[PARAM_HEAD_ATTRACT] = !approxEq(headAttractLambda, compHeadAttractLambda)
                                        || !approxEq(headAttractMaxRadius, compHeadAttractMaxRad);

        // ── Phase 6: Write default file if not present ──
        if (!Files.isRegularFile(file)) {
            save();
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Saving
    // ══════════════════════════════════════════════════════════

    /**
     * Save current values to civil.properties.
     * <p>Simple params are always written. Raw params that have active overrides
     * are written uncommented (preserving the user's manual configuration);
     * all other raw params are written commented as reference.
     */
    public static void save() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        Path file = dir.resolve(FILE_NAME);
        try {
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder();
            sb.append("# Civil mod configuration\n");
            sb.append("# Simple settings are used by the in-game GUI.\n");
            sb.append("# Advanced (raw) settings override simple-computed values if uncommented.\n\n");

            sb.append("# ── Diagnostics ──\n");
            sb.append("tpsLog.enabled=").append(tpsLogEnabled).append('\n');
            sb.append("tpsLog.intervalTicks=").append(tpsLogIntervalTicks).append('\n');
            sb.append('\n');

            sb.append("# ── Simple Settings (GUI) ──\n");
            sb.append("simple.freshnessDuration=").append(simpleFreshnessDuration).append('\n');
            sb.append("simple.decaySpeed=").append(simpleDecaySpeed).append('\n');
            sb.append("simple.decayFloor=").append(simpleDecayFloor).append('\n');
            sb.append("simple.recoverySpeed=").append(simpleRecoverySpeed).append('\n');
            sb.append("simple.spawnSuppression=").append(simpleSpawnSuppression).append('\n');
            sb.append("simple.detectionRange=").append(simpleDetectionRange).append('\n');
            sb.append("simple.headAttractStrength=").append(simpleHeadAttractStrength).append('\n');
            sb.append("simple.headAttractRange=").append(simpleHeadAttractRange).append('\n');
            sb.append("simple.patrolRange=").append(simplePatrolRange).append('\n');
            sb.append('\n');

            sb.append("# ── Aura Visualization ──\n");
            sb.append("aura.enabled=").append(auraEffectEnabled).append('\n');
            sb.append('\n');

            // Raw overrides: uncommented if active, commented otherwise
            String pFresh  = rawOverrides[PARAM_FRESHNESS]   ? "" : "#";
            String pDecay  = rawOverrides[PARAM_DECAY_SPEED] ? "" : "#";
            String pFloor  = rawOverrides[PARAM_DECAY_FLOOR] ? "" : "#";
            String pRecov  = rawOverrides[PARAM_RECOVERY]    ? "" : "#";
            String pSpawn  = rawOverrides[PARAM_SPAWN]       ? "" : "#";
            String pRange  = rawOverrides[PARAM_RANGE]       ? "" : "#";
            String pHead   = rawOverrides[PARAM_HEAD_ATTRACT] ? "" : "#";

            sb.append("# ── Advanced: Decay & Recovery (uncomment to override) ──\n");
            sb.append(pFresh).append("decay.gracePeriodHours=").append(gracePeriodHours).append('\n');
            sb.append(pDecay).append("decay.lambda=").append(decayLambda).append('\n');
            sb.append(pFloor).append("decay.minFloor=").append(minDecayFloor).append('\n');
            sb.append(pRecov).append("recovery.cooldownMs=").append(recoveryCooldownMs).append('\n');
            sb.append(pRecov).append("recovery.fraction=").append(recoveryFraction).append('\n');
            sb.append(pRecov).append("recovery.minMs=").append(minRecoveryMs).append('\n');
            sb.append('\n');

            sb.append("# ── Advanced: Spawn Thresholds ──\n");
            sb.append(pSpawn).append("spawn.thresholdLow=").append(spawnThresholdLow).append('\n');
            sb.append(pSpawn).append("spawn.thresholdMid=").append(spawnThresholdMid).append('\n');
            sb.append('\n');

            sb.append("# ── Advanced: Scoring ──\n");
            sb.append("#scoring.sigmoidMid=").append(sigmoidMid).append('\n');
            sb.append("#scoring.sigmoidSteepness=").append(sigmoidSteepness).append('\n');
            sb.append("#scoring.distanceAlphaSq=").append(distanceAlphaSq).append('\n');
            sb.append("#scoring.normalizationFactor=").append(normalizationFactor).append('\n');
            sb.append('\n');

            sb.append("# ── Advanced: Detection Ranges (voxel chunks) ──\n");
            sb.append(pRange).append("range.detectionRadiusX=").append(detectionRadiusX).append('\n');
            sb.append(pRange).append("range.detectionRadiusZ=").append(detectionRadiusZ).append('\n');
            sb.append("#range.detectionRadiusY=").append(detectionRadiusY).append('\n');
            sb.append("#range.coreRadiusX=").append(coreRadiusX).append('\n');
            sb.append("#range.coreRadiusZ=").append(coreRadiusZ).append('\n');
            sb.append("#range.coreRadiusY=").append(coreRadiusY).append('\n');
            sb.append("#range.headRangeX=").append(headRangeX).append('\n');
            sb.append("#range.headRangeZ=").append(headRangeZ).append('\n');
            sb.append("#range.headRangeY=").append(headRangeY).append('\n');
            sb.append('\n');

            sb.append("# ── Advanced: Head Attraction (totem spawn suppression) ──\n");
            sb.append("#headAttract.enabled=").append(headAttractEnabled).append('\n');
            sb.append("#headAttract.nearBlocks=").append(headAttractNearBlocks).append('\n');
            sb.append(pHead).append("headAttract.maxRadius=").append(headAttractMaxRadius).append('\n');
            sb.append(pHead).append("headAttract.lambda=").append(headAttractLambda).append('\n');
            sb.append('\n');

            sb.append("# ── Advanced: Cache & Performance ──\n");
            sb.append("#cache.l1TtlMs=").append(l1TtlMs).append('\n');
            sb.append("#cache.resultTtlMs=").append(resultTtlMs).append('\n');
            sb.append("#cache.clockPersistTicks=").append(clockPersistTicks).append('\n');
            sb.append('\n');

            sb.append("# ── Advanced: UI ──\n");
            sb.append("#ui.detectorAnimationTicks=").append(detectorAnimationTicks).append('\n');
            sb.append("#ui.detectorCooldownTicks=").append(detectorCooldownTicks).append('\n');

            Files.writeString(file, sb.toString());
        } catch (IOException ignored) {
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════

    /** Snap detection range to nearest valid step: odd chunk count × 16. */
    static int snapDetectionRange(int blocks) {
        // Valid values: 112, 144, 176, ..., 496 (step 32)
        int clamped = Math.max(112, Math.min(496, blocks));
        return ((clamped - 112 + 16) / 32) * 32 + 112;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static boolean approxEq(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    private static boolean parseBoolean(String v, boolean def) {
        if (v == null) return def;
        return "true".equalsIgnoreCase(v.trim());
    }

    private static int parseInt(String v, int def) {
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static long parseLong(String v, long def) {
        if (v == null) return def;
        try { return Long.parseLong(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }

    private static double parseDouble(String v, double def) {
        if (v == null) return def;
        try { return Double.parseDouble(v.trim()); }
        catch (NumberFormatException e) { return def; }
    }
}
