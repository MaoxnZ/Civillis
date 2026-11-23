package civil.config;

import civil.CivilPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Centralized tunable parameters for the Civil mod.
 *
 * <p>Two layers of configuration:
 * <ol>
 *   <li><b>Simple</b> ({@code simple.*}): values edited in the Cloth GUI, forward-mapped to internal params via
 *       {@link #computeInternalFromSimple()}.</li>
 *   <li><b>Advanced</b> ({@code advanced.*}): file-only tuning and manual overrides of computed internals.
 *       Uncomment a line in {@code civil.properties} to apply it.</li>
 * </ol>
 *
 * <p>Load order: optional schema migration resetting {@code simple.*} fields → {@link #loadSimpleFromProperties}
 * → snapshot → {@link #computeInternalFromSimple()} → {@link #loadAdvancedFromProperties} → raw-override detection.
 *
 * <p><b>Advanced “activation”:</b> Only uncommented {@code advanced.*} lines are loaded by {@link Properties}.
 * {@link #refreshAdvancedKeyActiveFlags} records which keys were present; {@link #save()} keeps those keys
 * uncommented on rewrite so manual edits are not stripped.
 */
public final class CivilConfig {

    private static final String FILE_NAME = "civil.properties";
    private static final Logger LOGGER = LoggerFactory.getLogger("civil");

    /** Last-released mod version string stored when {@link #save()} runs; drives simple-settings migration. */
    private static final String KEY_CONFIG_SIMPLE_SCHEMA_VERSION = "config.simpleSchemaVersion";
    /**
     * When {@code true}, simple GUI keys are not reset when {@link #KEY_CONFIG_SIMPLE_SCHEMA_VERSION} differs from the
     * current jar. Uncomment this line in civil.properties to enable (default absent/false).
     */
    private static final String KEY_SIMPLE_PERSIST_ACROSS_SCHEMA = "simple.persistAcrossSchema";

    /** Ship-time: allow jar vs {@code config.simpleSchemaVersion} mismatch to reset {@code simple.*} unless {@code simple.persistAcrossSchema}. First run (no key) still resets. Not in properties. */
    public static final boolean resetSimpleGuiOnJarSchemaMismatchThisRelease = false;

    /** Keys that appeared as uncommented {@code advanced.*} entries in the last loaded file (see {@link #load}). */
    private static final Set<String> advancedKeysActiveInFile = new HashSet<>();

    @FunctionalInterface
    private interface EntryWriter {
        String value();
    }

    @FunctionalInterface
    private interface EntryLoader {
        void load(Properties p, String key);
    }

    @FunctionalInterface
    private interface IntSetter {
        void set(int value);
    }

    @FunctionalInterface
    private interface LongSetter {
        void set(long value);
    }

    @FunctionalInterface
    private interface DoubleSetter {
        void set(double value);
    }

    @FunctionalInterface
    private interface BooleanSetter {
        void set(boolean value);
    }

    @FunctionalInterface
    private interface IntValueParser {
        int parse(String raw, int current);
    }

    @FunctionalInterface
    private interface LongValueParser {
        long parse(String raw, long current);
    }

    @FunctionalInterface
    private interface DoubleValueParser {
        double parse(String raw, double current);
    }

    private record ConfigEntry(String key, EntryWriter writer, EntryLoader loader) {
    }

    private record ConfigSection(String title, ConfigEntry[] entries) {
    }

    // ══════════════════════════════════════════════════════════
    //  User-facing simple params (GUI sliders)
    // ══════════════════════════════════════════════════════════

    /** Freshness duration: hours before decay starts. Range [1, 48], default 6. */
    public static int simpleFreshnessDuration = 6;

    /** Master toggle for civilization decay (outerSum freshness factor). */
    public static boolean decayEnabled = true;

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

    /** Patrol influence range: 2-8 slider (VC radius), val×16 = blocks. Default 3 → 48 blocks. */
    public static int simplePatrolRange = 3;

    /** Farm shrine suppression strength: 1 (weak) to 10 (strong), default 5. Maps to farmShrineAttractLambda. */
    public static int simpleShrineSuppressStrength = 5;

    /** Farm shrine suppression range: 3-10 slider, val×16 = blocks. Default 8 → 128 blocks. Maps to farmShrineAttractMaxRadius. */
    public static int simpleShrineSuppressRange = 8;

    /** Whether the protection aura visualization is enabled (sonar scan, particles, walls, sounds). */
    public static boolean auraEffectEnabled = true;

    /** Whether the civil undying anchor save is enabled (teleport + totem effects on near-death). */
    public static boolean undyingAnchorEnabled = true;

    /** Fraction of (1.0 - greenLine) to advance beyond greenLine for undying anchor validity.
     * 0.8 = "80% toward full score from greenLine". Formula: greenLine + (1 - greenLine) * civRatio.
     * See {@link #getUndyingAnchorCivRequired()}. */
    public static double undyingAnchorCivRatio = 0.8;

    /** Global cooldown in seconds after rescue before anchor can save again. */
    public static int undyingAnchorGlobalCooldownSeconds = 10;

    /**
     * Max search radius in blocks (3D Euclidean) when looking for a valid anchor on death.
     * Range [32, 256] step 16 (same discrete pattern as {@link #snapUndyingAnchorMaxSearchRadiusBlocks}).
     */
    public static int undyingAnchorMaxSearchRadius = 128;

    /** Civilization score required for undying anchor: greenLine + (1 - greenLine) * civRatio.
     * Aligns with Mob Flee's "过半逃兵" philosophy: anchor needs stricter than greenLine, softer than 1.0. */
    public static double getUndyingAnchorCivRequired() {
        double greenLine = spawnThresholdMid;
        return greenLine + (1.0 - greenLine) * undyingAnchorCivRatio;
    }

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
    public static final int PARAM_SHRINE_SUPPRESS = 6;
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
    private static int loadedSimpleShrineSuppressStrength;
    private static int loadedSimpleShrineSuppressRange;

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
        if (simpleShrineSuppressStrength != loadedSimpleShrineSuppressStrength
                || simpleShrineSuppressRange != loadedSimpleShrineSuppressRange) {
            rawOverrides[PARAM_SHRINE_SUPPRESS] = false;
            loadedSimpleShrineSuppressStrength = simpleShrineSuppressStrength;
            loadedSimpleShrineSuppressRange = simpleShrineSuppressRange;
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
    /** Farm shrine bypass window half-extent in voxel chunks (±N from anchor VC), default 1/1/0. */
    public static int farmShrineRangeX = 1;
    public static int farmShrineRangeZ = 1;
    public static int farmShrineRangeY = 0;

    // -- Patrol Influence (player presence keeping area alive) --
    /** Patrol radius (voxel chunks). User slider sets X/Z together; Y defaults to 1. */
    public static int patrolRadiusX = 3;
    public static int patrolRadiusZ = 3;
    public static int patrolRadiusY = 1;

    // -- Result prefetch queue (CFR 1.2.2: round-robin + epoch receipts) --
    /** Samples required before zone HUD treats center as civilized for enter transition. */
    public static int requiredCountEnter = 10;
    /** Samples required before zone HUD treats leaving civilized (hysteresis). */
    public static int requiredCountLeave = 10;
    /**
     * Zone HUD receipt strictness ratio in [0, 1].
     * A prefetch sample increments {@code civilizedCount} only if:
     * strict = spawnThresholdMid + (1 - spawnThresholdMid) * ratio.
     * Center sample {@code centerCivilized} still uses {@link #spawnThresholdMid}.
     */
    public static double zoneReceiptStrongCivilizedRatio = 0.9999;
    /** Max result shards visited per server tick (global budget, split across active queue owners). */
    public static int resultBudgetPerTick = 1000;
    /** Drop queued prefetch tasks older than this many seconds (epoch TTL). */
    public static int resultEpochTtlSec = 2;
    /** Result raw score threshold for staging presence writes vs deletes (CFR prefetch). */
    public static double presenceRawEpsilon = 0.01;

    // -- Presence keepalive (datapack civil_presence_keepalive: entity vicinity anti-decay) --
    /** Ticks between enqueue waves scanning configured entity types (default ~600s). */
    public static int keepaliveIntervalTicks = 12000;
    /** Max keepalive tasks consumed per server tick (getCScoreAt + visitAt per VC). */
    public static int keepaliveBudgetPerTick = 10;
    /** Max queued tasks; after shuffle, trim head excess when exceeded. */
    public static int keepaliveQueueCap = 150000;

    // -- Farm shrine spawn suppression (attract radius around activated anchors) --
    public static double farmShrineAttractMaxRadius = 128.0;
    public static double farmShrineAttractLambda = 0.15;

    // -- Town center (Phase A + Profile) --
    public static int townCenterMaxCount = 1000;
    public static int townCenterMinSpacingHorizontal = 11;
    public static int townCenterMinSpacingVertical = 3;
    public static double baseScoreGlobalCap = 2.0;
    public static double baseScoreGlobalFloor = -2.0;
    public static int townCenterShutdownTicks = 1200;
    public static int townCenterZoneBuffDurationTicks = 300;
    public static int townCenterZoneBuffRefreshTicks = 200;
    public static int townCenterSpoilDurationTicks = 2400;

    // -- Cache & Performance --
    /** L1 information shard TTL. Short-lived: only needs to survive until ResultEntry is computed.
     *  Prefetcher refreshes on VC change; stationary players rely on ResultEntry for spawn checks. */
    public static long l1TtlMs = 5 * 60 * 1000L;         // 5 minutes
    /** ResultEntry TTL. Long-lived: user-facing cache for O(1) spawn checks + decay tracking. */
    public static long resultTtlMs = 60 * 60 * 1000L;    // 60 minutes
    public static int  clockPersistTicks = 6000;

    // -- Mob Flee AI --
    /** Master toggle for mob flee behavior in civilized areas. */
    public static boolean mobFleeEnabled = true;

    /**
     * Master toggle for datapack-driven entity presence keepalive (civil_presence_keepalive:
     * periodic getCScoreAt + visitAt near listed entity types). Independent from player prefetch.
     */
    public static boolean presenceKeepaliveEnabled = true;
    /** Ratio of (greenLine → 1.0) interval where combat panic begins. 0.5 = halfway. */
    public static double mobFleeCombatFleeRatio = 0.5;
    /** Base interval (ticks) between flee evaluations per mob. */
    public static int mobFleeCheckIntervalTicks = 80;
    /** Random jitter added to check interval (ticks) for desynchronization. */
    public static int mobFleeJitterTicks = 40;
    /** Max duration of a combat panic burst (ticks). */
    public static int mobFleePanicDurationTicks = 80;
    /** Max duration of an idle flee movement (ticks). */
    public static int mobFleeMaxDurationTicks = 200;
    /** Movement speed multiplier during flee. 1.0 = normal walk speed. */
    public static double mobFleeSpeed = 1.0;
    /** Block distance for 8-direction gradient sampling. */
    public static int mobFleeSampleDistance = 14;

    // -- Sonar System (independent from detectionRadius) --
    /** Whether the detector sonar visualization is enabled (user-facing toggle). */
    public static boolean detectorSonarEnabled = true;
    /** Detector sonar scan radius in voxel chunk units (GUI range: 3-7). */
    public static int sonarDetectorRadius = 5;
    /** Bell (static) sonar scan radius in voxel chunk units (GUI range: 8-12). */
    public static int sonarStaticRadius = 10;
    /** Bell sonar cooldown in ticks (per player). */
    public static int sonarStaticCooldownTicks = 40;

    // -- UI / Items --
    /** Zone semantic transition HUD (structure caution / civilized overlay). */
    public static boolean zoneTransitionHudEnabled = true;
    /**
     * When non-blank (after {@link #sanitizeZoneTransitionLabel}), overrides lang for the civilized HUD line.
     * Keys {@code civil.hud.zone_transition.civilized} etc. when left empty.
     */
    public static String zoneTransitionLabelCivilized = "";
    public static String zoneTransitionLabelWilderness = "";
    public static String zoneTransitionLabelCaution = "";
    /** When non-blank, overrides lang for the farm-shrine (bypass) HUD line. */
    public static String zoneTransitionLabelShrine = "";
    /**
     * Minimum seconds between starting two zone HUD displays; 0 = no limit. Elapsed time is checked on the
     * client when a payload arrives; if still within the window the update is skipped (no queue).
     */
    public static int zoneTransitionHudCooldownSeconds = 10;
    /**
     * HUD anchor (cluster center) offset from screen center along X: percent of <b>full window width</b>.
     * Right positive, left negative; ±50 puts the anchor on the left/right edge (centered on that edge).
     */
    public static int zoneTransitionHudAnchorOffsetXPercent = 0;
    /**
     * HUD anchor offset from screen center along Y: percent of <b>full window height</b>.
     * Up positive, down negative (screen Y increases downward); ±50 puts the anchor on the top/bottom edge center.
     * Default 30 matches the legacy placement (~20% from top of screen, horizontally centered).
     */
    public static int zoneTransitionHudAnchorOffsetYPercent = 30;
    /**
     * Overall ZoneTransition HUD scale in percent. 100 = current built-in visual size.
     * Applies uniformly to text and decorative bars.
     */
    public static int zoneTransitionHudFontScalePercent = 100;
    public static int detectorAnimationTicks = 40;
    public static int detectorCooldownTicks = 10;

    // -- Civil map tint (baked into map palette on server) --
    /** HIGH area fill: lerp strength from terrain toward white [0,255]. */
    public static int mapTintHighFillAlpha = 65;
    /**
     * HIGH region border: uniform parchment→white blend strength [0,255]. Borders ignore underlying
     * map color so palette quantization stays consistent along edges.
     */
    public static int mapTintHighEdgeAlpha = 150;
    /** MONSTER area fill: lerp strength from terrain toward purple [0,255]. */
    public static int mapTintMonsterFillAlpha = 60;
    /**
     * MONSTER region border: uniform neutral→purple blend strength [0,255] (same rationale as HIGH edges).
     */
    public static int mapTintMonsterEdgeAlpha = 145;
    /** ZONE (structure caution) fill: lerp strength toward orange [0, 255]. */
    public static int mapTintZoneFillAlpha = 62;
    /** ZONE region border: uniform neutral → orange [0, 255]. */
    public static int mapTintZoneEdgeAlpha = 140;

    private static final ConfigSection[] SIMPLE_SECTIONS = new ConfigSection[] {
            section("Spawn & detection",
                    intEntry("simple.spawn.suppressionStrength", () -> simpleSpawnSuppression,
                            v -> simpleSpawnSuppression = Math.max(1, Math.min(10, v))),
                    intEntry("simple.detection.rangeBlocks", () -> simpleDetectionRange,
                            v -> simpleDetectionRange = snapDetectionRange(v))),
            section("Sonar",
                    boolEntry("simple.sonar.detectorEnabled", () -> detectorSonarEnabled,
                            v -> detectorSonarEnabled = v),
                    intEntry("simple.sonar.detectorRadius", () -> sonarDetectorRadius,
                            v -> sonarDetectorRadius = Math.max(3, Math.min(7, v))),
                    intEntry("simple.sonar.staticRadius", () -> sonarStaticRadius,
                            v -> sonarStaticRadius = Math.max(8, Math.min(12, v)))),
            section("Decay",
                    boolEntry("simple.decay.enabled", () -> decayEnabled,
                            v -> decayEnabled = v),
                    intEntry("simple.decay.speed", () -> simpleDecaySpeed,
                            v -> simpleDecaySpeed = Math.max(1, Math.min(10, v))),
                    intEntry("simple.decay.floor", () -> simpleDecayFloor,
                            v -> simpleDecayFloor = Math.max(0, Math.min(50, v))),
                    intEntry("simple.recovery.speed", () -> simpleRecoverySpeed,
                            v -> simpleRecoverySpeed = Math.max(1, Math.min(10, v))),
                    intEntry("simple.decay.freshnessHours", () -> simpleFreshnessDuration,
                            v -> simpleFreshnessDuration = Math.max(1, Math.min(48, v))),
                    intEntry("simple.patrol.rangeVchunks", () -> simplePatrolRange,
                            v -> simplePatrolRange = Math.max(2, Math.min(8, v)))),
            section("Farm shrine",
                    intEntry("simple.farmShrine.suppressStrength", () -> simpleShrineSuppressStrength,
                            v -> simpleShrineSuppressStrength = Math.max(1, Math.min(10, v))),
                    intEntry("simple.farmShrine.suppressRange", () -> simpleShrineSuppressRange,
                            v -> simpleShrineSuppressRange = Math.max(3, Math.min(10, v)))),
            section("Undying anchor",
                    boolEntry("simple.undying.enabled", () -> undyingAnchorEnabled,
                            v -> undyingAnchorEnabled = v),
                    intEntry("simple.undying.maxSearchRadiusBlocks", () -> undyingAnchorMaxSearchRadius,
                            v -> undyingAnchorMaxSearchRadius = snapUndyingAnchorMaxSearchRadiusBlocks(v)),
                    intEntry("simple.undying.globalCooldownSeconds", () -> undyingAnchorGlobalCooldownSeconds,
                            v -> undyingAnchorGlobalCooldownSeconds = Math.max(1, Math.min(300, v)))),
            section("Zone HUD",
                    boolEntry("simple.zoneHud.transitionEnabled", () -> zoneTransitionHudEnabled,
                            v -> zoneTransitionHudEnabled = v),
                    stringEntry("simple.zoneHud.label.civilized", () -> zoneTransitionLabelCivilized,
                            v -> zoneTransitionLabelCivilized = sanitizeZoneTransitionLabel(v)),
                    stringEntry("simple.zoneHud.label.wilderness", () -> zoneTransitionLabelWilderness,
                            v -> zoneTransitionLabelWilderness = sanitizeZoneTransitionLabel(v)),
                    stringEntry("simple.zoneHud.label.caution", () -> zoneTransitionLabelCaution,
                            v -> zoneTransitionLabelCaution = sanitizeZoneTransitionLabel(v)),
                    stringEntry("simple.zoneHud.label.shrine", () -> zoneTransitionLabelShrine,
                            v -> zoneTransitionLabelShrine = sanitizeZoneTransitionLabel(v)),
                    intEntry("simple.zoneHud.cooldownSeconds", () -> zoneTransitionHudCooldownSeconds,
                            v -> zoneTransitionHudCooldownSeconds = Math.max(0, Math.min(60, v))),
                    intEntry("simple.zoneHud.anchorOffsetXPercent", () -> zoneTransitionHudAnchorOffsetXPercent,
                            v -> zoneTransitionHudAnchorOffsetXPercent = Math.max(-50, Math.min(50, v))),
                    intEntry("simple.zoneHud.anchorOffsetYPercent", () -> zoneTransitionHudAnchorOffsetYPercent,
                            v -> zoneTransitionHudAnchorOffsetYPercent = Math.max(-50, Math.min(50, v))),
                    intEntry("simple.zoneHud.fontScalePercent", () -> zoneTransitionHudFontScalePercent,
                            v -> zoneTransitionHudFontScalePercent = Math.max(50, Math.min(500, v)))),
            section("Mob flee",
                    boolEntry("simple.mobFlee.enabled", () -> mobFleeEnabled,
                            v -> mobFleeEnabled = v)),
            section("Presence keepalive",
                    boolEntry("simple.presenceKeepalive.enabled", () -> presenceKeepaliveEnabled,
                            v -> presenceKeepaliveEnabled = v)),
    };

    private static final ConfigSection[] ADVANCED_SECTIONS = new ConfigSection[] {
            section("Diagnostics / effects",
                    boolEntry("advanced.diagnostics.tps.enabled", () -> tpsLogEnabled,
                            v -> tpsLogEnabled = v),
                    intEntry("advanced.diagnostics.tps.intervalTicks", () -> tpsLogIntervalTicks,
                            v -> tpsLogIntervalTicks = Math.max(1, Math.min(1000, v))),
                    boolEntry("advanced.aura.enabled", () -> auraEffectEnabled,
                            v -> auraEffectEnabled = v),
                    doubleEntry("advanced.undying.civRatio", () -> undyingAnchorCivRatio,
                            v -> undyingAnchorCivRatio = Math.max(0.0, Math.min(1.0, v))),
                    intEntry("advanced.sonar.staticCooldownTicks", () -> sonarStaticCooldownTicks,
                            v -> sonarStaticCooldownTicks = v)),
            section("Decay & recovery overrides",
                    doubleEntry("advanced.decay.gracePeriodHours", () -> gracePeriodHours,
                            v -> gracePeriodHours = v),
                    doubleEntry("advanced.decay.lambda", () -> decayLambda,
                            v -> decayLambda = v),
                    doubleEntry("advanced.decay.minFloor", () -> minDecayFloor,
                            v -> minDecayFloor = v),
                    longEntry("advanced.recovery.cooldownMs", () -> recoveryCooldownMs,
                            v -> recoveryCooldownMs = v),
                    doubleEntry("advanced.recovery.fraction", () -> recoveryFraction,
                            v -> recoveryFraction = v),
                    longEntry("advanced.recovery.minMs", () -> minRecoveryMs,
                            v -> minRecoveryMs = v)),
            section("Spawn thresholds",
                    doubleEntry("advanced.spawn.thresholdLow", () -> spawnThresholdLow,
                            v -> spawnThresholdLow = v),
                    doubleEntry("advanced.spawn.thresholdMid", () -> spawnThresholdMid,
                            v -> spawnThresholdMid = v)),
            section("Scoring",
                    doubleEntry("advanced.scoring.sigmoidMid", () -> sigmoidMid,
                            v -> sigmoidMid = v),
                    doubleEntry("advanced.scoring.sigmoidSteepness", () -> sigmoidSteepness,
                            v -> sigmoidSteepness = v),
                    doubleEntry("advanced.scoring.distanceAlphaSq", () -> distanceAlphaSq,
                            v -> distanceAlphaSq = v),
                    doubleEntry("advanced.scoring.normalizationFactor", () -> normalizationFactor,
                            v -> normalizationFactor = v)),
            section("Detection / influence ranges (voxel chunks)",
                    intEntry("advanced.range.detectionRadiusX", () -> detectionRadiusX,
                            v -> detectionRadiusX = v),
                    intEntry("advanced.range.detectionRadiusZ", () -> detectionRadiusZ,
                            v -> detectionRadiusZ = v),
                    intEntry("advanced.range.detectionRadiusY", () -> detectionRadiusY,
                            v -> detectionRadiusY = v),
                    intEntry("advanced.range.coreRadiusX", () -> coreRadiusX,
                            v -> coreRadiusX = v),
                    intEntry("advanced.range.coreRadiusZ", () -> coreRadiusZ,
                            v -> coreRadiusZ = v),
                    intEntry("advanced.range.coreRadiusY", () -> coreRadiusY,
                            v -> coreRadiusY = v),
                    intEntry("advanced.range.farmShrineRangeX", () -> farmShrineRangeX,
                            v -> farmShrineRangeX = v),
                    intEntry("advanced.range.farmShrineRangeZ", () -> farmShrineRangeZ,
                            v -> farmShrineRangeZ = v),
                    intEntry("advanced.range.farmShrineRangeY", () -> farmShrineRangeY,
                            v -> farmShrineRangeY = v)),
            section("Farm shrine attract (physics)",
                    doubleEntry("advanced.farmShrine.attractMaxRadius", () -> farmShrineAttractMaxRadius,
                            v -> farmShrineAttractMaxRadius = v),
                    doubleEntry("advanced.farmShrine.attractLambda", () -> farmShrineAttractLambda,
                            v -> farmShrineAttractLambda = v)),
            section("Cache & performance",
                    longEntry("advanced.cache.l1TtlMs", () -> l1TtlMs,
                            v -> l1TtlMs = v),
                    longEntry("advanced.cache.resultTtlMs", () -> resultTtlMs,
                            v -> resultTtlMs = v),
                    intEntry("advanced.cache.clockPersistTicks", () -> clockPersistTicks,
                            v -> clockPersistTicks = v)),
            section("Result prefetch (zone HUD epoch pipeline)",
                    intEntry("advanced.prefetch.requiredCountEnter", () -> requiredCountEnter,
                            v -> requiredCountEnter = Math.max(1, Math.min(256, v))),
                    intEntry("advanced.prefetch.requiredCountLeave", () -> requiredCountLeave,
                            v -> requiredCountLeave = Math.max(1, Math.min(256, v))),
                    intEntry("advanced.prefetch.resultBudgetPerTick", () -> resultBudgetPerTick,
                            v -> resultBudgetPerTick = Math.max(0, Math.min(200000, v))),
                    intEntry("advanced.prefetch.resultEpochTtlSec", () -> resultEpochTtlSec,
                            v -> resultEpochTtlSec = Math.max(0, Math.min(60, v))),
                    doubleEntry("advanced.prefetch.presenceRawEpsilon", () -> presenceRawEpsilon,
                            v -> presenceRawEpsilon = Math.max(0.0, Math.min(1_000_000.0, v))),
                    doubleEntry("advanced.prefetch.zoneReceiptStrongCivilizedRatio", () -> zoneReceiptStrongCivilizedRatio,
                            v -> zoneReceiptStrongCivilizedRatio = Math.max(0.0, Math.min(1.0, v)))),
            section("Presence keepalive (entity anti-decay sweep)",
                    intEntry("advanced.keepalive.intervalTicks", () -> keepaliveIntervalTicks,
                            v -> keepaliveIntervalTicks = Math.max(1, Math.min(72000, v))),
                    intEntry("advanced.keepalive.budgetPerTick", () -> keepaliveBudgetPerTick,
                            v -> keepaliveBudgetPerTick = Math.max(0, Math.min(200000, v))),
                    intEntry("advanced.keepalive.queueCap", () -> keepaliveQueueCap,
                            v -> keepaliveQueueCap = Math.max(64, Math.min(500_000, v)))),
            section("Mob flee (parameters other than simple toggle)",
                    doubleEntry("advanced.mobFlee.combatFleeRatio", () -> mobFleeCombatFleeRatio,
                            v -> mobFleeCombatFleeRatio = v),
                    intEntry("advanced.mobFlee.checkIntervalTicks", () -> mobFleeCheckIntervalTicks,
                            v -> mobFleeCheckIntervalTicks = v),
                    intEntry("advanced.mobFlee.jitterTicks", () -> mobFleeJitterTicks,
                            v -> mobFleeJitterTicks = v),
                    intEntry("advanced.mobFlee.panicDurationTicks", () -> mobFleePanicDurationTicks,
                            v -> mobFleePanicDurationTicks = v),
                    intEntry("advanced.mobFlee.maxDurationTicks", () -> mobFleeMaxDurationTicks,
                            v -> mobFleeMaxDurationTicks = v),
                    doubleEntry("advanced.mobFlee.speed", () -> mobFleeSpeed,
                            v -> mobFleeSpeed = v),
                    intEntry("advanced.mobFlee.sampleDistance", () -> mobFleeSampleDistance,
                            v -> mobFleeSampleDistance = v)),
            section("Detector item timing",
                    intEntry("advanced.ui.detectorAnimationTicks", () -> detectorAnimationTicks,
                            v -> detectorAnimationTicks = v),
                    intEntry("advanced.ui.detectorCooldownTicks", () -> detectorCooldownTicks,
                            v -> detectorCooldownTicks = v)),
            section("Civil map tint (0-255; baked server-side)",
                    intEntry("advanced.mapTint.highFillAlpha", () -> mapTintHighFillAlpha,
                            v -> mapTintHighFillAlpha = Math.max(0, Math.min(255, v))),
                    intEntry("advanced.mapTint.highEdgeAlpha", () -> mapTintHighEdgeAlpha,
                            v -> mapTintHighEdgeAlpha = Math.max(0, Math.min(255, v))),
                    intEntry("advanced.mapTint.monsterFillAlpha", () -> mapTintMonsterFillAlpha,
                            v -> mapTintMonsterFillAlpha = Math.max(0, Math.min(255, v))),
                    intEntry("advanced.mapTint.monsterEdgeAlpha", () -> mapTintMonsterEdgeAlpha,
                            v -> mapTintMonsterEdgeAlpha = Math.max(0, Math.min(255, v))),
                    intEntry("advanced.mapTint.zoneFillAlpha", () -> mapTintZoneFillAlpha,
                            v -> mapTintZoneFillAlpha = Math.max(0, Math.min(255, v))),
                    intEntry("advanced.mapTint.zoneEdgeAlpha", () -> mapTintZoneEdgeAlpha,
                            v -> mapTintZoneEdgeAlpha = Math.max(0, Math.min(255, v)))),
            section("Town center profile",
                    intEntry("advanced.townCenter.shutdownTicks", () -> townCenterShutdownTicks,
                            v -> townCenterShutdownTicks = Math.max(0, v)),
                    intEntry("advanced.townCenter.zoneBuffDurationTicks", () -> townCenterZoneBuffDurationTicks,
                            v -> townCenterZoneBuffDurationTicks = Math.max(1, v)),
                    intEntry("advanced.townCenter.zoneBuffRefreshTicks", () -> townCenterZoneBuffRefreshTicks,
                            v -> townCenterZoneBuffRefreshTicks = Math.max(1, v)),
                    intEntry("advanced.townCenter.spoilDurationTicks", () -> townCenterSpoilDurationTicks,
                            v -> townCenterSpoilDurationTicks = Math.max(1, v))),
    };

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
     * Compute internal params from {@code simple.*} values mapped by the Cloth GUI.
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

        // 7-8. Farm shrine suppression strength & range
        if (!rawOverrides[PARAM_SHRINE_SUPPRESS]) {
            farmShrineAttractLambda = 0.03 * simpleShrineSuppressStrength;
            farmShrineAttractMaxRadius = simpleShrineSuppressRange * 16.0;
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
        Path dir = CivilPlatform.getConfigDir();
        Path file = dir.resolve(FILE_NAME);
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(reader);
            } catch (IOException e) {
                // Ignore, use default values
            }
        }
        refreshAdvancedKeyActiveFlags(p);

        String diskSimpleSchema = p.getProperty(KEY_CONFIG_SIMPLE_SCHEMA_VERSION);
        boolean persistSimpleAcrossSchema = parseBoolean(p.getProperty(KEY_SIMPLE_PERSIST_ACROSS_SCHEMA), false);
        String currentJarVersion = CivilPlatform.getReleasedModVersion();

        boolean needResetSimple;
        if (diskSimpleSchema == null) {
            needResetSimple = true;
        } else if (diskSimpleSchema.equals(currentJarVersion)) {
            needResetSimple = false;
        } else {
            needResetSimple = !persistSimpleAcrossSchema && resetSimpleGuiOnJarSchemaMismatchThisRelease;
        }

        if (needResetSimple) {
            LOGGER.info(
                    "[civil] Applying built-in GUI simple defaults (schema migration: diskSchema={} current={} persist={})",
                    diskSimpleSchema,
                    currentJarVersion,
                    persistSimpleAcrossSchema);
            resetSimpleParamsToBuiltInDefaults();
            applySimpleParamClamps();
        } else {
            loadSimpleFromProperties(p);
        }

        // Snapshot simple values for change detection in GUI save flow
        loadedSimpleFreshness           = simpleFreshnessDuration;
        loadedSimpleDecaySpeed          = simpleDecaySpeed;
        loadedSimpleDecayFloor          = simpleDecayFloor;
        loadedSimpleRecoverySpeed       = simpleRecoverySpeed;
        loadedSimpleSpawnSuppression    = simpleSpawnSuppression;
        loadedSimpleDetectionRange      = simpleDetectionRange;
        loadedSimpleShrineSuppressStrength = simpleShrineSuppressStrength;
        loadedSimpleShrineSuppressRange = simpleShrineSuppressRange;

        // Reset override flags (important if load() is called more than once)
        java.util.Arrays.fill(rawOverrides, false);

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
        double compFarmShrineAttractLambda = farmShrineAttractLambda;
        double compFarmShrineAttractMaxRad = farmShrineAttractMaxRadius;

        loadAdvancedFromProperties(p);

        // Detect raw overrides relative to simple-computed internals
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
        rawOverrides[PARAM_SHRINE_SUPPRESS] = !approxEq(farmShrineAttractLambda, compFarmShrineAttractLambda)
                                        || !approxEq(farmShrineAttractMaxRadius, compFarmShrineAttractMaxRad);

        if (!Files.isRegularFile(file) || needResetSimple) {
            save();
        }
    }

    /** Load {@code simple.*} keys from disk (skipped when schema migration resets simple fields). */
    private static void loadSimpleFromProperties(Properties p) {
        loadSections(p, SIMPLE_SECTIONS);
    }

    /** Same literals as static field initializers for every Cloth-controlled field in this class. */
    private static void resetSimpleParamsToBuiltInDefaults() {
        simpleFreshnessDuration = 6;
        decayEnabled = true;
        simpleDecaySpeed = 5;
        simpleDecayFloor = 25;
        simpleRecoverySpeed = 5;
        simpleSpawnSuppression = 5;
        simpleDetectionRange = 240;
        simpleShrineSuppressStrength = 5;
        simpleShrineSuppressRange = 8;
        simplePatrolRange = 3;

        detectorSonarEnabled = true;
        sonarDetectorRadius = 5;
        sonarStaticRadius = 10;

        undyingAnchorEnabled = true;
        undyingAnchorMaxSearchRadius = 128;
        undyingAnchorGlobalCooldownSeconds = 10;

        zoneTransitionHudEnabled = true;
        zoneTransitionLabelCivilized = "";
        zoneTransitionLabelWilderness = "";
        zoneTransitionLabelCaution = "";
        zoneTransitionLabelShrine = "";
        zoneTransitionHudCooldownSeconds = 10;
        zoneTransitionHudAnchorOffsetXPercent = 0;
        zoneTransitionHudAnchorOffsetYPercent = 30;
        zoneTransitionHudFontScalePercent = 100;

        mobFleeEnabled = true;
        presenceKeepaliveEnabled = true;
    }

    private static void applySimpleParamClamps() {
        simpleFreshnessDuration = Math.max(1, Math.min(48, simpleFreshnessDuration));
        simpleDecaySpeed = Math.max(1, Math.min(10, simpleDecaySpeed));
        simpleDecayFloor = Math.max(0, Math.min(50, simpleDecayFloor));
        simpleRecoverySpeed = Math.max(1, Math.min(10, simpleRecoverySpeed));
        simpleSpawnSuppression = Math.max(1, Math.min(10, simpleSpawnSuppression));
        simpleDetectionRange = snapDetectionRange(simpleDetectionRange);
        simpleShrineSuppressStrength = Math.max(1, Math.min(10, simpleShrineSuppressStrength));
        simpleShrineSuppressRange = Math.max(3, Math.min(10, simpleShrineSuppressRange));
        simplePatrolRange = Math.max(2, Math.min(8, simplePatrolRange));

        sonarDetectorRadius = Math.max(3, Math.min(7, sonarDetectorRadius));
        sonarStaticRadius = Math.max(8, Math.min(12, sonarStaticRadius));
        undyingAnchorGlobalCooldownSeconds = Math.max(1, Math.min(300, undyingAnchorGlobalCooldownSeconds));
        undyingAnchorMaxSearchRadius = snapUndyingAnchorMaxSearchRadiusBlocks(undyingAnchorMaxSearchRadius);
        zoneTransitionHudCooldownSeconds = Math.max(0, Math.min(60, zoneTransitionHudCooldownSeconds));
        zoneTransitionHudAnchorOffsetXPercent = Math.max(-50, Math.min(50, zoneTransitionHudAnchorOffsetXPercent));
        zoneTransitionHudAnchorOffsetYPercent = Math.max(-50, Math.min(50, zoneTransitionHudAnchorOffsetYPercent));
        zoneTransitionHudFontScalePercent = Math.max(50, Math.min(500, zoneTransitionHudFontScalePercent));
    }

    /** Load {@code advanced.*} after {@link #computeInternalFromSimple()} so overrides apply on top of mapping. */
    private static void loadAdvancedFromProperties(Properties p) {
        loadSections(p, ADVANCED_SECTIONS);
    }

    /** Remember which {@code advanced.*} keys were uncommented in the loaded file so {@link #save()} preserves them. */
    private static void refreshAdvancedKeyActiveFlags(Properties p) {
        advancedKeysActiveInFile.clear();
        for (String k : p.stringPropertyNames()) {
            if (k.startsWith("advanced.")) {
                advancedKeysActiveInFile.add(k);
            }
        }
    }

    /** Prefix for an advanced property line: empty if that key was active on disk, otherwise {@code #}. */
    private static String advLinePrefix(String advKey) {
        return advancedKeysActiveInFile.contains(advKey) ? "" : "#";
    }

    // ══════════════════════════════════════════════════════════
    //  Saving
    // ══════════════════════════════════════════════════════════

    /**
     * Write {@code civil.properties} in one pass: {@code simple.*} lines are active (no leading {@code #} on key lines).
     * {@code advanced.*} lines default to commented reference values; keys the user left uncommented on disk stay
     * uncommented across saves ({@link #advancedKeysActiveInFile}).
     */
    public static void save() {
        Path dir = CivilPlatform.getConfigDir();
        Path file = dir.resolve(FILE_NAME);
        try {
            Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder();
            sb.append("# Civil mod configuration\n");
            sb.append("# simple.* : in-game GUI (written uncommented).\n");
            sb.append("# advanced.* : optional tuning (default lines commented; remove leading # to apply).\n\n");

            sb.append("# ── Simple (GUI) ──\n");
            sb.append("# Stored release version for simple-settings migration (jar bump reset gated in CivilConfig; opt-out below).\n");
            sb.append(KEY_CONFIG_SIMPLE_SCHEMA_VERSION).append('=').append(CivilPlatform.getReleasedModVersion()).append('\n');
            sb.append("# Remove leading # on the next line to opt out of simple migration when updating the mod jar.\n");
            sb.append('#').append(KEY_SIMPLE_PERSIST_ACROSS_SCHEMA).append("=true\n");
            sb.append('\n');
            writeSections(sb, SIMPLE_SECTIONS, false);
            sb.append("# ── Advanced ──\n");
            writeSections(sb, ADVANCED_SECTIONS, true);

            Files.writeString(file, sb.toString());
        } catch (IOException ignored) {
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════

    private static ConfigSection section(String title, ConfigEntry... entries) {
        return new ConfigSection(title, entries);
    }

    private static ConfigEntry boolEntry(String key, BooleanSupplier getter, BooleanSetter setter) {
        return new ConfigEntry(
                key,
                () -> Boolean.toString(getter.getAsBoolean()),
                (p, k) -> {
                    String raw = p.getProperty(k);
                    if (raw != null) {
                        setter.set(parseBoolean(raw, getter.getAsBoolean()));
                    }
                });
    }

    private static ConfigEntry intEntry(String key, IntSupplier getter, IntSetter setter) {
        return intEntry(key, getter, setter, CivilConfig::parseInt);
    }

    private static ConfigEntry intEntry(String key, IntSupplier getter, IntSetter setter, IntValueParser parser) {
        return new ConfigEntry(
                key,
                () -> Integer.toString(getter.getAsInt()),
                (p, k) -> {
                    String raw = p.getProperty(k);
                    if (raw != null) {
                        setter.set(parser.parse(raw, getter.getAsInt()));
                    }
                });
    }

    private static ConfigEntry longEntry(String key, LongSupplier getter, LongSetter setter) {
        return longEntry(key, getter, setter, CivilConfig::parseLong);
    }

    private static ConfigEntry longEntry(String key, LongSupplier getter, LongSetter setter, LongValueParser parser) {
        return new ConfigEntry(
                key,
                () -> Long.toString(getter.getAsLong()),
                (p, k) -> {
                    String raw = p.getProperty(k);
                    if (raw != null) {
                        setter.set(parser.parse(raw, getter.getAsLong()));
                    }
                });
    }

    private static ConfigEntry doubleEntry(String key, DoubleSupplier getter, DoubleSetter setter) {
        return doubleEntry(key, getter, setter, CivilConfig::parseDouble);
    }

    private static ConfigEntry doubleEntry(String key, DoubleSupplier getter, DoubleSetter setter, DoubleValueParser parser) {
        return new ConfigEntry(
                key,
                () -> Double.toString(getter.getAsDouble()),
                (p, k) -> {
                    String raw = p.getProperty(k);
                    if (raw != null) {
                        setter.set(parser.parse(raw, getter.getAsDouble()));
                    }
                });
    }

    private static ConfigEntry stringEntry(String key, Supplier<String> getter, Consumer<String> setter) {
        return new ConfigEntry(
                key,
                getter::get,
                (p, k) -> {
                    String raw = p.getProperty(k);
                    if (raw != null) {
                        setter.accept(raw);
                    }
                });
    }

    private static void loadSections(Properties p, ConfigSection[] sections) {
        for (ConfigSection section : sections) {
            for (ConfigEntry entry : section.entries()) {
                entry.loader().load(p, entry.key());
            }
        }
    }

    private static void writeSections(StringBuilder sb, ConfigSection[] sections, boolean advanced) {
        for (ConfigSection section : sections) {
            sb.append("# ").append(section.title()).append('\n');
            for (ConfigEntry entry : section.entries()) {
                if (advanced) {
                    sb.append(advLinePrefix(entry.key()));
                }
                sb.append(entry.key()).append('=').append(entry.writer().value()).append('\n');
            }
            sb.append('\n');
        }
    }

    /** Normalizes HUD label overrides from GUI or civil.properties (no newlines; max length). */
    public static String sanitizeZoneTransitionLabel(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.replace('\r', ' ').replace('\n', ' ').trim();
        return t.length() > 128 ? t.substring(0, 128) : t;
    }

    /** Snap detection range to nearest valid step: odd chunk count × 16. */
    static int snapDetectionRange(int blocks) {
        // Valid values: 112, 144, 176, ..., 496 (step 32)
        int clamped = Math.max(112, Math.min(496, blocks));
        return ((clamped - 112 + 16) / 32) * 32 + 112;
    }

    /**
     * Snap undying anchor search radius to the legal 16-block grid, then clamp to [32, 256].
     * Same structural role as {@link #snapDetectionRange}: limits are encoded here, not as extra fields above.
     */
    static int snapUndyingAnchorMaxSearchRadiusBlocks(int blocks) {
        int clamped = Math.max(32, Math.min(256, blocks));
        int snapped = (int) Math.round(clamped / 16.0) * 16;
        return Math.max(32, Math.min(256, snapped));
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
