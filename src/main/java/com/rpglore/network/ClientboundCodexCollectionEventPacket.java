package com.rpglore.network;

import com.rpglore.codex.LoreCodexClientHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from server to client when a new book is collected into the Codex.
 * The client decides whether to show the notification and/or play the sound
 * based on the client config values. All client-only code lives in
 * {@link LoreCodexClientHelper} (an {@code @OnlyIn(CLIENT)} class) so this
 * packet class stays safe to load on a dedicated server.
 */
public class ClientboundCodexCollectionEventPacket {

    private final String bookTitle;
    /** True when the absorbed book was a duplicate banked as a spare copy. */
    private final boolean duplicate;

    public ClientboundCodexCollectionEventPacket(String bookTitle, boolean duplicate) {
        this.bookTitle = bookTitle;
        this.duplicate = duplicate;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(bookTitle);
        buf.writeBoolean(duplicate);
    }

    public static ClientboundCodexCollectionEventPacket decode(FriendlyByteBuf buf) {
        return new ClientboundCodexCollectionEventPacket(buf.readUtf(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> LoreCodexClientHelper.showCollectionEvent(bookTitle, duplicate)));
        ctx.get().setPacketHandled(true);
    }
}
