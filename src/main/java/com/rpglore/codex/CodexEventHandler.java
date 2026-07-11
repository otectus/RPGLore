package com.rpglore.codex;

import com.rpglore.RpgLoreMod;
import com.rpglore.compat.CuriosCompat;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.config.ServerConfig;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookItem;
import com.rpglore.network.ClientboundCodexCollectionEventPacket;
import com.rpglore.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;

/**
 * Handles all Codex-related Forge events:
 * - Soul-binding (keep on death, including Curios slots)
 * - Initial grant on first login
 * - Auto-collection: lore books go INTO the Codex instead of inventory
 * - Drop prevention when soul-bound
 */
public class CodexEventHandler {

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!ServerConfig.CODEX_ENABLED.get() || !ServerConfig.CODEX_SOULBOUND.get()) return;

        // With keepInventory, vanilla (and Curios) already carried everything over;
        // restoring on top of that would duplicate the Codex.
        if (event.getEntity().level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        if (!LoreCodexItem.findCodex(event.getEntity()).isEmpty()) return;

        event.getOriginal().reviveCaps();

        try {
            // onPlayerDrops parked the Codex back in the dead player's inventory
            Inventory original = event.getOriginal().getInventory();
            Inventory newInv = event.getEntity().getInventory();

            for (int i = 0; i < original.getContainerSize(); i++) {
                ItemStack stack = original.getItem(i);
                if (stack.getItem() instanceof LoreCodexItem) {
                    original.setItem(i, ItemStack.EMPTY);
                    if (!newInv.add(stack)) {
                        RpgLoreMod.LOGGER.warn("Failed to restore Codex to new inventory for {}",
                                event.getEntity().getName().getString());
                    }
                    return;
                }
            }

            // Fallback: Codex still sitting in the dead player's Curios slots
            // (e.g. another mod suppressed the curio drop)
            if (CuriosCompat.isLoaded()) {
                ItemStack curioCodex = CuriosCompat.findCodexInCurios(event.getOriginal());
                if (!curioCodex.isEmpty()) {
                    if (!newInv.add(curioCodex.copy())) {
                        RpgLoreMod.LOGGER.warn("Failed to restore Curios Codex to new inventory for {}",
                                event.getEntity().getName().getString());
                    }
                }
            }
        } finally {
            event.getOriginal().invalidateCaps();
        }
    }

    @SubscribeEvent
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!ServerConfig.CODEX_ENABLED.get() || !ServerConfig.CODEX_SOULBOUND.get()) return;

        // By the time this fires, the dying player's inventory has already been
        // emptied into the drops list. Removing the Codex drop without putting it
        // somewhere would delete it — park it back in the dead player's (now empty)
        // inventory, where onPlayerClone picks it up for the respawned player.
        event.getDrops().removeIf(drop -> {
            ItemStack stack = drop.getItem();
            return stack.getItem() instanceof LoreCodexItem && player.getInventory().add(stack);
        });
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!ServerConfig.CODEX_ENABLED.get() || !ServerConfig.CODEX_SOULBOUND.get()) return;

        if (event.getEntity().getItem().getItem() instanceof LoreCodexItem) {
            event.getPlayer().getInventory().add(event.getEntity().getItem());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ServerConfig.CODEX_ENABLED.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        CodexService service = CodexService.get();
        if (service == null) return;

        if (ServerConfig.CODEX_GRANT_ON_FIRST_JOIN.get()) {
            service.grantCodex(serverPlayer);
        }

        // Warm the client's Codex cache so the first open of the session shows the
        // real collection (and its toggle button) instead of empty placeholder data
        service.resyncPlayer(serverPlayer);
    }

    /**
     * When a lore book is picked up it always feeds into the Codex instead of the
     * inventory (a book only exists in book form on the ground or once extracted):
     * - New book: recorded as the permanent, readable master copy.
     * - Duplicate of a collected book: banked as an extractable spare copy
     *   (lower-pitch absorption sound), UNLESS "leave on ground" is enabled —
     *   in which case the pickup is denied so the book stays on the ground.
     */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!ServerConfig.CODEX_ENABLED.get() || !ServerConfig.CODEX_AUTO_COLLECT.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        ItemStack pickedUp = event.getItem().getItem();
        if (!(pickedUp.getItem() instanceof LoreBookItem)) return;

        CompoundTag tag = pickedUp.getTag();
        if (tag == null || !tag.contains("lore_id", Tag.TAG_STRING)) return;
        String bookId = tag.getString("lore_id");
        if (bookId.isEmpty()) return;

        // Books excluded from Codex go to inventory normally
        if (tag.getBoolean("lore_codex_exclude")) return;

        // Must have a Codex (inventory or Curios)
        ItemStack codexStack = LoreCodexItem.findCodex(serverPlayer);
        if (codexStack.isEmpty()) return;

        CodexTrackingData data = CodexTrackingData.getInstance();
        if (data == null) return;

        CodexService service = CodexService.get();
        if (service == null) return;

        boolean duplicate = data.hasBook(serverPlayer.getUUID(), bookId);

        if (duplicate) {
            // "Leave on ground": deny the pickup, do not absorb (book stays in the world)
            if (data.isPreventDuplicates(serverPlayer.getUUID())) {
                event.setCanceled(true);
                return;
            }
            // Otherwise bank it as a spare copy
            service.bankDuplicate(serverPlayer, bookId);
        } else {
            // NEW book: record it as the permanent master copy
            service.collectBook(serverPlayer, bookId);
        }

        // Consume the item entity (destroy it from the world)
        event.getItem().discard();

        // Prevent vanilla pickup logic from running
        event.setResult(Event.Result.ALLOW);
        event.setCanceled(true);

        // Send collection event to client (client decides whether to show notification/sound)
        Optional<LoreBookDefinition> optDef = LoreBookRegistry.getById(bookId);
        String title = optDef.map(LoreBookDefinition::title).orElse(bookId);
        ModNetwork.sendToPlayer(
                new ClientboundCodexCollectionEventPacket(title, duplicate), serverPlayer);
    }
}
