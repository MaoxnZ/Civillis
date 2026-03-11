package civil.respawn;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Client-side cinematic for civil save (emerald block / undying anchor).
 * Phase 0: 0.5s with FOV + red tint; beacon at tick 2. At end: black 1s + teleport → totem reveal.
 *
 * <p>Note: Sounds and totem particles use anchor position (from payload) for stability —
 * avoids desync if the teleport packet hasn't arrived when reveal starts.
 */
public final class UndyingAnchorCinematicEffect {

    private UndyingAnchorCinematicEffect() {}

    private static final int PHASE_NONE = 0;
    private static final int PHASE0 = 1;
    private static final int PHASE_BLACK = 2;
    private static final int PHASE_REVEAL = 3;

    private static final int RED_TINT = 0xB04030;

    private static int phase = PHASE_NONE;
    private static long phaseStartNano = 0;
    private static long cinematicStartNano = 0;
    private static float phase0Sec;
    private static float blackSec;
    private static float revealSec;
    private static boolean beaconPlayed = false;
    /** Anchor position for totem sound/particles (stable, no teleport desync). */
    private static double anchorX, anchorY, anchorZ;

    public static void startPreTeleport(int phase0Ticks, int anchorX, int anchorY, int anchorZ) {
        phase0Sec = phase0Ticks / 20f;
        blackSec = UndyingAnchorCinematic.BLACK_DURATION_SEC;
        revealSec = UndyingAnchorCinematic.REVEAL_DURATION_SEC;
        UndyingAnchorCinematicEffect.anchorX = anchorX + 0.5;
        UndyingAnchorCinematicEffect.anchorY = anchorY + 1.0;
        UndyingAnchorCinematicEffect.anchorZ = anchorZ + 0.5;
        long now = System.nanoTime();
        phaseStartNano = now;
        cinematicStartNano = now;
        beaconPlayed = false;
        phase = PHASE0;
    }

    public static void tickAndApplyShake(PoseStack poseStack) {
        if (phase == PHASE_NONE) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || player.level() == null) {
            phase = PHASE_NONE;
            return;
        }

        long now = System.nanoTime();
        float elapsed = (now - phaseStartNano) / 1_000_000_000f;
        float beaconAt = UndyingAnchorCinematic.PHASE0_BEACON_TICK / 20f;

        if (phase == PHASE0) {
            if (!beaconPlayed && elapsed >= beaconAt - 0.01f) {
                player.level().playLocalSound(
                        player.getX(), player.getY() + 1, player.getZ(),
                        SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 0.8f, false);
                beaconPlayed = true;
            }
            if (elapsed >= phase0Sec) {
                phase = PHASE_BLACK;
                phaseStartNano = now;
            }
        }

        if (phase == PHASE_BLACK) {
            if (elapsed >= blackSec) {
                phase = PHASE_REVEAL;
                phaseStartNano = now;
                playTotemEffect(player.level());
            }
        }

