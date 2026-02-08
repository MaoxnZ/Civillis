package civil.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * User-friendly Cloth Config GUI for Civil mod settings.
 *
 * <p>Dynamic derived info (half-life, moat width, etc.) is shown directly
 * on the slider face via {@code setTextGetter()}, so users see the impact
 * without hovering.  Individual slider tooltips are intentionally removed
 * to avoid obscuring the slider during interaction; the subcategory header
 * tooltip explains the overall concept instead.
 *
 * <p>Only loaded when both Mod Menu and Cloth Config are present.
 */
public final class CivilConfigScreen {

    /**
     * Estimated full-protection moat width (blocks) beyond a standard village border.
     * Index = suppression strength (1-10). Index 0 is unused padding.
     */
    private static final int[] VILLAGE_MOAT = {0, 0, 8, 15, 20, 25, 35, 45, 55, 65, 80};

    /**
     * Detection range valid block values: 112, 144, 176, ..., 496  (step 32, 13 entries).
     * Slider uses index 0-12, mapped to blocks via this array.
     */
    private static final int[] RANGE_STEPS;
    static {
        RANGE_STEPS = new int[13];
        for (int i = 0; i < 13; i++) {
            RANGE_STEPS[i] = 112 + i * 32;
        }
    }

    /** Convert a block value to the nearest RANGE_STEPS index. */
    private static int rangeToIndex(int blocks) {
        int idx = Math.round((CivilConfig.snapDetectionRange(blocks) - 112) / 32.0f);
        return Math.max(0, Math.min(12, idx));
    }

