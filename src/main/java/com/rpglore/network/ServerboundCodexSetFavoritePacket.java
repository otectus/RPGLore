package com.rpglore.network;

import com.rpglore.codex.CodexService;
import com.rpglore.config.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sets or clears the favorite flag on a collected book. Idempotent: the client sends
 * the value it wants, not a toggle, so a dropped packet cannot desync the flag.
 */
public class ServerboundCodexSetFavoritePacket {

    private final String bookId;
    private final boolean favorite;

    public ServerboundCodexSetFavoritePacket(String bookId, boolean favorite) {
        this.bookId = bookId;
        this.favorite = favorite;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(bookId);
        buf.writeBoolean(favorite);
    }

    public static ServerboundCodexSetFavoritePacket decode(FriendlyByteBuf buf) {
        return new ServerboundCodexSetFavoritePacket(buf.readUtf(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!ServerConfig.CODEX_ENABLED.get()) return;

            CodexService service = CodexService.get();
            if (service == null) return;

            service.setFavorite(player, bookId, favorite);
        });
        ctx.get().setPacketHandled(true);
    }
}
