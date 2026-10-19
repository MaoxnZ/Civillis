package civil.forge;

import civil.CivilMod;
import civil.ModItems;
import civil.ModMenuTypes;
import civil.ModRecipeSerializers;
import civil.ModSounds;
import civil.recipe.CivilDetectorMapUpgradeRecipe;
import civil.aura.SonarBoundaryPayload;
import civil.aura.SonarChargePayload;
import civil.aura.SonarScanManager;
import civil.aura.SonarType;
import civil.component.ModComponents;
import civil.item.CivilDetectorAnimationReset;
import civil.item.CivilDetectorItem;
import civil.perf.TpsLogger;
import civil.registry.BlockWeightLoader;
import civil.registry.DimensionPolicyLoader;
import civil.registry.HeadTypeLoader;
import civil.registry.MobFleeEntityLoader;
import civil.registry.PresenceKeepAliveLoader;
import civil.registry.SpawnGateEntityLoader;
import civil.registry.TownCenterLevelLoader;
import civil.registry.ZonePolicyLoader;
import civil.command.CivilAdminCommands;
import civil.config.CivilConfig;
import civil.civilization.ZoneTransitionPayload;
import civil.compat.bandsync.ChunkBandSubscribePayload;
import civil.compat.bandsync.ChunkBandSyncPolicyPayload;
import civil.compat.bandsync.ChunkBandTintPayload;
import civil.compat.bandsync.ChunkBandTintSync;
import civil.shrine.FarmShrineActivationHandler;
import civil.shrine.FarmShrineParticleManager;
import civil.shrine.FarmShrineParticlePayload;
import civil.towncenter.TownCenterActivationBurstPayload;
import civil.towncenter.TownCenterParticleManager;
import civil.towncenter.TownCenterParticlePayload;
import civil.towncenter.network.TownCenterC2SPayload;
import civil.towncenter.network.TownCenterGuiSyncPayload;
import civil.towncenter.network.TownCenterMenuActionHandler;
import civil.respawn.UndyingAnchorActivationHandler;
import civil.respawn.UndyingAnchorParticleManager;
import civil.respawn.UndyingAnchorParticlePayload;
import civil.respawn.UndyingAnchorPreTeleportPayload;
import civil.respawn.UndyingAnchorSaveHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import civil.towncenter.gui.TownCenterMenu;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.level.block.Blocks;

/**
 * Forge entry point. Registers all Forge-specific events, networking,
 * resource loaders, item groups, and delegates to common {@link CivilMod#init()}.
 */
