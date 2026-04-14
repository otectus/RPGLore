package com.rpglore.codex;

import com.rpglore.RpgLoreMod;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.data.LoreTrackingData;
import com.rpglore.network.ClientboundCodexSyncPacket;
import com.rpglore.network.ModNetwork;
import com.rpglore.registry.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Centralizes all Codex state mutations. Every method that changes Codex state
 * atomically updates: SavedData -> item NBT cache -> client sync packet.
 * This eliminates drift between the three state locations.
 *
 * <p><b>Threading:</b> the singleton {@code instance} is lifecycle-managed from
 * {@link com.rpglore.RpgLoreMod#onServerStarting} / {@code onServerStopped} on
 * the main server thread. All mutation methods ({@link #collectBook}, etc.) must
 * be called on the main server thread — they touch SavedData and player
 * inventories. Packet handlers that invoke these methods MUST wrap the call in
 * {@code ctx.enqueueWork(...)}; Forge does not auto-queue in 1.20.1.
 *
 * <p>The {@code volatile} on {@code instance} is a cheap safety belt so that a
 * packet-decoder-thread read of {@link #get()} sees the init/clear transition
 * without torn state — even though dispatched work should always run on the
 * main thread afterwards.
 */
public final class CodexService {

    @Nullable
    private static volatile CodexService instance;

    private final CodexTrackingData codexData;

    private CodexService(CodexTrackingData codexData) {
        this.codexData = codexData;
    }

    public static void init(CodexTrackingData codexData) {
        instance = new CodexService(codexData);
    }

    public static void clear() {
        instance = null;
    }

    @Nullable
    public static CodexService get() {
        return instance;
    }

    public CodexTrackingData getTrackingData() {
        return codexData;
    }

    // --- Mutations ---

    /**
     * Adds a book to the player's Codex collection.
     * @return true if the book was newly added
     */
    public boolean collectBook(ServerPlayer player, String bookId) {
        boolean added = codexData.addBook(player.getUUID(), bookId);
        if (added) {
            syncAll(player);
        }
        return added;
    }

    /**
     * Removes a book from the player's Codex collection.
     * @return true if the book was removed
     */
    public boolean removeBook(ServerPlayer player, String bookId) {
        boolean removed = codexData.removeBook(player.getUUID(), bookId);
        if (removed) {
            syncAll(player);
        }
        return removed;
    }

    /**
     * Clears all collected books for a player.
     */
    public void resetPlayer(ServerPlayer player) {
        codexData.clearPlayer(player.getUUID());
        syncAll(player);
    }

    /**
     * Toggles duplicate prevention for a player.
     */
    public void toggleDuplicatePrevention(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean current = codexData.isPreventDuplicates(uuid);
        codexData.setPreventDuplicates(uuid, !current);
        syncAll(player);
    }

    /**
     * Grants a Codex item to a player. Handles full inventory by dropping.
     * Only marks as granted after successful delivery.
     * @return true if the Codex was delivered
     */
    public boolean grantCodex(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // Already granted
        if (codexData.hasEverReceivedCodex(uuid)) return false;

        // Already has one (inventory or Curios)
        if (!LoreCodexItem.findCodex(player).isEmpty()) {
            codexData.markCodexGranted(uuid);
            return false;
        }

        ItemStack codex = new ItemStack(ModItems.LORE_CODEX.get());
        LoreCodexItem.syncItemNbt(codex, codexData, uuid);

        if (!player.getInventory().add(codex)) {
            // Inventory full — drop on ground
            ItemEntity drop = player.drop(codex, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setThrower(uuid);
            }
        }

        codexData.markCodexGranted(uuid);
        return true;
    }

    /**
     * Prunes stale entries from both tracking systems and resyncs all online players.
     */
    public void pruneAndResync(MinecraftServer server) {
        LoreTrackingData trackingData = LoreBookRegistry.getTrackingData();
        if (trackingData != null) {
            trackingData.pruneStaleEntries(LoreBookRegistry.getAllBookIds());
        }
        codexData.pruneStaleEntries(LoreBookRegistry.getCodexEligibleIds());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            resyncPlayer(player);
        }
    }

    /**
     * Re-syncs a specific player's Codex item NBT and client cache.
     */
    public void resyncPlayer(ServerPlayer player) {
        syncAll(player);
    }

    // --- Private ---

    /**
     * Atomically updates all three state locations:
     * 1. SavedData (already done by caller)
     * 2. Item NBT cache on the held Codex
     * 3. Client sync packet
     */
    private void syncAll(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // Update item NBT on the player's Codex (if they have one)
        ItemStack codexStack = LoreCodexItem.findCodex(player);
        if (!codexStack.isEmpty()) {
            LoreCodexItem.syncItemNbt(codexStack, codexData, uuid);
        }

        // Send sync packet to client
        ClientboundCodexSyncPacket packet = ClientboundCodexSyncPacket.create(uuid, codexData);
        ModNetwork.sendToPlayer(packet, player);
    }
}
