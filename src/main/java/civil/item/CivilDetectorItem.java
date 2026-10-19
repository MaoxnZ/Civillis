package civil.item;

import civil.CivilMod;
import civil.CivilServices;
import civil.config.CivilConfig;
import civil.ModSounds;
import civil.aura.SonarScanManager;
import civil.aura.SonarType;
import civil.component.ModComponents;
import civil.civilization.CivilRegionClassifier;
import civil.civilization.CivilRegionKind;
import civil.civilization.CScore;
import civil.civilization.VoxelChunkKey;
import civil.progress.CivilAdvancements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Civilization detector: right-click when held to activate, outputs current position's civilization value to chat,
 * and displays 2-second animation on item (right-side chunk color + client flickering particles), restores default after 2 seconds.
 */
public class CivilDetectorItem extends Item {

    public CivilDetectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (world instanceof ServerLevel serverWorld) {
            player.getCooldowns().addCooldown(this, CivilConfig.detectorCooldownTicks);
            BlockPos pos = player.blockPosition();
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
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.sendSystemMessage(Component.literal(msg));
                }
            }

            String displayState = scoreToDisplayState(cScore, serverWorld, pos);
            ModComponents.setDisplay(stack, displayState);
            ModComponents.setAnimationEnd(stack, world.getGameTime() + CivilConfig.detectorAnimationTicks);
            CivilDetectorAnimationReset.markActive();

            // Award high-civilization advancement the first time a HIGH reading is obtained
            if ("high".equals(displayState) && player instanceof ServerPlayer serverPlayer) {
                CivilAdvancements.tryAward(serverPlayer, CivilAdvancements.HIGH_CIVILIZATION);
            }

            // Play sound effect by detection state: get sound + pitch at once (vanilla uses pitch to distinguish, custom OGG uses 1.0)
            ModSounds.DetectorPlayback playback = ModSounds.getDetectorPlayback(displayState);
            if (playback.sound() != null) {
                float volume = ("monster".equals(displayState) || "zone".equals(displayState)) ? 0.7f : 1.0f;
                serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(),
                        playback.sound(), SoundSource.BLOCKS, volume, playback.pitch());
                if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.info("[civil] detector sound played: displayState={}", displayState);
                }
            }

            if (CivilConfig.auraEffectEnabled && SonarType.DETECTOR.isEnabled()
                    && player instanceof ServerPlayer serverPlayer) {
                SonarScanManager.startScan(serverPlayer, serverWorld, SonarType.DETECTOR);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        String display = ModComponents.getDisplay(stack);
        return !"default".equals(display) && ModComponents.hasAnimationEnd(stack);
    }

    /**
     * Determine display state from civilization score and farm shrine bypass proximity.
     *
     * <p>Priority: inside an activated shrine bypass box = purple,
     * below thresholdLow = low/red, between thresholdLow..thresholdMid = medium/yellow,
     * above thresholdMid = high/green.  Thresholds follow CivilConfig (GUI suppression slider).
     *
     * <p>Shrine bypass uses {@link FarmShrineTracker} (consistent with spawn policy), since CScore
     * does not fold mob heads into the fusion score.
     */
    private static String scoreToDisplayState(CScore cScore, ServerLevel world, BlockPos pos) {
        CivilRegionKind kind = CivilRegionClassifier.classify(world, VoxelChunkKey.from(pos)).kind();
        if (kind == CivilRegionKind.SHRINE) {
            return "monster";
        }
        if (kind == CivilRegionKind.ZONE) {
            return "zone";
        }
        if (cScore == null) {
            return "default";
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
