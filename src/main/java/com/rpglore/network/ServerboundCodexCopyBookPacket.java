package com.rpglore.network;

import com.rpglore.codex.CodexTrackingData;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.config.ServerConfig;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

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

            // Validate book is in collection
            if (!data.hasBook(player.getUUID(), bookId)) return;

            // Find physical copy in inventory
            int slot = findBookInInventory(player, bookId);
            if (slot < 0) {
                player.displayClientMessage(
                        Component.translatable("rpg_lore.codex.no_physical_copy")
                                .withStyle(ChatFormatting.RED),
                        true);
                return;
            }

            // Consume the physical copy
            player.getInventory().removeItem(slot, 1);

            // Create a new copy with generation incremented
            Optional<LoreBookDefinition> optDef = LoreBookRegistry.getById(bookId);
            if (optDef.isEmpty()) return;

            ItemStack copy = LoreBookItem.createStack(optDef.get());
            CompoundTag copyTag = copy.getOrCreateTag();
            int newGen = Math.min(copyTag.getInt("generation") + 1, 3);
            copyTag.putInt("generation", newGen);

            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static int findBookInInventory(ServerPlayer player, String bookId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof LoreBookItem) {
                CompoundTag tag = stack.getTag();
                if (tag != null && bookId.equals(tag.getString("lore_id"))) {
                    return i;
                }
            }
        }
        return -1;
    }
}
