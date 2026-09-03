package com.rpglore.acquisition;

import com.rpglore.codex.CodexService;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.config.ServerConfig;
import com.rpglore.lore.LoreBookDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import java.util.Optional;

/**
 * The single entry point for putting a lore book into a player's Codex, whatever the
 * source. Other mods should call {@link #collect(ServerPlayer, String, LoreAcquisitionSource)}
 * rather than touching {@code CodexService} directly: this is where the Codex-enabled
 * check, the exclusion rule and the {@link LoreCollectedEvent} broadcast live.
 *
 * <p>Server side only. Every method must run on the server thread.
 */
public final class LoreAcquisitionService {

    /** Why a book was handed to a player. Carried on {@link LoreCollectedEvent}. */
    public enum LoreAcquisitionSource {
        /** Dropped by a mob and picked up through the loot modifier path. */
        ENTITY_DROP,
        /** Generated into a container or other named loot table. */
        LOOT_TABLE,
        /** Granted for earning an advancement. */
        ADVANCEMENT,
        /** Picked up off the ground as a physical book. */
        PICKUP,
        /** Added by an operator command. */
        COMMAND,
        /** Added by another mod through this service. */
        API
    }

    /** Outcome of a collection attempt. */
    public enum CollectResult {
        /** The book entered the Codex for the first time. */
        NEWLY_COLLECTED,
        /** The player already had this book; nothing changed. */
        ALREADY_COLLECTED,
        /** No book is loaded under that id. */
        NOT_FOUND,
        /** The book is marked {@code codex_exclude} and never enters the Codex. */
        EXCLUDED,
        /** The Codex feature is switched off in the server config. */
        CODEX_DISABLED
    }

    private LoreAcquisitionService() {}

    /**
     * Records a book in the player's Codex and, when that is a first-time collection,
     * fires {@link LoreCollectedEvent} on the Forge event bus.
     *
     * @param player the receiving player
     * @param loreId the book id, as it appears in the registry
     * @param source what caused the collection
     * @return what actually happened; never null
     */
    public static CollectResult collect(ServerPlayer player, String loreId, LoreAcquisitionSource source) {
        if (!ServerConfig.CODEX_ENABLED.get()) return CollectResult.CODEX_DISABLED;

        Optional<LoreBookDefinition> optDef = LoreBookRegistry.getById(loreId);
        if (optDef.isEmpty()) return CollectResult.NOT_FOUND;
        if (optDef.get().codexExclude()) return CollectResult.EXCLUDED;

        CodexService service = CodexService.get();
        if (service == null) return CollectResult.CODEX_DISABLED;

        if (!service.collectBook(player, loreId)) {
            return CollectResult.ALREADY_COLLECTED;
        }

        MinecraftForge.EVENT_BUS.post(
                new LoreCollectedEvent(player, new ResourceLocation(loreId), source));
        return CollectResult.NEWLY_COLLECTED;
    }
}
