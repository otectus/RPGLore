package com.rpglore.network;

import com.rpglore.codex.CodexPlayerState;
import com.rpglore.codex.LoreCodexClientHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The player's own Codex state (collected entries, read/favorite flags, spare counts
 * and the config flags the UI gates on). Sent on every mutation.
 */
public class ClientboundCodexPlayerStatePacket {

    private final CodexPlayerState state;

    public ClientboundCodexPlayerStatePacket(CodexPlayerState state) {
        this.state = state;
    }

    public void encode(FriendlyByteBuf buf) {
        state.write(buf);
    }

    public static ClientboundCodexPlayerStatePacket decode(FriendlyByteBuf buf) {
        return new ClientboundCodexPlayerStatePacket(CodexPlayerState.read(buf));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> LoreCodexClientHelper.updatePlayerState(state)));
        ctx.get().setPacketHandled(true);
    }
}
