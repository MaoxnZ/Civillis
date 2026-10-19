package civil.mixin;

import civil.map.CivilMapTintUpdateSession;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aligns civil tint computation with vanilla {@link MapItem#update}: same pixels and striping as
 * {@link MapItemSavedData#updateColor}; see {@link CivilMapTintUpdateSession}.
 */
@Mixin(MapItem.class)
public abstract class CivilMapItemUpdateTintMixin {

    @Inject(method = "update", at = @At("HEAD"))
    private void civil$tintSessionHead(Level level, Entity entity, MapItemSavedData data, CallbackInfo ci) {
        CivilMapTintUpdateSession.begin(level, entity, data);
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void civil$tintSessionTail(Level level, Entity entity, MapItemSavedData data, CallbackInfo ci) {
        CivilMapTintUpdateSession.end();
    }

    @Redirect(
            method = "update",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;updateColor(IIB)Z"))
    private boolean civil$tintOnVanillaUpdateColor(MapItemSavedData data, int mapX, int mapY, byte packedColor) {
        return CivilMapTintUpdateSession.updateColorWithBake(data, mapX, mapY, packedColor);
    }
}
