package civil.compat.moonlight;

import civil.CivilMod;
import civil.map.CivilMapTintPalette;
import java.util.Arrays;
import net.mehvahdjukaar.moonlight.api.map.CustomMapData;
import net.mehvahdjukaar.moonlight.api.map.MapDataRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Parallel per-pixel band + edge for civilization map tint, synced via Moonlight map packets.
 */
public final class CivilTintMapData implements CustomMapData<CivilTintMapData.Dirty> {

    private static CustomMapData.Type<CivilTintMapData> tintType;

    private static final String TAG_SCHEMA = "CvSchema";
    private static final String TAG_BANDS = "Bands";
    private static final String TAG_EDGES = "Edges";

    private static final int PIXELS = 128 * 128;

    private final Dirty dirty = new Dirty();
    private final byte[] bands = new byte[PIXELS];
    private final byte[] edges = new byte[PIXELS];

    public CivilTintMapData() {
        Arrays.fill(bands, CivilMapTintPalette.UNKNOWN);
    }

    /** Called once from {@link MoonlightMapCompatBootstrap#bootstrap()} (not in a static initializer). */
    public static void registerType() {
        if (tintType != null) {
            return;
        }
        tintType =
                MapDataRegistry.registerCustomMapSavedData(
                        new ResourceLocation(CivilMod.MOD_ID, "civil_map_tint"), CivilTintMapData::new);
    }

    public static CustomMapData.Type<CivilTintMapData> tintType() {
        return tintType;
    }

    @Override
    public CustomMapData.Type<CivilTintMapData> getType() {
        return tintType;
    }

    @Override
    public boolean persistOnRescale() {
        return false;
    }

    @Override
    public Dirty createDirtyCounter() {
        return dirty;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains(TAG_BANDS, 7)) {
            byte[] loaded = tag.getByteArray(TAG_BANDS);
            if (loaded.length == PIXELS) {
                System.arraycopy(loaded, 0, bands, 0, PIXELS);
            }
        }
        if (tag.contains(TAG_EDGES, 7)) {
            byte[] loaded = tag.getByteArray(TAG_EDGES);
            if (loaded.length == PIXELS) {
                System.arraycopy(loaded, 0, edges, 0, PIXELS);
            }
        }
    }

    @Override
    public void loadUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putInt(TAG_SCHEMA, CivilMapTintMoonlightSpi.SCHEMA_VERSION);
        tag.putByteArray(TAG_BANDS, bands.clone());
        tag.putByteArray(TAG_EDGES, edges.clone());
    }

    @Override
    public void saveToUpdateTag(CompoundTag tag, Dirty dirtyCounter) {
        if (dirtyCounter.isDirty()) {
            save(tag);
            dirtyCounter.clearDirty();
        }
    }

    public void recordPixel(int mapX, int mapY, byte band, boolean edge) {
        int i = mapX + mapY * 128;
        if (i < 0 || i >= PIXELS) {
            return;
        }
        bands[i] = band;
        edges[i] = edge ? (byte) 1 : (byte) 0;
    }

    public void markDirtyOn(MapItemSavedData mapData) {
        setDirty(mapData, Dirty::markDirty);
    }

    public byte bandAt(int mapX, int mapY) {
        int i = mapX + mapY * 128;
        if (i < 0 || i >= PIXELS) {
            return CivilMapTintPalette.UNKNOWN;
        }
        return bands[i];
    }

    public boolean edgeAt(int mapX, int mapY) {
        int i = mapX + mapY * 128;
        if (i < 0 || i >= PIXELS) {
            return false;
        }
        return edges[i] != 0;
    }

    public static final class Dirty implements CustomMapData.DirtyCounter {
        private boolean dirty = true;

        public void markDirty() {
            this.dirty = true;
        }

        @Override
        public boolean isDirty() {
            return dirty;
        }

        @Override
        public void clearDirty() {
            dirty = false;
        }
    }
}
