package com.rpglore;

import com.rpglore.codex.CodexEventHandler;
import com.rpglore.codex.CodexService;
import com.rpglore.codex.CodexTrackingData;
import com.rpglore.command.RpgLoreCommands;
import com.rpglore.config.BooksConfigLoader;
import com.rpglore.config.ClientConfig;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.config.ServerConfig;
import com.rpglore.data.LoreTrackingData;
import com.rpglore.lore.LoreBookLecternHandler;
import com.rpglore.loot.ModLootModifiers;
import com.rpglore.network.ModNetwork;
import com.rpglore.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RpgLoreMod.MODID)
public class RpgLoreMod {
    public static final String MODID = "rpg_lore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RpgLoreMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register DeferredRegisters on MOD bus
        ModItems.ITEMS.register(modEventBus);
        ModLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);

        // Register configs with subfolder paths
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC,
                "rpg_lore/server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC,
                "rpg_lore/client.toml");

        // Register network channel
        ModNetwork.register();

        // MOD bus listeners
        modEventBus.addListener(this::onBuildCreativeTabContents);

        // FORGE bus listeners
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);

        // Register Codex event handler
        MinecraftForge.EVENT_BUS.register(CodexEventHandler.class);

        // Allow LoreBookItem to be placed in vanilla lecterns
        MinecraftForge.EVENT_BUS.register(LoreBookLecternHandler.class);

        LOGGER.info("RPG Lore initializing");
    }

    // L1: Creative tab registration
    private void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.LORE_BOOK);
            if (ServerConfig.CODEX_ENABLED.get()) {
                event.accept(ModItems.LORE_CODEX);
            }
        }
    }

    private void onServerStarting(final ServerStartingEvent event) {
        ServerLevel overworld = event.getServer().overworld();

        // Set up persistent per-player copy tracking from the overworld's saved data
        LoreTrackingData trackingData = LoreTrackingData.getOrCreate(overworld);
        LoreBookRegistry.setTrackingData(trackingData);

        // Set up Codex tracking data and service
        CodexTrackingData codexData = CodexTrackingData.getOrCreate(overworld);
        CodexTrackingData.setInstance(codexData);
        CodexService.init(codexData);

        BooksConfigLoader.ensureDefaults();
        BooksConfigLoader.reload();

        // Prune stale entries from both tracking systems
        trackingData.pruneStaleEntries(LoreBookRegistry.getAllBookIds());
        codexData.pruneStaleEntries(LoreBookRegistry.getCodexEligibleIds());
    }

    private void onServerStopped(final ServerStoppedEvent event) {
        // Clear tracking data references to avoid holding stale world data
        LoreBookRegistry.setTrackingData(null);
        CodexTrackingData.setInstance(null);
        CodexService.clear();
    }

    private void onRegisterCommands(final RegisterCommandsEvent event) {
        RpgLoreCommands.register(event.getDispatcher());
    }
}
