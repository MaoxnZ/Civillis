package civil.item;

import civil.CivilMod;
import civil.CivilServices;
import civil.ModSounds;
import civil.component.ModComponents;
import civil.civilization.CScore;
import civil.civilization.CivilValues;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;

/**
 * Civilization detector: right-click when held to activate, outputs current position's civilization value to chat,
 * and displays 2-second animation on item (right-side chunk color + client flickering particles), restores default after 2 seconds.
 */
public class CivilDetectorItem extends Item {

    private static final int ANIMATION_TICKS = 40; // 2 seconds
    private static final int COOLDOWN_TICKS = 10;  // 0.5 second cooldown

    public CivilDetectorItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        // Skip if on cooldown
        if (player.getItemCooldownManager().isCoolingDown(stack)) {
            return ActionResult.PASS;
        }
        if (world instanceof ServerWorld serverWorld) {
            // Set cooldown
            player.getItemCooldownManager().set(stack, COOLDOWN_TICKS);
            BlockPos pos = player.getBlockPos();
            double score;
            try {
                CScore cScore = CivilServices.getCivilizationService().getCScoreAt(serverWorld, pos);
                score = cScore.score();
            } catch (Exception e) {
                if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.warn("[civil] Civil detector failed at {}: {}", pos, e.getMessage());
                }
                score = -1.0;
            }

            if (CivilMod.DEBUG) {
                String msg = score >= 0
                        ? String.format("§aCivilization Value: §f%.2f §7(Position %d, %d, %d)", score, pos.getX(), pos.getY(), pos.getZ())
                        : "§cCivilization detection failed";
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    serverPlayer.sendMessage(Text.literal(msg));
                }
            }

            // Trigger 2-second animation: write display state and animation end tick, start animation reset logic checking
            String displayState = scoreToDisplayState(score);
            stack.set(ModComponents.DETECTOR_DISPLAY, displayState);
            stack.set(ModComponents.DETECTOR_ANIMATION_END, world.getTime() + ANIMATION_TICKS);
            CivilDetectorAnimationReset.markActive();

            // Play sound effect by detection state: get sound + pitch at once (vanilla uses pitch to distinguish, custom OGG uses 1.0)
            ModSounds.DetectorPlayback playback = ModSounds.getDetectorPlayback(displayState);
            if (playback.sound() != null) {
                serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                        playback.sound(), SoundCategory.BLOCKS, 1.0f, playback.pitch());
                if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.info("[civil] detector sound played: displayState={}", displayState);
                }
            }
        }

        return ActionResult.SUCCESS;
    }

    /** During animation (DETECTOR_DISPLAY is not default and DETECTOR_ANIMATION_END exists) show enchantment glow effect. */
    @Override
    public boolean hasGlint(ItemStack stack) {
        String display = stack.get(ModComponents.DETECTOR_DISPLAY);
        Long endTick = stack.get(ModComponents.DETECTOR_ANIMATION_END);
        return display != null && !"default".equals(display) && endTick != null;
    }

    /** Civilization value -> display state: 0–0.2 low/red, 0.2–0.5 medium/yellow, 0.5+ high/green, 2 monster head/purple. */
    private static String scoreToDisplayState(double score) {
        if (score < 0) {
            return "default";
        }
        if (score >= CivilValues.FORCE_ALLOW_SCORE) {
            return "monster";
        }
        if (score < 0.1) {
            return "low";
        }
        if (score < 0.3) {
            return "medium";
        }
        return "high";
    }
}
