package com.rpglore.network;

import com.rpglore.codex.CodexTrackingData;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.config.ServerConfig;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Creates a physical copy of a book from the Codex into the player's inventory.
 * Since the Codex stores the original, copies are created directly from its data.
 * The copy has generation incremented (capped at 3) to distinguish it from originals.
 */
public class ServerboundCodexCopyBookPacket {

    private final String bookId;

    public ServerboundCodexCopyBookPacket(String bookId) {
        this.bookId = bookId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(bookId);
    }

    public static ServerboundCodexCopyBookPacket decode(FriendlyByteBuf buf) {
        return new ServerboundCodexCopyBookPacket(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!ServerConfig.CODEX_ALLOW_COPY.get()) return;

            CodexTrackingData data = CodexTrackingData.getInstance();
            if (data == null) return;

            // Validate book is in the player's Codex collection
            if (!data.hasBook(player.getUUID(), bookId)) return;

            // Look up the book definition
            Optional<LoreBookDefinition> optDef = LoreBookRegistry.getById(bookId);
            if (optDef.isEmpty()) return;

            // Reject if inventory is full to avoid unpickable dropped copies
            if (player.getInventory().getFreeSlot() == -1) {
                player.displayClientMessage(
                        Component.translatable("rpg_lore.codex.copy.inventory_full"), false);
                return;
            }

            // Create a physical copy with generation incremented to mark it as a copy
            ItemStack copy = LoreBookItem.createStack(optDef.get());
            CompoundTag copyTag = copy.getOrCreateTag();
            int newGen = Math.min(copyTag.getInt("generation") + 1, 3);
            copyTag.putInt("generation", newGen);

            player.getInventory().add(copy);

            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.5f, 1.0f);
        });
        ctx.get().setPacketHandled(true);
    }
}
