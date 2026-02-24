package civil.neoforge;

import civil.CivilMod;
import civil.ModItems;
import civil.ModSounds;
import civil.component.ModComponents;
import civil.item.CivilDetectorItem;
import civil.aura.SonarBoundaryPayload;
import civil.aura.SonarChargePayload;
import civil.aura.SonarScanManager;
import civil.item.CivilDetectorAnimationReset;
import civil.perf.TpsLogger;
import civil.registry.BlockWeightLoader;
import civil.registry.HeadTypeLoader;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge entry point. Calls common {@link CivilMod#init()} and registers
 * all NeoForge-specific events, networking, resource loaders, and item groups.
 *
 * <p>NeoForge freezes registries before mod construction, so all registry entries
 * must use {@link DeferredRegister} instead of direct {@code Registry.register()} calls.
 * Common static fields are populated after registry events fire via {@link FMLCommonSetupEvent}.
 */
@Mod("civil")
public class CivilModNeoForge {

    // ── Deferred Registers ──────────────────────────────────────────────
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CivilMod.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, CivilMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, CivilMod.MOD_ID);

    // ── Deferred Holders (components) ───────────────────────────────────
    @SuppressWarnings("unchecked")
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> DETECTOR_DISPLAY =
            (DeferredHolder<DataComponentType<?>, DataComponentType<String>>)
            (DeferredHolder<?, ?>) COMPONENTS.register("detector_display", ModComponents::buildDetectorDisplay);
    @SuppressWarnings("unchecked")
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> DETECTOR_ANIMATION_END =
            (DeferredHolder<DataComponentType<?>, DataComponentType<Long>>)
            (DeferredHolder<?, ?>) COMPONENTS.register("detector_animation_end", ModComponents::buildDetectorAnimationEnd);

    // ── Deferred Holders (sounds) ───────────────────────────────────────
    private static final DeferredHolder<SoundEvent, SoundEvent> SOUND_DEFAULT =
            SOUNDS.register("detector_default", () -> ModSounds.buildSoundEvent("detector_default"));
    private static final DeferredHolder<SoundEvent, SoundEvent> SOUND_LOW =
            SOUNDS.register("detector_low", () -> ModSounds.buildSoundEvent("detector_low"));
    private static final DeferredHolder<SoundEvent, SoundEvent> SOUND_MEDIUM =
            SOUNDS.register("detector_medium", () -> ModSounds.buildSoundEvent("detector_medium"));
    private static final DeferredHolder<SoundEvent, SoundEvent> SOUND_HIGH =
            SOUNDS.register("detector_high", () -> ModSounds.buildSoundEvent("detector_high"));
    private static final DeferredHolder<SoundEvent, SoundEvent> SOUND_MONSTER =
            SOUNDS.register("detector_monster", () -> ModSounds.buildSoundEvent("detector_monster"));

    // ── Deferred Holders (items) ────────────────────────────────────────
    private static final DeferredHolder<Item, Item> CIVIL_DETECTOR =
            ITEMS.register("civil_detector", () -> {
                ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                        Identifier.fromNamespaceAndPath(CivilMod.MOD_ID, "civil_detector"));
                Item.Properties props = new Item.Properties()
                        .setId(key)
                        .stacksTo(1)
                        .component(DETECTOR_DISPLAY.get(), "default");
                return new CivilDetectorItem(props);
            });

    public CivilModNeoForge(IEventBus modBus, Dist dist, ModContainer modContainer) {
        COMPONENTS.register(modBus);
        SOUNDS.register(modBus);
        ITEMS.register(modBus);

        modBus.addListener(this::onCommonSetup);
        modBus.addListener(this::onRegisterPayloads);
        modBus.addListener(this::onBuildCreativeTab);

        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(this::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(this::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(this::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(this::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);

        CivilMod.init();

        if (dist.isClient()) {
            CivilModClientNeoForge.init(modBus, modContainer);
        }
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        ModComponents.DETECTOR_DISPLAY = DETECTOR_DISPLAY.get();
        ModComponents.DETECTOR_ANIMATION_END = DETECTOR_ANIMATION_END.get();
        ModSounds.DETECTOR_DEFAULT = SOUND_DEFAULT.get();
        ModSounds.DETECTOR_LOW = SOUND_LOW.get();
        ModSounds.DETECTOR_MEDIUM = SOUND_MEDIUM.get();
        ModSounds.DETECTOR_HIGH = SOUND_HIGH.get();
        ModSounds.DETECTOR_MONSTER = SOUND_MONSTER.get();
        ModItems.setCivilDetector(CIVIL_DETECTOR.get());
        CivilMod.LOGGER.debug("Common fields populated from deferred holders (NeoForge)");
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("civil");
        registrar.playToClient(SonarChargePayload.ID, SonarChargePayload.CODEC,
                NeoForgeClientPayloadHandler::handleSonarCharge);
        registrar.playToClient(SonarBoundaryPayload.ID, SonarBoundaryPayload.CODEC,
                NeoForgeClientPayloadHandler::handleSonarBoundary);
    }

    private void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(CIVIL_DETECTOR.get());
        }
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        ResourceManager manager = server.getResourceManager();
        BlockWeightLoader.reload(manager);
        HeadTypeLoader.reload(manager);
    }

    private void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = event.getPlayerList().getServer();
        ResourceManager manager = server.getResourceManager();
        BlockWeightLoader.reload(manager);
        HeadTypeLoader.reload(manager);
    }

    private void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel world) {
            CivilMod.onWorldLoad(world.getServer(), world);
        }
    }

    private void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel world) {
            CivilMod.onWorldUnload(world.getServer(), world);
        }
    }

    private void onServerTickPre(ServerTickEvent.Pre event) {
        TpsLogger.onStartTick(event.getServer());
    }

    private void onServerTickPost(ServerTickEvent.Post event) {
        CivilMod.onServerTick(event.getServer());
        TpsLogger.onEndTick(event.getServer());
        CivilDetectorAnimationReset.onServerTick(event.getServer());
        SonarScanManager.onServerTick(event.getServer());
    }

    private void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel world
                && event.getChunk() instanceof LevelChunk chunk) {
            CivilMod.onChunkLoad(world, chunk);
        }
    }

    private void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CivilDetectorAnimationReset.onPlayerJoin(player);
        }
    }
}
