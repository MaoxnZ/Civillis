package civil.mixin;

import civil.towncenter.TownCenterActivationHandler;
import civil.towncenter.gui.TownCenterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Town center lectern interact (1.20.1 {@code Block#use} API).
 *
 * <p><b>Forge 1.20.1 / 1.20.2 双轨</b>：Mojmap {@code use} 与 SRG {@code m_6227_}（{@code remap = false}，
 * {@code require = 0}），与 {@link CivilCartographyTableMenuMixin} 同套路。
 */
@Mixin(LecternBlock.class)
public abstract class CivilLecternBlockUseMixin {

    @Inject(
            method = "use(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)"
                    + "Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            require = 0,
            cancellable = true)
    private void civil$onUseMoj(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        civil$onUseCore(state, level, pos, player, hand, player.getItemInHand(hand), cir);
    }

    @Inject(
            method = "m_6227_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)"
                    + "Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            require = 0,
            remap = false,
            cancellable = true)
    private void civil$onUseSrg(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        civil$onUseCore(state, level, pos, player, hand, player.getItemInHand(hand), cir);
    }

    private static void civil$onUseCore(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack,
            CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = civil$handleTownCenterInteract(state, level, pos, player, hand, stack);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    private static InteractionResult civil$handleTownCenterInteract(
            BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (level.isClientSide()) return null;
        if (!(level instanceof ServerLevel serverLevel)) return null;
        if (!(player instanceof ServerPlayer serverPlayer)) return null;

        if (TownCenterActivationHandler.isRegisteredTownCenterLectern(serverLevel, pos)) {
            if (TownCenterActivationHandler.shouldOpenRegisteredGui(serverLevel, pos)) {
                serverPlayer.openMenu(new TownCenterMenu.Provider(pos));
                return InteractionResult.CONSUME;
            }
            return civil$handleRegisteredTownCenterRepair(serverLevel, pos, state, serverPlayer, hand);
        }

        if (!stack.isEmpty() && stack.is(Items.EMERALD)
                && TownCenterActivationHandler.tryActivateFromInteract(serverPlayer, serverLevel, pos)) {
            return InteractionResult.CONSUME;
        }

        return null;
    }

    private static InteractionResult civil$handleRegisteredTownCenterRepair(
            ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, InteractionHand hand) {
        if (!(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern)) {
            return InteractionResult.CONSUME;
        }

        ItemStack currentBook = lectern.getBook();
        if (!currentBook.isEmpty()) {
            return InteractionResult.CONSUME;
        }

        if (hand != null && player.getItemInHand(hand).is(Items.WRITTEN_BOOK)) {
            ItemStack stack = player.getItemInHand(hand);
            ItemStack book = stack.copy();
            book.setCount(1);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            lectern.setBook(book);
            LecternBlock.resetBookState(player, level, pos, state, true);
            level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.CONSUME;
    }
}
