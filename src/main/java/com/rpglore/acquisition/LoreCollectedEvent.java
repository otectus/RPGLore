package com.rpglore.acquisition;

import com.rpglore.acquisition.LoreAcquisitionService.LoreAcquisitionSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the Forge event bus, server side, right after a lore book enters a player's
 * Codex for the first time. Duplicates and no-op collections do not fire it.
 *
 * <p>Not cancelable: by the time it is posted the book is already recorded. Listen for it
 * to hand out rewards, drive quests or mirror the collection into another mod's progress.
 */
public class LoreCollectedEvent extends Event {

    private final ServerPlayer player;
    private final ResourceLocation loreId;
    private final LoreAcquisitionSource source;

    public LoreCollectedEvent(ServerPlayer player, ResourceLocation loreId, LoreAcquisitionSource source) {
        this.player = player;
        this.loreId = loreId;
        this.source = source;
    }

    /** The player who collected the book. */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** The book's registry id. */
    public ResourceLocation getLoreId() {
        return loreId;
    }

    /** What caused the collection. */
    public LoreAcquisitionSource getSource() {
        return source;
    }
}
