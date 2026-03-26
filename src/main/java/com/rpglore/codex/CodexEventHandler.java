package com.rpglore.codex;

import com.rpglore.config.LoreBookRegistry;
import com.rpglore.config.ServerConfig;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookItem;
import com.rpglore.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;

/**
 * Handles all Codex-related Forge events:
 * - Soul-binding (keep on death)
 * - Initial grant on first login
 * - Auto-collection on lore book pickup
 * - Drop prevention when soul-bound
 */
public class CodexEventHandler {

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!ServerConfig.CODEX_ENABLED.get() || !ServerConfig.CODEX_SOULBOUND.get()) return;

        event.getOriginal().reviveCaps();

        Inventory original = event.getOriginal().getInventory();
        Inventory newInv = event.getEntity().getInventory();

        for (int i = 0; i < original.getContainerSize(); i++) {
            ItemStack stack = original.getItem(i);
            if (stack.getItem() instanceof LoreCodexItem) {
                newInv.setItem(i, stack.copy());
                original.setItem(i, ItemStack.EMPTY);
                break; // only preserve one Codex
            }
        }

        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!ServerConfig.CODEX_ENABLED.get() || !ServerConfig.CODEX_SOULBOUND.get()) return;

        event.getDrops().removeIf(drop -> drop.getItem().getItem() instanceof LoreCodexItem);
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
        if (!ServerConfig.CODEX_ENABLED.get() || !ServerConfig.CODEX_GRANT_ON_FIRST_JOIN.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        CodexTrackingData data = CodexTrackingData.getInstance();
        if (data == null) return;
        if (data.hasEverReceivedCodex(serverPlayer.getUUID())) return;

        // Check if they already somehow have one
        for (int i = 0; i < serverPlayer.getInventory().getContainerSize(); i++) {
            if (serverPlayer.getInventory().getItem(i).getItem() instanceof LoreCodexItem) {
                data.markCodexGranted(serverPlayer.getUUID());
                return;
            }
        }

        ItemStack codex = new ItemStack(ModItems.LORE_CODEX.get());
        codex.getOrCreateTag().putString("codex_owner", serverPlayer.getUUID().toString());
        LoreCodexItem.syncItemNbt(codex, data, serverPlayer.getUUID());
        serverPlayer.getInventory().add(codex);
        data.markCodexGranted(serverPlayer.getUUID());
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!ServerConfig.CODEX_ENABLED.get() || !ServerConfig.CODEX_AUTO_COLLECT.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        ItemStack pickedUp = event.getItem().getItem();
        if (!(pickedUp.getItem() instanceof LoreBookItem)) return;

        CompoundTag tag = pickedUp.getTag();
        if (tag == null || !tag.contains("lore_id")) return;
        String bookId = tag.getString("lore_id");

        // Check if this book is excluded from the Codex
        if (tag.getBoolean("lore_codex_exclude")) return;

        // Check if player has a Codex in inventory
        ItemStack codexStack = findCodexInInventory(serverPlayer);
        if (codexStack.isEmpty()) return;

        CodexTrackingData data = CodexTrackingData.getInstance();
        if (data == null) return;

        // Duplicate prevention: if enabled and book already collected, cancel pickup
        if (data.isPreventDuplicates(serverPlayer.getUUID()) && data.hasBook(serverPlayer.getUUID(), bookId)) {
            event.setCanceled(true);
            return;
        }

        // Auto-collect: register new book in Codex
        if (data.addBook(serverPlayer.getUUID(), bookId)) {
            LoreCodexItem.syncItemNbt(codexStack, data, serverPlayer.getUUID());

            // Get book title for notification
            Optional<LoreBookDefinition> optDef = LoreBookRegistry.getById(bookId);
            String title = optDef.map(LoreBookDefinition::title).orElse(bookId);

            serverPlayer.displayClientMessage(
                    Component.translatable("rpg_lore.codex.collected",
                            Component.literal(title).withStyle(ChatFormatting.GOLD)),
                    true);

            serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5f, 1.2f);
        }
    }

    private static ItemStack findCodexInInventory(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof LoreCodexItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
