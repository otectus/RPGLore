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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
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

    /**
     * Pulls the Codex out of the dying player before the inventory is emptied into
     * drops, and parks it in save data until the respawn clone arrives.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ServerConfig.CODEX_ENABLED.get() || !ServerConfig.CODEX_SOULBOUND.get()) return;

        // With keepInventory, vanilla (and Curios) already carry everything over;
        // stashing on top of that would duplicate the Codex.
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;

        CodexTrackingData data = CodexTrackingData.getInstance();
        if (data == null) return;

        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() instanceof LoreCodexItem) {
                inv.setItem(i, ItemStack.EMPTY);
                data.stashCodex(player.getUUID(), stack, null, 0);
                return;
            }
        }

        // Otherwise the Codex is worn in a Curios slot — remember which one
        if (CuriosCompat.isLoaded()) {
            CuriosCompat.ExtractedCurio extracted = CuriosCompat.extractCodexFromCurios(player);
            if (extracted != null) {
                data.stashCodex(player.getUUID(), extracted.stack(), extracted.slotId(), extracted.index());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ServerConfig.CODEX_ENABLED.get() || !ServerConfig.CODEX_SOULBOUND.get()) return;

        CodexTrackingData data = CodexTrackingData.getInstance();
        if (data == null) return;

        // Safety net: if onLivingDeath missed the Codex (another mod moved it), stash
        // it from the drops list rather than letting it fall on the ground.
        event.getDrops().removeIf(drop -> {
            if (!(drop.getItem().getItem() instanceof LoreCodexItem)) return false;
            RpgLoreMod.LOGGER.warn("Codex reached death drops for {} — stashing via safety net",
                    player.getName().getString());
            data.stashCodex(player.getUUID(), drop.getItem(), null, 0);
            return true;
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        restoreStashedCodex(player);
    }

    /** Gives a stashed Codex back, preferring the Curios slot it was worn in. */
    private static void restoreStashedCodex(ServerPlayer player) {
        CodexTrackingData data = CodexTrackingData.getInstance();
        if (data == null) return;

        CodexTrackingData.StashedCodex stash = data.popStashedCodex(player.getUUID());
        if (stash == null) return;

        if (!LoreCodexItem.findCodex(player).isEmpty()) {
            RpgLoreMod.LOGGER.warn("Discarding stashed Codex for {} — player already has one",
                    player.getName().getString());
            return;
        }

        if (stash.curioSlot() == null || !CuriosCompat.isLoaded()
                || !CuriosCompat.equipCodexInCurios(player, stash.stack(), stash.curioSlot(), stash.curioIndex())) {
            player.getInventory().placeItemBackInInventory(stash.stack());
        }
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
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        // Catch a stash left behind by a logout between death and respawn
        if (!serverPlayer.isDeadOrDying()) {
            restoreStashedCodex(serverPlayer);
        }

        if (!ServerConfig.CODEX_ENABLED.get()) return;

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
