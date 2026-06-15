package com.rpglore.network;

import com.rpglore.codex.CodexService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Requests extraction of a physical copy of a book from the Codex into the player's
 * inventory. Extraction draws down the player's banked spare copies (the original
 * master is never extractable); the extracted book has its generation incremented
 * (capped at 3) to distinguish it from originals. All logic lives in
 * {@link CodexService#extractCopy} so the mutation resyncs the UI counts.
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

            CodexService service = CodexService.get();
            if (service == null) return;

            // Extraction draws down the player's banked spare copies (and resyncs the UI)
            CodexService.ExtractResult result = service.extractCopy(player, bookId);
            switch (result) {
                case INVENTORY_FULL -> player.displayClientMessage(
                        Component.translatable("rpg_lore.codex.copy.inventory_full"), false);
                case NO_COPIES -> player.displayClientMessage(
                        Component.translatable("rpg_lore.codex.copy.no_copies"), false);
                default -> { /* SUCCESS / DISABLED / INVALID: no message */ }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
