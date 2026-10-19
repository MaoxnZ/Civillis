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
 * Civil map tint during {@link MapItem#update}.
 *
 * <p><b>Forge 1.20.1 / 1.20.2 双轨</b>（见 port-workflow 5.4）：除 Mojmap 全描述符外，必须再挂
 * {@code remap = false} 的 SRG 目标（如 {@code m_42893_} / {@code m_164792_}），否则 reobf 产物下 mixin 可能不生效。
 */
@Mixin(MapItem.class)
public abstract class CivilMapItemUpdateTintMixin {

    @Inject(
            method = "update(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V",
            at = @At("HEAD"),
            require = 0)
    private void civil$tintSessionHeadMoj(
            Level level, Entity entity, MapItemSavedData data, CallbackInfo ci) {
        CivilMapTintUpdateSession.begin(level, entity, data);
    }

    @Inject(
            method = "m_42893_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false)
    private void civil$tintSessionHeadSrg(
            Level level, Entity entity, MapItemSavedData data, CallbackInfo ci) {
        CivilMapTintUpdateSession.begin(level, entity, data);
    }

    @Inject(
            method = "update(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V",
            at = @At("TAIL"),
            require = 0)
    private void civil$tintSessionTailMoj(
            Level level, Entity entity, MapItemSavedData data, CallbackInfo ci) {
        CivilMapTintUpdateSession.end();
    }

    @Inject(
            method = "m_42893_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V",
            at = @At("TAIL"),
            require = 0,
            remap = false)
    private void civil$tintSessionTailSrg(
            Level level, Entity entity, MapItemSavedData data, CallbackInfo ci) {
        CivilMapTintUpdateSession.end();
    }

    @Redirect(
            method = "update(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;updateColor(IIB)Z"),
            require = 0)
    private boolean civil$tintOnVanillaUpdateColorMoj(
            MapItemSavedData data, int mapX, int mapY, byte packedColor) {
        return CivilMapTintUpdateSession.updateColorWithBake(data, mapX, mapY, packedColor);
    }

    @Redirect(
            method = "m_42893_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;m_164792_(IIB)Z"),
            require = 0,
            remap = false)
    private boolean civil$tintOnVanillaUpdateColorSrg(
            MapItemSavedData data, int mapX, int mapY, byte packedColor) {
        return CivilMapTintUpdateSession.updateColorWithBake(data, mapX, mapY, packedColor);
    }
}
