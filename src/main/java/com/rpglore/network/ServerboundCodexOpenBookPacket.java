package com.rpglore.network;

import com.rpglore.codex.CodexTrackingData;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

public class ServerboundCodexOpenBookPacket {

    private final String bookId;

    public ServerboundCodexOpenBookPacket(String bookId) {
        this.bookId = bookId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(bookId);
    }

    public static ServerboundCodexOpenBookPacket decode(FriendlyByteBuf buf) {
        return new ServerboundCodexOpenBookPacket(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            CodexTrackingData data = CodexTrackingData.getInstance();
            if (data == null) return;

            // Validate book is in player's collection
            if (!data.hasBook(player.getUUID(), bookId)) return;

            Optional<LoreBookDefinition> optDef = LoreBookRegistry.getById(bookId);
            if (optDef.isEmpty()) return;

            // Create a temporary ItemStack for the book and send it to the client
            ItemStack bookStack = LoreBookItem.createStack(optDef.get());
            ClientboundCodexOpenBookPacket packet = new ClientboundCodexOpenBookPacket(bookStack);
            ModNetwork.sendToPlayer(packet, player);
        });
        ctx.get().setPacketHandled(true);
    }
}
