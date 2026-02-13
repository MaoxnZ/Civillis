package civil.item;

import civil.CivilMod;
import civil.CivilServices;
import civil.config.CivilConfig;
import civil.ModSounds;
import civil.aura.SonarScanManager;
import civil.component.ModComponents;
import civil.civilization.CScore;
import civil.civilization.MobHeadRegistry;
import net.minecraft.entity.EntityType;
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

import java.util.List;

/**
 * Civilization detector: right-click when held to activate, outputs current position's civilization value to chat,
 * and displays 2-second animation on item (right-side chunk color + client flickering particles), restores default after 2 seconds.
 */
public class CivilDetectorItem extends Item {

    // Animation/cooldown durations are in CivilConfig

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
            player.getItemCooldownManager().set(stack, CivilConfig.detectorCooldownTicks);
            BlockPos pos = player.getBlockPos();
            CScore cScore;
            try {
                cScore = CivilServices.getCivilizationService().getCScoreAt(serverWorld, pos);
            } catch (Exception e) {
                if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.warn("[civil] Civil detector failed at {}: {}", pos, e.getMessage());
                }
                cScore = null;
            }

            if (CivilMod.DEBUG) {
                String msg = cScore != null
                        ? String.format("§aCivilization Value: §f%.2f §7(Position %d, %d, %d)", cScore.score(), pos.getX(), pos.getY(), pos.getZ())
                        : "§cCivilization detection failed";
                if (player instanceof ServerPlayerEntity serverPlayer) {
                    serverPlayer.sendMessage(Text.literal(msg));
                }
            }

            // Trigger 2-second animation: write display state and animation end tick, start animation reset logic checking
            String displayState = scoreToDisplayState(cScore, serverWorld, pos);
            stack.set(ModComponents.DETECTOR_DISPLAY, displayState);
            stack.set(ModComponents.DETECTOR_ANIMATION_END, world.getTime() + CivilConfig.detectorAnimationTicks);
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

            // Trigger sonar scan (protection aura visualization)
            if (CivilConfig.auraEffectEnabled && player instanceof ServerPlayerEntity serverPlayer) {
                SonarScanManager.startScan(serverPlayer, serverWorld);
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

    /**
     * Determine display state from civilization score and nearby mob heads.
     *
     * <p>Priority: monster head nearby = purple,
     * below thresholdLow = low/red, between thresholdLow..thresholdMid = medium/yellow,
     * above thresholdMid = high/green.  Thresholds follow CivilConfig (GUI suppression slider).
     *
     * <p>Head detection queries MobHeadRegistry directly (consistent with SpawnPolicy),
     * since CScore no longer carries headTypes in the Fusion Architecture.
     */
    private static String scoreToDisplayState(CScore cScore, ServerWorld world, BlockPos pos) {
        if (cScore == null) {
            return "default";
        }

        // Check MobHeadRegistry directly (fusion architecture: heads decoupled from CScore)
        MobHeadRegistry registry = CivilServices.getMobHeadRegistry();
        if (registry != null && registry.isInitialized()) {
            String dim = world.getRegistryKey().toString();
            List<EntityType<?>> headTypes = registry.getHeadTypesNear(
                    dim, pos,
                    CivilConfig.headRangeX,
                    CivilConfig.headRangeZ,
                    CivilConfig.headRangeY);
            if (!headTypes.isEmpty()) {
                return "monster";
            }
        }

        double score = cScore.score();
        if (score < CivilConfig.spawnThresholdLow) {
            return "low";
        }
        if (score < CivilConfig.spawnThresholdMid) {
            return "medium";
        }
        return "high";
    }
}
