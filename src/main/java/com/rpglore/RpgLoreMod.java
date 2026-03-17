package com.rpglore;

import com.rpglore.command.RpgLoreCommands;
import com.rpglore.config.BooksConfigLoader;
import com.rpglore.config.ClientConfig;
import com.rpglore.config.ServerConfig;
import com.rpglore.loot.ModLootModifiers;
import com.rpglore.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
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

        // FORGE bus listeners
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);

        LOGGER.info("RPG Lore initializing");
    }

    private void onServerStarting(final ServerStartingEvent event) {
        BooksConfigLoader.ensureDefaults();
        BooksConfigLoader.reload();
    }

    private void onRegisterCommands(final RegisterCommandsEvent event) {
        RpgLoreCommands.register(event.getDispatcher());
    }
}
