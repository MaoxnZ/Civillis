package civil.mixin;

import civil.component.ModComponents;
import civil.item.CivilMapItemStacks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After vanilla {@link CartographyTableMenu#setupResultSlot} (which calls {@link ContainerLevelAccess#execute}
 * synchronously), when the map slot holds {@link ModComponents#CIVIL_MAP}, normalize the result stack with
 * {@link CivilMapItemStacks#toCivilFilled} so scale (paper), clone (empty map), and lock (glass pane) outputs
 * keep {@link ModComponents#CIVIL_MAP} and civil lore alongside vanilla map components.
 */
@Mixin(CartographyTableMenu.class)
public abstract class CivilCartographyTableMenuMixin {

    /** Prevents {@code getSlot(2).set} from re-entering {@code slotsChanged} / {@code setupResultSlot}. */
    @Unique
    private boolean civil$inAfterSetupResult;

    @Inject(method = "setupResultSlot", at = @At("TAIL"))
    private void civil$afterSetupResult(ItemStack map, ItemStack extra, ItemStack preview, CallbackInfo ci) {
        if (!Boolean.TRUE.equals(map.get(ModComponents.CIVIL_MAP))) {
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
        MapId baseId = map.get(DataComponents.MAP_ID);
        if (baseId == null) {
            return;
        }
        if (rawResult.isEmpty() || !(rawResult.getItem() instanceof MapItem)) {
            return;
        }
        ItemStack civilResult = CivilMapItemStacks.toCivilFilled(rawResult);
        self.getSlot(2).set(civilResult);
    }
}
