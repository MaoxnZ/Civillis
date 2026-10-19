package civil.mixin;

import civil.component.ModComponents;
import civil.item.CivilMapItemStacks;
import civil.map.CivilMapNbt;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After {@link CartographyTableMenu#setupResultSlot}, normalize civil map outputs.
 *
 * <p><b>Forge 1.20.1 / 1.20.2 双轨</b>：除 Mojmap 描述符外，增加 SRG {@code m_39162_}（{@code remap = false}，
 * {@code require = 0}），与 {@link CivilLevelBlockChangeMixin} 同套路。
 */
@Mixin(CartographyTableMenu.class)
public abstract class CivilCartographyTableMenuMixin {

    @Unique
    private boolean civil$inAfterSetupResult;

    @Inject(
            method = "setupResultSlot(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("TAIL"),
            require = 0)
    private void civil$afterSetupResultMoj(ItemStack map, ItemStack extra, ItemStack preview, CallbackInfo ci) {
        civil$afterSetupResultCore(map, extra, preview);
    }

    @Inject(
            method = "m_39162_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("TAIL"),
            require = 0,
            remap = false)
    private void civil$afterSetupResultSrg(ItemStack map, ItemStack extra, ItemStack preview, CallbackInfo ci) {
        civil$afterSetupResultCore(map, extra, preview);
    }

    private void civil$afterSetupResultCore(ItemStack map, ItemStack extra, ItemStack preview) {
        if (!ModComponents.isCivilMap(map)) {
            return;
        }
        if (this.civil$inAfterSetupResult) {
            return;
        }
        this.civil$inAfterSetupResult = true;
        try {
            civil$afterSetupResult0(map, extra, preview);
        } finally {
            this.civil$inAfterSetupResult = false;
        }
    }

    private void civil$afterSetupResult0(ItemStack map, ItemStack extra, ItemStack preview) {
        CartographyTableMenu self = (CartographyTableMenu) (Object) this;
        ItemStack rawResult = self.getSlot(2).getItem();
        if (CivilMapNbt.getVanillaMapId(map) == null) {
            return;
        }
        if (rawResult.isEmpty() || !(rawResult.getItem() instanceof MapItem)) {
            return;
        }
        ItemStack civilResult = CivilMapItemStacks.toCivilFilled(rawResult);
        self.getSlot(2).set(civilResult);
    }
}
