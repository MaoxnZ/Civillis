package civil.fabric;

import civil.CivilMod;
import civil.ModItems;
import civil.ModSounds;
import civil.component.ModComponents;
import civil.aura.SonarBoundaryPayload;
import civil.aura.SonarChargePayload;
import civil.aura.SonarScanManager;
import civil.item.CivilDetectorAnimationReset;
import civil.perf.TpsLogger;
import civil.registry.BlockWeightLoader;
import civil.registry.HeadTypeLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.PackType;
import net.minecraft.resources.Identifier;

/**
 * Fabric entry point. Calls common {@link CivilMod#init()} and registers
 * all Fabric-specific events, networking, resource loaders, and item groups.
 */
public class CivilModFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ModComponents.registerDirect();
        ModSounds.registerDirect();
        ModItems.registerDirect();
        CivilMod.init();

        registerDatapackReloadListeners();
        registerPayloadTypes();
        registerEvents();
        registerItemGroups();
    }

    private void registerDatapackReloadListeners() {
        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath("civil", "block_weights");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        BlockWeightLoader.reload(manager);
                    }
                });

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.fromNamespaceAndPath("civil", "head_types");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager manager) {
                        HeadTypeLoader.reload(manager);
                    }
                });
    }

    private void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(SonarChargePayload.ID, SonarChargePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SonarBoundaryPayload.ID, SonarBoundaryPayload.CODEC);
    }

    private void registerEvents() {
        ServerWorldEvents.LOAD.register(CivilMod::onWorldLoad);
        ServerWorldEvents.UNLOAD.register(CivilMod::onWorldUnload);

        ServerTickEvents.START_SERVER_TICK.register(TpsLogger::onStartTick);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CivilMod.onServerTick(server);
            TpsLogger.onEndTick(server);
            CivilDetectorAnimationReset.onServerTick(server);
            SonarScanManager.onServerTick(server);
        });

        ServerChunkEvents.CHUNK_LOAD.register(CivilMod::onChunkLoad);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                CivilDetectorAnimationReset.onPlayerJoin(handler.player));
    }

    private void registerItemGroups() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries ->
                entries.addAfter(Items.COMPASS, ModItems.getCivilDetector()));
    }
}