    private CivilConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("civil.config.title"))
                .setSavingRunnable(() -> {
                    CivilConfig.clearOverridesForChangedSimple();
                    CivilConfig.computeInternalFromSimple();
                    CivilConfig.save();
                });

        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory cat = builder.getOrCreateCategory(
                Text.translatable("civil.config.category.main"));

        // ── 1. Spawn Suppression (1-10) ──
        if (CivilConfig.hasRawOverride(CivilConfig.PARAM_SPAWN)) {
            cat.addEntry(eb.startTextDescription(
                    Text.translatable("civil.config.override.warning")).build());
        }
        cat.addEntry(eb.startIntSlider(
                        Text.translatable("civil.config.simple.spawnSuppression"),
                        CivilConfig.simpleSpawnSuppression, 1, 10)
                .setDefaultValue(5)
                .setTextGetter(val -> {
                    int moat = VILLAGE_MOAT[Math.max(1, Math.min(10, val))];
                    if (val <= 2) {
                        return Text.translatable("civil.config.slider.spawn.unsafe", val, moat);
                    }
                    return Text.translatable("civil.config.slider.spawn", val, moat);
                })
                .setSaveConsumer(v -> CivilConfig.simpleSpawnSuppression = v)
                .build());

        // ── 2. Detection Range (discrete: 13 steps → 112..496 blocks) ──
        if (CivilConfig.hasRawOverride(CivilConfig.PARAM_RANGE)) {
            cat.addEntry(eb.startTextDescription(
                    Text.translatable("civil.config.override.warning")).build());
        }
        cat.addEntry(eb.startIntSlider(
                        Text.translatable("civil.config.simple.detectionRange"),
                        rangeToIndex(CivilConfig.simpleDetectionRange), 0, 12)
                .setDefaultValue(rangeToIndex(240))
                .setTextGetter(idx -> {
                    int blocks = RANGE_STEPS[Math.max(0, Math.min(12, idx))];
                    return Text.translatable("civil.config.slider.range", blocks, blocks);
                })
                .setSaveConsumer(idx -> CivilConfig.simpleDetectionRange = RANGE_STEPS[Math.max(0, Math.min(12, idx))])
                .build());

        // ── 3. Decay Details (collapsible) ──
        SubCategoryBuilder decay = eb.startSubCategory(
                Text.translatable("civil.config.subcategory.decay"));
        decay.setExpanded(false);
        decay.setTooltip(
                Text.translatable("civil.config.subcategory.decay.tooltip.1"),
                Text.translatable("civil.config.subcategory.decay.tooltip.2"),
                Text.translatable("civil.config.subcategory.decay.tooltip.3"));

        // 3a. Decay Speed (1-10)
        if (CivilConfig.hasRawOverride(CivilConfig.PARAM_DECAY_SPEED)) {
            decay.add(eb.startTextDescription(
                    Text.translatable("civil.config.override.warning")).build());
        }
        decay.add(eb.startIntSlider(
                        Text.translatable("civil.config.simple.decaySpeed"),
                        CivilConfig.simpleDecaySpeed, 1, 10)
                .setDefaultValue(5)
                .setTextGetter(val -> {
                    double lambda = 0.001 * Math.exp(0.52 * (val - 1));
                    double halfLifeHours = Math.log(2) / lambda;
                    if (halfLifeHours >= 48) {
                        String hl = String.format("%.1f", halfLifeHours / 24.0);
                        return Text.translatable("civil.config.slider.decay.days", val, hl);
                    } else {
                        String hl = String.format("%.1f", halfLifeHours);
                        return Text.translatable("civil.config.slider.decay.hours", val, hl);
                    }
                })
                .setSaveConsumer(v -> CivilConfig.simpleDecaySpeed = v)
                .build());

        // 3b. Recovery Speed (1-10)
        if (CivilConfig.hasRawOverride(CivilConfig.PARAM_RECOVERY)) {
            decay.add(eb.startTextDescription(
                    Text.translatable("civil.config.override.warning")).build());
        }
        decay.add(eb.startIntSlider(
                        Text.translatable("civil.config.simple.recoverySpeed"),
                        CivilConfig.simpleRecoverySpeed, 1, 10)
                .setDefaultValue(5)
                .setTextGetter(val -> {
                    double t = (val - 1.0) / 9.0;
                    double fraction = 0.05 + (0.50 - 0.05) * t;
                    double cooldownSec = (120_000 + (15_000 - 120_000) * t) / 1000.0;
                    double steps = Math.log(24) / -Math.log(1 - fraction);
                    int totalMin = (int) Math.ceil(steps * cooldownSec / 60.0);
                    return Text.translatable("civil.config.slider.recovery", val, totalMin);
                })
                .setSaveConsumer(v -> CivilConfig.simpleRecoverySpeed = v)
                .build());

        // 3c. Decay Floor (0-50 %)
        if (CivilConfig.hasRawOverride(CivilConfig.PARAM_DECAY_FLOOR)) {
            decay.add(eb.startTextDescription(
                    Text.translatable("civil.config.override.warning")).build());
        }
        decay.add(eb.startIntSlider(
                        Text.translatable("civil.config.simple.decayFloor"),
                        CivilConfig.simpleDecayFloor, 0, 50)
                .setDefaultValue(25)
                .setTextGetter(val -> Text.literal(val + "%"))
                .setSaveConsumer(v -> CivilConfig.simpleDecayFloor = v)
                .build());

        // 3d. Freshness Duration (1-48 hours)
        if (CivilConfig.hasRawOverride(CivilConfig.PARAM_FRESHNESS)) {
            decay.add(eb.startTextDescription(
                    Text.translatable("civil.config.override.warning")).build());
        }
        decay.add(eb.startIntSlider(
                        Text.translatable("civil.config.simple.freshnessDuration"),
                        CivilConfig.simpleFreshnessDuration, 1, 48)
                .setDefaultValue(6)
                .setTextGetter(val -> Text.translatable("civil.config.slider.freshness", val))
                .setSaveConsumer(v -> CivilConfig.simpleFreshnessDuration = v)
                .build());

        cat.addEntry(decay.build());

        return builder.build();
    }
}
