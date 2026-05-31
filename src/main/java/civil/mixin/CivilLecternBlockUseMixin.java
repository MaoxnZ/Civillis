package civil.mixin;

import civil.towncenter.TownCenterActivationHandler;
import civil.towncenter.gui.TownCenterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LecternBlock.class)
public abstract class CivilLecternBlockUseMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void civil$onUseWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = civil$handleTownCenterInteract(level, pos, player, ItemStack.EMPTY);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void civil$onUseItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = civil$handleTownCenterInteract(level, pos, player, stack);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    private static InteractionResult civil$handleTownCenterInteract(
            Level level, BlockPos pos, Player player, ItemStack stack) {
        if (level.isClientSide()) return null;
        if (!(level instanceof ServerLevel serverLevel)) return null;
        if (!(player instanceof ServerPlayer serverPlayer)) return null;

        if (TownCenterActivationHandler.shouldOpenRegisteredGui(serverLevel, pos)) {
            serverPlayer.openMenu(new TownCenterMenu.Provider(pos));
            return InteractionResult.CONSUME;
        }

        // 仅手持绿宝石时才尝试激活；放书/其它物品不应触发 fail 音
        if (!stack.isEmpty() && stack.is(Items.EMERALD)
                && TownCenterActivationHandler.tryActivateFromInteract(serverPlayer, serverLevel, pos)) {
            return InteractionResult.CONSUME;
        }

        return null;
    }
}