@Mod(CivilMod.MOD_ID)
public class CivilModForge {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CivilMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, CivilMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CivilMod.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, CivilMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CivilMod.MOD_ID);

    private static final RegistryObject<SoundEvent> DETECTOR_DEFAULT = SOUNDS.register("detector_default", () -> ModSounds.buildSoundEvent("detector_default"));
    private static final RegistryObject<SoundEvent> DETECTOR_LOW     = SOUNDS.register("detector_low",     () -> ModSounds.buildSoundEvent("detector_low"));
    private static final RegistryObject<SoundEvent> DETECTOR_MEDIUM  = SOUNDS.register("detector_medium",  () -> ModSounds.buildSoundEvent("detector_medium"));
    private static final RegistryObject<SoundEvent> DETECTOR_HIGH    = SOUNDS.register("detector_high",    () -> ModSounds.buildSoundEvent("detector_high"));
    private static final RegistryObject<SoundEvent> DETECTOR_MONSTER = SOUNDS.register("detector_monster", () -> ModSounds.buildSoundEvent("detector_monster"));
    private static final RegistryObject<SoundEvent> DETECTOR_SHRINE = SOUNDS.register("detector_shrine", () -> ModSounds.buildSoundEvent("detector_shrine"));

    private static final RegistryObject<Item> CIVIL_DETECTOR = ITEMS.register("civil_detector",
            () -> new CivilDetectorItem(new Item.Properties().stacksTo(1)));
    private static final RegistryObject<SimpleCraftingRecipeSerializer<CivilDetectorMapUpgradeRecipe>> DETECTOR_MAP_UPGRADE =
            RECIPE_SERIALIZERS.register("detector_map_upgrade",
                    () -> new SimpleCraftingRecipeSerializer<>(CivilDetectorMapUpgradeRecipe::new));
    @SuppressWarnings("unchecked")
    private static final RegistryObject<MenuType<TownCenterMenu>> TOWN_CENTER_MENU =
            (RegistryObject) MENUS.register("town_center", ModMenuTypes::createTownCenterMenuType);

    public CivilModForge() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        SOUNDS.register(modBus);
        ITEMS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onBuildCreativeTab);

        MinecraftForge.EVENT_BUS.addListener(this::onWorldLoad);
        MinecraftForge.EVENT_BUS.addListener(this::onWorldUnload);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTickStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerJoin);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogout);
        MinecraftForge.EVENT_BUS.addListener(this::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onRightClickBlock);

        registerNetworking();

        if (FMLEnvironment.dist.isClient()) {
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (mc, parent) -> civil.config.CivilConfigScreen.create(parent)
                    )
            );
            modBus.addListener(CivilModClientForge::onClientSetup);
            CivilModClientForge.registerClientEvents();
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        ModComponents.initialize();
        ModSounds.DETECTOR_DEFAULT = DETECTOR_DEFAULT.get();
        ModSounds.DETECTOR_LOW     = DETECTOR_LOW.get();
        ModSounds.DETECTOR_MEDIUM  = DETECTOR_MEDIUM.get();
        ModSounds.DETECTOR_HIGH    = DETECTOR_HIGH.get();
        ModSounds.DETECTOR_MONSTER = DETECTOR_MONSTER.get();
        ModSounds.DETECTOR_SHRINE = DETECTOR_SHRINE.get();
        ModItems.setCivilDetector(CIVIL_DETECTOR.get());
        ModRecipeSerializers.bindDetectorMapUpgrade(DETECTOR_MAP_UPGRADE.get());
        ModMenuTypes.setTownCenter(TOWN_CENTER_MENU.get());
        CivilMod.init();
        CivilMod.tryBootstrapMoonlightMapCompat(ModList.get().isLoaded("moonlight"));
    }

    @SuppressWarnings("UnusedAssignment")
    private void registerNetworking() {
        int id = 0;
        CHANNEL.messageBuilder(SonarChargePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(SonarChargePayload::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        CivilModClientForge.handleCharge(msg);
                    }
                })
                .add();
        CHANNEL.messageBuilder(SonarBoundaryPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(SonarBoundaryPayload::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        CivilModClientForge.handleBoundary(msg);
                    }
                })
                .add();
        CHANNEL.messageBuilder(UndyingAnchorPreTeleportPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(UndyingAnchorPreTeleportPayload::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        CivilModClientForge.handlePreTeleport(msg);
                    }
                })
                .add();
        CHANNEL.messageBuilder(UndyingAnchorParticlePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(UndyingAnchorParticlePayload::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        CivilModClientForge.handleAnchorParticles(msg);
                    }
                })
                .add();
        CHANNEL.messageBuilder(FarmShrineParticlePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(FarmShrineParticlePayload::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        CivilModClientForge.handleFarmShrineParticles(msg);
                    }
                })
                .add();
        CHANNEL.messageBuilder(ZoneTransitionPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(ZoneTransitionPayload::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        CivilModClientForge.handleZoneTransition(msg);
                    }
                })
                .add();
        CHANNEL.messageBuilder(ChunkBandTintPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(ChunkBandTintPayload::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        CivilModClientForge.handleChunkBandTint(msg);
                    }
                })
                .add();
        CHANNEL.messageBuilder(ChunkBandSyncPolicyPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(ChunkBandSyncPolicyPayload::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        CivilModClientForge.handleChunkBandSyncPolicy(msg);
                    }
                })
                .add();
        CHANNEL.messageBuilder(ChunkBandSubscribePayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(ChunkBandSubscribePayload::read)
                .consumerMainThread((msg, ctx) -> {
                    var sender = ctx.get().getSender();
                    if (sender instanceof ServerPlayer) {
                        ChunkBandTintSync.applySubscribe((ServerPlayer) sender, msg.subscribed());
                    }
                })
                .add();
        CHANNEL.messageBuilder(TownCenterParticlePayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(TownCenterParticlePayload::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        CivilModClientForge.handleTownCenterParticles(msg);
                    }
                })
                .add();
        CHANNEL.messageBuilder(TownCenterActivationBurstPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(TownCenterActivationBurstPayload::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        CivilModClientForge.handleTownCenterBurst(msg);
                    }
                })
                .add();
        CHANNEL.messageBuilder(TownCenterGuiSyncPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(TownCenterGuiSyncPayload::read)
                .consumerMainThread((msg, ctx) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        CivilModClientForge.handleTownCenterGuiSync(msg);
                    }
                })
                .add();
        CHANNEL.messageBuilder(TownCenterC2SPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> msg.write(buf))
                .decoder(TownCenterC2SPayload::read)
                .consumerMainThread((msg, ctx) -> {
                    var sender = ctx.get().getSender();
                    if (sender instanceof ServerPlayer) {
                        TownCenterMenuActionHandler.handle((ServerPlayer) sender, msg);
                    }
                })
                .add();
    }

    private void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            CivilMod.onWorldLoad(serverLevel.getServer(), serverLevel);
        }
    }

    private void onWorldUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            CivilMod.onWorldUnload(serverLevel.getServer(), serverLevel);
        }
    }

    private void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            TpsLogger.onStartTick(event.getServer());
        }
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            var server = event.getServer();
            CivilMod.onServerTick(server);
            TpsLogger.onEndTick(server);
            CivilDetectorAnimationReset.onServerTick(server);
            UndyingAnchorSaveHandler.onServerTick(server);
            UndyingAnchorParticleManager.onServerTick(server);
            FarmShrineParticleManager.onServerTick(server);
            TownCenterParticleManager.onServerTick(server);
            SonarScanManager.onServerTick(server);
            ChunkBandForgeLifecycle.onServerTickEnd(server);
        }
    }

    private void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (UndyingAnchorSaveHandler.trySave(player)) {
                event.setCanceled(true);
            }
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ChunkBandForgeLifecycle.onPlayerLogout(serverPlayer);
            CivilMod.onPlayerLogout(serverPlayer);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CivilAdminCommands.register(event.getDispatcher());
    }

    /**
     * Match Fabric {@code ServerLifecycleEvents.SERVER_STARTED}: resource manager is ready and
     * block/entity tags are bound, so {@link BlockWeightLoader} can resolve {@code #tag} entries.
     */
    private void onServerStarted(ServerStartedEvent event) {
        reloadCivilDatapacks(event.getServer().getResourceManager(), event.getServer().registryAccess());
        CivilMod.onTownCenterLevelsReloaded(event.getServer());
    }

    /**
     * Match Fabric {@code ServerLifecycleEvents.END_DATA_PACK_RELOAD}: after {@code /reload}, tags
     * are applied; Forge fires {@link TagsUpdatedEvent.UpdateCause#SERVER_DATA_LOAD} (1.20.1 name).
     */
    private void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        reloadCivilDatapacks(server.getResourceManager(), event.getRegistryAccess());
        CivilMod.onTownCenterLevelsReloaded(server);
    }

    private static void reloadCivilDatapacks(ResourceManager manager, RegistryAccess registryAccess) {
        BlockWeightLoader.reload(manager);
        HeadTypeLoader.reload(manager);
        ZonePolicyLoader.reload(manager, registryAccess);
        DimensionPolicyLoader.reload(manager, registryAccess);
        SpawnGateEntityLoader.reload(manager);
        MobFleeEntityLoader.reload(manager);
        PresenceKeepAliveLoader.reload(manager);
        TownCenterLevelLoader.reload(manager);
        CivilMod.onHeadTypesReloaded();
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            CivilDetectorAnimationReset.onPlayerJoin(serverPlayer);
            ChunkBandForgeLifecycle.onPlayerJoin(serverPlayer);
        }
    }

    /**
     * Align with Fabric: detector immediately after {@link Items#COMPASS} in the tools tab
     * (stable position and search indexing), not appended at the end via {@code accept}.
     */
    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) {
            return;
        }
        ItemStack compass = new ItemStack(Items.COMPASS);
        ItemStack detector = new ItemStack(ModItems.getCivilDetector());
        event.getEntries().putAfter(compass, detector, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        BlockPos pos = event.getPos();
        var state = event.getLevel().getBlockState(pos);
        if (state.is(Blocks.EMERALD_BLOCK)
                && event.getEntity() instanceof ServerPlayer sp
                && event.getLevel() instanceof ServerLevel world) {
            if (UndyingAnchorActivationHandler.tryActivate(sp, world, pos, event.getHand())) {
                event.setCanceled(true);
            }
            return;
        }
        if (state.is(Blocks.SOUL_CAMPFIRE)
                && event.getEntity() instanceof ServerPlayer sp2
                && event.getLevel() instanceof ServerLevel world2) {
            if (FarmShrineActivationHandler.tryActivate(sp2, world2, pos, event.getHand())) {
                event.setCanceled(true);
            }
            return;
        }
        if (!state.is(Blocks.BELL)) return;
        if (!event.getLevel().getBlockState(pos.below()).is(Blocks.LODESTONE)) return;
        if (!CivilConfig.auraEffectEnabled) return;
        var hitResult = event.getHitVec();
        double hitRelativeY = hitResult.getLocation().y - (double) pos.getY();
        if (!SonarScanManager.isBellProperHit(state, hitResult.getDirection(), hitRelativeY)) return;
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel world) {
            if (SonarScanManager.tryBellCooldown(player, world)) {
                SonarScanManager.startScan(player, world, pos, SonarType.STATIC);
            }
        }
    }

    public static void sendToPlayer(ServerPlayer player, civil.network.CivilS2CPacket payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(civil.network.CivilC2SPacket payload) {
        if (payload instanceof ChunkBandSubscribePayload p) {
            CHANNEL.sendToServer(p);
        } else if (payload instanceof TownCenterC2SPayload p) {
            CHANNEL.sendToServer(p);
        }
    }
}
