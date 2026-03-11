package civil.fabric;

import civil.CivilMod;
import civil.ModItems;
import civil.ModSounds;
import civil.component.ModComponents;
import civil.aura.SonarBoundaryPayload;
import civil.aura.SonarChargePayload;
import civil.aura.SonarScanManager;
import civil.aura.SonarType;
import civil.respawn.UndyingAnchorActivationHandler;
import civil.respawn.UndyingAnchorParticleManager;
import civil.respawn.UndyingAnchorParticlePayload;
import civil.respawn.UndyingAnchorSaveHandler;
import civil.respawn.UndyingAnchorPreTeleportPayload;
import civil.item.CivilDetectorAnimationReset;
import civil.perf.TpsLogger;
import civil.registry.BlockWeightLoader;
import civil.registry.HeadTypeLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;

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
        PayloadTypeRegistry.playS2C().register(UndyingAnchorPreTeleportPayload.ID, UndyingAnchorPreTeleportPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UndyingAnchorParticlePayload.ID, UndyingAnchorParticlePayload.CODEC);
    }

    private void registerEvents() {
        ServerWorldEvents.LOAD.register(CivilMod::onWorldLoad);
        ServerWorldEvents.UNLOAD.register(CivilMod::onWorldUnload);

        ServerTickEvents.START_SERVER_TICK.register(TpsLogger::onStartTick);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CivilMod.onServerTick(server);
            TpsLogger.onEndTick(server);
            CivilDetectorAnimationReset.onServerTick(server);
            UndyingAnchorSaveHandler.onServerTick(server);
            UndyingAnchorParticleManager.onServerTick(server);
            SonarScanManager.onServerTick(server);
        });

        ServerChunkEvents.CHUNK_LOAD.register(CivilMod::onChunkLoad);

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (entity instanceof ServerPlayer player) {
                boolean saved = UndyingAnchorSaveHandler.trySave(player);
                return !saved;
            }
            return true;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                CivilDetectorAnimationReset.onPlayerJoin(handler.player));

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            BlockPos pos = hitResult.getBlockPos();
            var state = world.getBlockState(pos);
            if (state.is(Blocks.EMERALD_BLOCK) && player instanceof ServerPlayer sp) {
                if (UndyingAnchorActivationHandler.tryActivate(sp, world, pos, hand)) {
                    return InteractionResult.SUCCESS;
                }
            }
            if (!state.is(Blocks.BELL)) return InteractionResult.PASS;
            if (!world.getBlockState(pos.below()).is(Blocks.LODESTONE)) return InteractionResult.PASS;
            if (!civil.config.CivilConfig.auraEffectEnabled) return InteractionResult.PASS;
            double hitRelativeY = hitResult.getLocation().y - (double) pos.getY();
            if (!SonarScanManager.isBellProperHit(state, hitResult.getDirection(), hitRelativeY))
                return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer && world instanceof ServerLevel serverWorld) {
                if (SonarScanManager.tryBellCooldown(serverPlayer, serverWorld)) {
                    SonarScanManager.startScan(serverPlayer, serverWorld, pos, SonarType.STATIC);
                }
            }
            return InteractionResult.PASS;
        });
    }

    private void registerItemGroups() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries ->
                entries.addAfter(Items.COMPASS, ModItems.getCivilDetector()));
    }
}
