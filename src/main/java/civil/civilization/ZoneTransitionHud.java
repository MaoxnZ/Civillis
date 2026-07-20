package civil.civilization;

import civil.CivilMod;
import civil.config.CivilConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Client HUD for zone semantic transitions.
 * <p>
 * Layout uses a screen-centered coordinate system: offset percents are of <b>full</b> width / height;
 * right and up are positive; the HUD cluster (text + bars) is anchored by its visual center. ±50% places
 * that center on the corresponding screen edge (e.g. top center at X=0%, Y=+50%).
 */
@SuppressWarnings("null")
public final class ZoneTransitionHud {
    private static final int FADE_IN_TICKS = 16;
    private static final int HOLD_TICKS = 28;
    private static final int FADE_OUT_TICKS = 16;
    private static final int CIV_RGB = 0xEAF4FF;
    private static final int WILD_RGB = 0x8ECFFF;
    private static final int CAUTION_RGB = 0xFFB78F;
    private static final int SHRINE_RGB = 0xD4A0FF;
    private static final int BAR_WIDTH = 2;
    private static final int BAR_HEIGHT = 12;
    private static final int BAR_GAP = 8;
    private static final int BAR_TRAVEL = 16;
    private static final float BAR_CENTER_Y_BIAS_RATIO = -0.15f;

    private static long latestEpoch = Long.MIN_VALUE;
    /** Wall-clock time when we last started a HUD sequence (after cooldown / epoch checks). */
    private static long lastHudShowStartTimeMs = 0L;
    private static Component currentText = Component.empty();
    private static ZoneSemanticState currentState = ZoneSemanticState.WILDERNESS;
    private static int ticksRemaining = 0;

    private ZoneTransitionHud() {}

    /**
     * Clears monotonic epoch ordering and visible HUD state when the client leaves a world or joins
     * another (same JVM session). Call from connection lifecycle so a new world's smaller
     * {@link ZoneTransitionPayload#epoch()} is not dropped as "stale".
     */
    public static void resetForWorldSession() {
        latestEpoch = Long.MIN_VALUE;
        lastHudShowStartTimeMs = 0L;
        currentText = Component.empty();
        currentState = ZoneSemanticState.WILDERNESS;
        ticksRemaining = 0;
    }

