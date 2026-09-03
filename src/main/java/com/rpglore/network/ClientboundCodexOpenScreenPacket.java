package com.rpglore.network;

import com.rpglore.codex.LoreCodexClientHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the client to open the Codex screen. Empty payload: the catalog and player
 * state were resynced just before this packet, so the client already holds everything
 * the screen needs.
 */
public class ClientboundCodexOpenScreenPacket {

    public ClientboundCodexOpenScreenPacket() {}

    public void encode(FriendlyByteBuf buf) {}

    public static ClientboundCodexOpenScreenPacket decode(FriendlyByteBuf buf) {
        return new ClientboundCodexOpenScreenPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> LoreCodexClientHelper::openCodexScreen));
        ctx.get().setPacketHandled(true);
    }
}
