package civil.compat.journeymap;

import civil.CivilMod;
import civil.compat.bandsync.ChunkBandClientCache;
import civil.compat.bandsync.ChunkBandConsumers;
import civil.compat.bandsync.ChunkBandSubscriptionHub;
import civil.compat.bandsync.ChunkBandTintPayload;
import civil.config.CivilConfig;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.common.event.ClientEventRegistry;

import java.util.function.Consumer;

/**
 * JourneyMap client plugin: chunk-band Hub + overlay refresh while mapping.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class CivilJourneyMapPlugin implements IClientPlugin {

    private final Consumer<ChunkBandTintPayload> tintPayloadListener = this::handleTintPayload;
    private final Runnable fullMapListener = this::handleFullMapRefresh;

    private IClientAPI api;

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        ChunkBandClientCache.addTintPayloadListener(tintPayloadListener);
        ChunkBandClientCache.addFullMapListener(fullMapListener);
        ClientEventRegistry.MAPPING_EVENT.subscribe(CivilMod.MOD_ID, this::handleMappingEvent);
    }

    @Override
    public String getModId() {
        return CivilMod.MOD_ID;
    }

    private void handleMappingEvent(MappingEvent event) {
        if (event.getStage() == MappingEvent.Stage.MAPPING_STARTED) {
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
        } else if (event.getStage() == MappingEvent.Stage.MAPPING_STOPPED) {
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