    public static void onPayload(ZoneTransitionPayload payload) {
        if (!CivilConfig.zoneTransitionHudEnabled) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[zone][client] ignore payload: HUD disabled in config (epoch={} stateId={})",
                        payload.epoch(), payload.stateId());
            }
            return;
        }
        int cooldownSec = CivilConfig.zoneTransitionHudCooldownSeconds;
        if (cooldownSec > 0 && lastHudShowStartTimeMs > 0L) {
            long elapsed = System.currentTimeMillis() - lastHudShowStartTimeMs;
            if (elapsed < cooldownSec * 1000L) {
                if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.info(
                            "[zone][client] skip HUD (cooldown): epoch={} stateId={} elapsedMs={} needMs>={}",
                            payload.epoch(),
                            payload.stateId(),
                            elapsed,
                            cooldownSec * 1000L);
                }
                return;
            }
        }
        if (payload.epoch() < latestEpoch) {
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[zone][client] drop stale: payloadEpoch={} latestEpoch={} stateId={} (receipt vs fast ordering)",
                        payload.epoch(), latestEpoch, payload.stateId());
            }
            return;
        }
        if (CivilMod.DEBUG) {
            CivilMod.LOGGER.info("[zone][client] apply HUD: epoch={} state={} (id={})",
                    payload.epoch(), payload.state(), payload.stateId());
        }
        latestEpoch = payload.epoch();
        currentState = payload.state();
        currentText = labelForPayload(payload, currentState);
        ticksRemaining = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;
        lastHudShowStartTimeMs = System.currentTimeMillis();
    }

    private static Component labelForPayload(ZoneTransitionPayload payload, ZoneSemanticState state) {
        String override = payload.labelOverride();
        if (override != null) {
            override = override.trim();
            if (!override.isEmpty()) {
                return Component.literal(override);
            }
        }
        return labelForState(state);
    }

    private static Component labelForState(ZoneSemanticState state) {
        String override =
                switch (state) {
                    case CIVILIZED -> CivilConfig.zoneTransitionLabelCivilized;
                    case WILDERNESS -> CivilConfig.zoneTransitionLabelWilderness;
                    case CAUTION -> CivilConfig.zoneTransitionLabelCaution;
                    case SHRINE -> CivilConfig.zoneTransitionLabelShrine;
                };
        if (override != null) {
            override = override.trim();
            if (!override.isEmpty()) {
                return Component.literal(override);
            }
        }
        return Component.translatable(state.translationKey());
    }

    public static void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
    }

    public static void render(GuiGraphics guiGraphics, float partialTick) {
        if (!CivilConfig.zoneTransitionHudEnabled) return;
        if (ticksRemaining <= 0 || currentText.getString().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null) return;
        int scalePercent = clampInt(CivilConfig.zoneTransitionHudFontScalePercent, 50, 500);
        float scale = scalePercent / 100.0f;

        int total = FADE_IN_TICKS + HOLD_TICKS + FADE_OUT_TICKS;
        float clampedPartial = clamp01(partialTick);
        float elapsed = (total - ticksRemaining) + clampedPartial;
        float inPhase = clamp01(elapsed / FADE_IN_TICKS);
        float outPhase = clamp01((elapsed - FADE_IN_TICKS - HOLD_TICKS) / FADE_OUT_TICKS);
        float alpha;
        if (elapsed < FADE_IN_TICKS) {
            alpha = easeOutCubic(inPhase);
        } else if (elapsed < (FADE_IN_TICKS + HOLD_TICKS)) {
            alpha = 1.0f;
        } else {
            alpha = 1.0f - easeInCubic(outPhase);
        }
        if (alpha <= 0.01f) return;

        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        int ox = clampInt(CivilConfig.zoneTransitionHudAnchorOffsetXPercent, -50, 50);
        int oy = clampInt(CivilConfig.zoneTransitionHudAnchorOffsetYPercent, -50, 50);
        // Screen center origin: right/up positive. Anchor = HUD cluster center (text + bars), in pixels.
        int anchorX = Math.round(width * (0.5f + ox / 100.0f));
        int anchorY = Math.round(height * (0.5f - oy / 100.0f));

        Component textComponent = currentText;
        int textWidth = scalePositive(mc.font.width(textComponent), scale);
        int textHeight = scalePositive(mc.font.lineHeight, scale);
        int barWidth = scalePositive(BAR_WIDTH, scale);
        int barHeight = scalePositive(BAR_HEIGHT, scale);
        int barGap = scalePositive(BAR_GAP, scale);
        int barTravel = scalePositive(BAR_TRAVEL, scale);
        int barCenterBias = Math.round(textHeight * BAR_CENTER_Y_BIAS_RATIO);
        int barCenterBiasMin = Math.min(-1, Math.round(-4.0f * scale));
        barCenterBias = clampInt(barCenterBias, barCenterBiasMin, -1);

        int x = anchorX - textWidth / 2;
        int y = alignBaselineToHudCenterY(anchorY, textHeight, barHeight, barCenterBias);

        int rgb = switch (currentState) {
            case CIVILIZED -> CIV_RGB;
            case WILDERNESS -> WILD_RGB;
            case CAUTION -> CAUTION_RGB;
            case SHRINE -> SHRINE_RGB;
        };
        int color = ((int) (alpha * 255.0f) << 24) | rgb;
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) x, (float) y);
        guiGraphics.pose().scale(scale, scale);
        guiGraphics.drawString(mc.font, textComponent, 0, 0, color, true);
        guiGraphics.pose().popMatrix();

        float inEased = easeOutCubic(inPhase);
        float outEased = easeInCubic(outPhase);
        float leftOffset;
        float rightOffset;
        if (elapsed < FADE_IN_TICKS) {
            leftOffset = -barTravel * (1.0f - inEased);
            rightOffset = barTravel * (1.0f - inEased);
        } else if (elapsed < (FADE_IN_TICKS + HOLD_TICKS)) {
            leftOffset = 0.0f;
            rightOffset = 0.0f;
        } else {
            leftOffset = barTravel * outEased;
            rightOffset = -barTravel * outEased;
        }

        int barColor = ((int) (alpha * 220.0f) << 24) | rgb;
        int barShadowRgb = switch (currentState) {
            case CIVILIZED -> 0x0C1526;
            case WILDERNESS -> 0x0D1A2A;
            case CAUTION -> 0x2A160D;
            case SHRINE -> 0x1A0C26;
        };
        int barShadowColor = ((int) (alpha * 120.0f) << 24) | barShadowRgb;
        int barShadowOffset = Math.max(1, Math.round(scale));
        int baseBarY = y + (textHeight - barHeight) / 2 + barCenterBias;
        int leftX = x - barGap - barWidth;
        int rightX = x + textWidth + barGap;
        int leftY1 = Math.round(baseBarY + leftOffset);
        int rightY1 = Math.round(baseBarY + rightOffset);
        guiGraphics.fill(leftX + barShadowOffset, leftY1 + barShadowOffset,
                leftX + barWidth + barShadowOffset, leftY1 + barHeight + barShadowOffset, barShadowColor);
        guiGraphics.fill(rightX + barShadowOffset, rightY1 + barShadowOffset,
                rightX + barWidth + barShadowOffset, rightY1 + barHeight + barShadowOffset, barShadowColor);
        guiGraphics.fill(leftX, leftY1, leftX + barWidth, leftY1 + barHeight, barColor);
        guiGraphics.fill(rightX, rightY1, rightX + barWidth, rightY1 + barHeight, barColor);
    }

    private static float clamp01(float value) {
        if (value < 0.0f) return 0.0f;
        return Math.min(value, 1.0f);
    }

    private static float easeOutCubic(float t) {
        float x = clamp01(t);
        float oneMinus = 1.0f - x;
        return 1.0f - oneMinus * oneMinus * oneMinus;
    }

    private static float easeInCubic(float t) {
        float x = clamp01(t);
        return x * x * x;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int scalePositive(int baseValue, float scale) {
        return Math.max(1, Math.round(baseValue * scale));
    }

    /** Vertical center (integer) of text + decorative bars for a given text baseline. */
    private static int hudClusterCenterY(int baselineY, int textHeight, int barHeight, int barCenterBias) {
        int k = (textHeight - barHeight) / 2 + barCenterBias;
        int baseBarY = baselineY + k;
        int textTop = baselineY - textHeight;
        int blockTop = Math.min(textTop, baseBarY);
        int blockBottom = Math.max(baselineY, baseBarY + barHeight);
        return (blockTop + blockBottom) / 2;
    }

    /**
     * Finds a text baseline such that {@link #hudClusterCenterY} matches {@code targetCenterY}
     * (iterative — layout is piecewise-linear in baseline).
     */
    private static int alignBaselineToHudCenterY(int targetCenterY, int textHeight, int barHeight, int barCenterBias) {
        int y = targetCenterY;
        for (int i = 0; i < 10; i++) {
            int cy = hudClusterCenterY(y, textHeight, barHeight, barCenterBias);
            if (cy == targetCenterY) {
                break;
            }
            y += targetCenterY - cy;
        }
        return y;
    }
}
