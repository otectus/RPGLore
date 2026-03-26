package com.rpglore.network;

import com.rpglore.codex.CodexTrackingData;
import com.rpglore.config.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ServerboundCodexToggleDuplicatePacket {

    public ServerboundCodexToggleDuplicatePacket() {}

    public void encode(FriendlyByteBuf buf) {
        // empty
    }

    public static ServerboundCodexToggleDuplicatePacket decode(FriendlyByteBuf buf) {
        return new ServerboundCodexToggleDuplicatePacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!ServerConfig.CODEX_ALLOW_DUPLICATE_PREVENTION.get()) return;

            CodexTrackingData data = CodexTrackingData.getInstance();
            if (data == null) return;

            boolean current = data.isPreventDuplicates(player.getUUID());
            data.setPreventDuplicates(player.getUUID(), !current);

            // Send updated sync
            ClientboundCodexSyncPacket syncPacket = ClientboundCodexSyncPacket.create(player.getUUID(), data);
            ModNetwork.sendToPlayer(syncPacket, player);
        });
        ctx.get().setPacketHandled(true);
    }
}
