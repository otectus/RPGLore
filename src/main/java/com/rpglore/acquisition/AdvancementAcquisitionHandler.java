package com.rpglore.acquisition;

import com.rpglore.RpgLoreMod;
import com.rpglore.acquisition.LoreAcquisitionService.LoreAcquisitionSource;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.config.LoreBookRegistry.AdvancementCandidate;
import com.rpglore.config.ServerConfig;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookItem;
import com.rpglore.lore.acquisition.AcquisitionRule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

/**
 * Grants books whose {@code advancement} acquisition rule names an advancement the
 * player just earned. Registered on the Forge bus.
 */
public final class AdvancementAcquisitionHandler {

    private AdvancementAcquisitionHandler() {}

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!ServerConfig.ENABLE_ADVANCEMENTS.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation advancementId = event.getAdvancement().getId();
        List<AdvancementCandidate> candidates = LoreBookRegistry.getAdvancementRules(advancementId);
        if (candidates.isEmpty()) return;

        for (AdvancementCandidate candidate : candidates) {
            if (candidate.rule().delivery() == AcquisitionRule.Delivery.INVENTORY) {
                giveToInventory(player, candidate.definition());
            } else {
                // Already-collected books are a no-op: the Codex never banks a spare here.
                LoreAcquisitionService.collect(player, candidate.definition().id(),
                        LoreAcquisitionSource.ADVANCEMENT);
            }
        }
    }

    /**
     * Hands over a physical book, once. The copy is recorded against a limit of one so a
     * revoke and re-grant of the advancement cannot mint a second book.
     */
    private static void giveToInventory(ServerPlayer player, LoreBookDefinition def) {
        if (!LoreBookRegistry.canPlayerReceive(player.getUUID(), def.id(), 1)) return;

        ItemStack stack = LoreBookItem.createStack(def);
        // Drops at the player's feet when the inventory is full rather than vanishing.
        ItemHandlerHelper.giveItemToPlayer(player, stack);
        LoreBookRegistry.recordPlayerReceived(player.getUUID(), def.id());

        RpgLoreMod.LOGGER.debug("Granted lore book '{}' to {} for an advancement", def.id(),
                player.getName().getString());
    }
}
