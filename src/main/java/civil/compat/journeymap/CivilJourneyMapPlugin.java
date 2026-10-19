package civil.compat.journeymap;

import civil.CivilMod;
import civil.compat.bandsync.ChunkBandClientCache;
import civil.compat.bandsync.ChunkBandConsumers;
import civil.compat.bandsync.ChunkBandSubscriptionHub;
import civil.compat.bandsync.ChunkBandTintPayload;
import civil.config.CivilConfig;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.event.ClientEvent;

import java.util.EnumSet;
import java.util.function.Consumer;

/**
 * JourneyMap client plugin: chunk-band Hub + overlay refresh while mapping.
 */
@ClientPlugin
public final class CivilJourneyMapPlugin implements IClientPlugin {

    private final Consumer<ChunkBandTintPayload> tintPayloadListener = this::handleTintPayload;
    private final Runnable fullMapListener = this::handleFullMapRefresh;

    private IClientAPI api;

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        ChunkBandClientCache.addTintPayloadListener(tintPayloadListener);
        ChunkBandClientCache.addFullMapListener(fullMapListener);
        api.subscribe(CivilMod.MOD_ID, EnumSet.of(
                ClientEvent.Type.MAPPING_STARTED,
                ClientEvent.Type.MAPPING_STOPPED));
    }

    @Override
    public String getModId() {
        return CivilMod.MOD_ID;
    }

    @Override
    public void onEvent(ClientEvent event) {
        if (event.type == ClientEvent.Type.MAPPING_STARTED) {
            ChunkBandSubscriptionHub.setJourneyMapMappingSessionActive(true);
            if (!CivilConfig.chunkBandClientEnabledJourneyMap) {
                if (api != null) {
                    api.removeAll(CivilMod.MOD_ID, DisplayType.Polygon);
                    JourneyMapChunkBandOverlayRenderer.clearPolygonTracking();
                }
                if (CivilMod.DEBUG) {
                    CivilMod.LOGGER.info("[journeymap] MAPPING_STARTED skip (chunkBand.clientEnabled.journeymap=false)");
                }
                return;
            }
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[journeymap] MAPPING_STARTED -> Hub.acquire + fullRebuild");
            }
            ChunkBandSubscriptionHub.acquire(ChunkBandConsumers.JOURNEY_MAP);
            JourneyMapChunkBandOverlayRenderer.fullRebuildFromCache(api);
        } else if (event.type == ClientEvent.Type.MAPPING_STOPPED) {
            ChunkBandSubscriptionHub.setJourneyMapMappingSessionActive(false);
            if (CivilMod.DEBUG) {
                CivilMod.LOGGER.info("[journeymap] MAPPING_STOPPED -> Hub.release + remove Polygon overlays");
            }
            ChunkBandSubscriptionHub.release(ChunkBandConsumers.JOURNEY_MAP);
            if (api != null) {
                api.removeAll(CivilMod.MOD_ID, DisplayType.Polygon);
                JourneyMapChunkBandOverlayRenderer.clearPolygonTracking();
            }
        }
    }

    private void handleTintPayload(ChunkBandTintPayload payload) {
        if (api == null) {
            return;
        }
        if (!CivilConfig.chunkBandClientEnabledJourneyMap
                || !ChunkBandSubscriptionHub.isHeld(ChunkBandConsumers.JOURNEY_MAP)) {
            return;
        }
        JourneyMapChunkBandOverlayRenderer.applyDelta(api, payload);
    }

    private void handleFullMapRefresh() {
        if (api == null) {
            return;
        }
        if (!CivilConfig.chunkBandClientEnabledJourneyMap
                || !ChunkBandSubscriptionHub.isHeld(ChunkBandConsumers.JOURNEY_MAP)) {
            api.removeAll(CivilMod.MOD_ID, DisplayType.Polygon);
            JourneyMapChunkBandOverlayRenderer.clearPolygonTracking();
            return;
        }
        JourneyMapChunkBandOverlayRenderer.fullRebuildFromCache(api);
    }
}
