package com.rpglore.network;

import com.rpglore.codex.CodexService;
import com.rpglore.config.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Chooses what happens to a duplicate lore book the player walks over:
 * {@code storeAsSpare = true} absorbs it into the spare bank, {@code false} leaves it
 * on the ground. Idempotent set rather than a toggle, so the client and server cannot
 * end up out of phase.
 */
public class ServerboundCodexSetDuplicateModePacket {

    private final boolean storeAsSpare;

    public ServerboundCodexSetDuplicateModePacket(boolean storeAsSpare) {
        this.storeAsSpare = storeAsSpare;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(storeAsSpare);
    }

    public static ServerboundCodexSetDuplicateModePacket decode(FriendlyByteBuf buf) {
        return new ServerboundCodexSetDuplicateModePacket(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!ServerConfig.CODEX_ALLOW_DUPLICATE_PREVENTION.get()) return;

            CodexService service = CodexService.get();
            if (service == null) return;

            service.setDuplicateMode(player, storeAsSpare);
        });
        ctx.get().setPacketHandled(true);
    }
}