        if (phase == PHASE_REVEAL) {
            tickRevealParticles(player.level(), elapsed);
            if (elapsed >= revealSec) {
                phase = PHASE_NONE;
            }
        }
    }

    private static void playTotemEffect(net.minecraft.world.level.Level level) {
        if (level == null) return;
        double x = anchorX;
        double y = anchorY;
        double z = anchorZ;

        // Totem particles (yellow-green burst)
        for (int i = 0; i < 80; i++) {
            float angle = (float) (Math.random() * Math.PI * 2);
            float dist = (float) (Math.random() * 2);
            double px = x + Math.cos(angle) * dist;
            double pz = z + Math.sin(angle) * dist;
            double vx = (Math.random() - 0.5) * 0.2;
            double vy = Math.random() * 0.25 + 0.05;
            double vz = (Math.random() - 0.5) * 0.2;
            level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, px, y, pz, vx, vy, vz);
        }

        // Entity effect particles (upward drifting sparkles, totem-like green)
        var entityEffect = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.33f, 1f, 0.5f);
        for (int i = 0; i < 50; i++) {
            float angle = (float) (Math.random() * Math.PI * 2);
            float dist = (float) (Math.random() * 1.5);
            double px = x + Math.cos(angle) * dist;
            double pz = z + Math.sin(angle) * dist;
            double vx = (Math.random() - 0.5) * 0.15;
            double vy = Math.random() * 0.2 + 0.08;  // stronger upward
            double vz = (Math.random() - 0.5) * 0.15;
            level.addParticle(entityEffect, px, y, pz, vx, vy, vz);
        }

        // Sounds: totem + portal at anchor position (stable, no teleport desync)
        level.playLocalSound(x, y, z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f, false);
        level.playLocalSound(x, y, z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.35f, 0.9f, false);
    }

    private static void tickRevealParticles(net.minecraft.world.level.Level level, float elapsed) {
        if (level == null) return;
        double x = anchorX;
        double y = anchorY;
        double z = anchorZ;
        float t = elapsed / revealSec;
        int totemN = t < 0.6f ? 5 : 2;
        int effectN = t < 0.6f ? 4 : 2;
        var entityEffect = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.33f, 1f, 0.5f);
        for (int i = 0; i < totemN; i++) {
            float angle = (float) (Math.random() * Math.PI * 2);
            float dist = (float) (Math.random() * 1.2);
            double px = x + Math.cos(angle) * dist;
            double pz = z + Math.sin(angle) * dist;
            double vx = (Math.random() - 0.5) * 0.15;
            double vy = Math.random() * 0.12 + 0.02;
            double vz = (Math.random() - 0.5) * 0.15;
            level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, px, y, pz, vx, vy, vz);
        }
        for (int i = 0; i < effectN; i++) {
            float angle = (float) (Math.random() * Math.PI * 2);
            float dist = (float) (Math.random() * 1.0);
            double px = x + Math.cos(angle) * dist;
            double pz = z + Math.sin(angle) * dist;
            double vx = (Math.random() - 0.5) * 0.1;
            double vy = Math.random() * 0.15 + 0.05;
            double vz = (Math.random() - 0.5) * 0.1;
            level.addParticle(entityEffect, px, y, pz, vx, vy, vz);
        }
    }

    public static float getOverlayAlpha() {
        if (phase == PHASE_NONE) return -1f;
        long now = System.nanoTime();
        float elapsed = (now - phaseStartNano) / 1_000_000_000f;

        if (phase == PHASE0) {
            return Math.min(0.55f, elapsed / Math.max(0.01f, phase0Sec) * 0.55f);
        }
        if (phase == PHASE_BLACK) return 1f;
        if (phase == PHASE_REVEAL) {
            return Math.max(0f, 1f - elapsed / revealSec);
        }
        return -1f;
    }

    public static boolean isOverlayBlack() {
        return phase == PHASE_BLACK;
    }

    public static boolean isActive() {
        return phase != PHASE_NONE;
    }

    /** FOV shrink during Phase 0 only. Ease-in cubic (先慢后快), subtle zoom (不放太大). */
    public static float getFovMultiplier() {
        if (phase == PHASE_NONE || phase == PHASE_BLACK || phase == PHASE_REVEAL) return -1f;
        if (phase != PHASE0) return -1f;
        long now = System.nanoTime();
        float totalElapsed = (now - cinematicStartNano) / 1_000_000_000f;
        if (totalElapsed <= 0 || totalElapsed >= phase0Sec) return -1f;
        float t = totalElapsed / phase0Sec;
        float u = t * t * t;  // ease-in cubic: 先慢后快
        return 1f - u * 0.2f; // 最多缩小 20%，不放太大
    }

    private static int getOverlayRgb() {
        if (phase == PHASE0) return RED_TINT;
        if (phase == PHASE_BLACK) return 0x000000;
        return 0x000000;
    }

    public static void renderOverlay(GuiGraphics guiGraphics, float tickDelta) {
        float alpha = getOverlayAlpha();
        if (alpha < 0.01f) return;

        int a = (int) (alpha * 255) & 0xFF;
        int rgb = isOverlayBlack() ? 0x000000 : getOverlayRgb();
        int color = (a << 24) | (rgb & 0xFFFFFF);

        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), color);
    }
}
