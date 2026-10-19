package civil.compat.moonlight.mixin;

import civil.compat.moonlight.CivilTintMapData;
import civil.map.CivilMapColorBake;
import civil.map.CivilMapTintPalette;
import com.mojang.blaze3d.platform.NativeImage;
import net.mehvahdjukaar.moonlight.api.map.ExpandedMapData;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moonlight map tint on the client at {@link MapRenderer.MapInstance#updateTexture} <strong>TAIL</strong>,
 * after vanilla fills {@link NativeImage} from {@link MapItemSavedData#colors} and runs the first
 * {@link DynamicTexture#upload()} (including Supplementaries {@code processTexture} before that upload).
 *
 * <p><strong>Why TAIL + second upload:</strong> writing before the vanilla-first-upload was observed to be
 * lost on CPU buffer after that call; tinting after the full method body then uploading again fixes it.
 */
@Mixin(value = MapRenderer.MapInstance.class, priority = 2000)
public abstract class CivilMapMoonlightClientMixin {

    @Shadow private MapItemSavedData data;

    @Shadow @Final private DynamicTexture texture;

    @Inject(method = "updateTexture", at = @At("TAIL"), require = 0)
    private void civil$afterMapTextureReady(CallbackInfo ci) {
        finishMoonlightTintPass(this.texture, this.data);
    }

    private static void finishMoonlightTintPass(DynamicTexture dynamicTexture, MapItemSavedData mapData) {
        int rgbaWrites = applyMoonlightTint(dynamicTexture, mapData);
        if (rgbaWrites > 0) {
            dynamicTexture.upload();
        }
    }

    private static int applyMoonlightTint(DynamicTexture dynamicTexture, MapItemSavedData mapData) {
        if (!(mapData instanceof ExpandedMapData)) {
            return 0;
        }
        var tintType = CivilTintMapData.tintType();
        if (tintType == null) {
            return 0;
        }
        CivilTintMapData tint = tintType.get(mapData);
        if (tint == null) {
            return 0;
        }
        NativeImage img = dynamicTexture.getPixels();
        if (img == null) {
            return 0;
        }

        int rgbaWrites = 0;
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                byte band = tint.bandAt(x, y);
                if (band != CivilMapTintPalette.HIGH
                        && band != CivilMapTintPalette.MONSTER
                        && band != CivilMapTintPalette.ZONE) {
                    continue;
                }
                boolean edge = tint.edgeAt(x, y);
                int idx = x + y * 128;
                byte base = mapData.colors[idx];
                byte blended = CivilMapColorBake.blendPackedMapByte(base, band, edge);
                if (blended == base) {
                    continue;
                }
                int rgbaOut = swapRedBlueArgb(CivilMapColorBake.rgbaForMapTexture(blended));
                img.setPixelRGBA(x, y, rgbaOut);
                rgbaWrites++;
            }
        }
        return rgbaWrites;
    }

    /**
     * Moonlight parallel tint runs after the first {@link DynamicTexture#upload()}; {@link CivilMapColorBake#rgbaForMapTexture}
     * matches vanilla single-pass map bytes → texel, so second-pass writes need R/B swapped to match the buffer.
     */
    private static int swapRedBlueArgb(int argb) {
        int a = argb & 0xFF000000;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return a | (b << 16) | (g << 8) | r;
    }
}
