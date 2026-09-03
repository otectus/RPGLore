package com.rpglore.network;

import com.rpglore.RpgLoreMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static SimpleChannel INSTANCE;
    private static final String PROTOCOL_VERSION = "3";

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(RpgLoreMod.MODID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        // Packet ids are fixed and must not be reshuffled without bumping
        // PROTOCOL_VERSION: 0-4 clientbound, 5-9 serverbound.

        INSTANCE.messageBuilder(ClientboundCodexCatalogPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundCodexCatalogPacket::encode)
                .decoder(ClientboundCodexCatalogPacket::decode)
                .consumerMainThread(ClientboundCodexCatalogPacket::handle)
                .add();

        INSTANCE.messageBuilder(ClientboundCodexPlayerStatePacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundCodexPlayerStatePacket::encode)
                .decoder(ClientboundCodexPlayerStatePacket::decode)
                .consumerMainThread(ClientboundCodexPlayerStatePacket::handle)
                .add();

        INSTANCE.messageBuilder(ClientboundCodexOpenBookPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundCodexOpenBookPacket::encode)
                .decoder(ClientboundCodexOpenBookPacket::decode)
                .consumerMainThread(ClientboundCodexOpenBookPacket::handle)
                .add();

        INSTANCE.messageBuilder(ClientboundCodexCollectionEventPacket.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundCodexCollectionEventPacket::encode)
                .decoder(ClientboundCodexCollectionEventPacket::decode)
                .consumerMainThread(ClientboundCodexCollectionEventPacket::handle)
                .add();

        INSTANCE.messageBuilder(ClientboundCodexOpenScreenPacket.class, 4, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundCodexOpenScreenPacket::encode)
                .decoder(ClientboundCodexOpenScreenPacket::decode)
                .consumerMainThread(ClientboundCodexOpenScreenPacket::handle)
                .add();

        INSTANCE.messageBuilder(ServerboundCodexOpenBookPacket.class, 5, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundCodexOpenBookPacket::encode)
                .decoder(ServerboundCodexOpenBookPacket::decode)
                .consumerMainThread(ServerboundCodexOpenBookPacket::handle)
                .add();

        INSTANCE.messageBuilder(ServerboundCodexCopyBookPacket.class, 6, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundCodexCopyBookPacket::encode)
                .decoder(ServerboundCodexCopyBookPacket::decode)
                .consumerMainThread(ServerboundCodexCopyBookPacket::handle)
                .add();

        INSTANCE.messageBuilder(ServerboundCodexSetFavoritePacket.class, 7, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundCodexSetFavoritePacket::encode)
                .decoder(ServerboundCodexSetFavoritePacket::decode)
                .consumerMainThread(ServerboundCodexSetFavoritePacket::handle)
                .add();

        INSTANCE.messageBuilder(ServerboundCodexSetDuplicateModePacket.class, 8, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundCodexSetDuplicateModePacket::encode)
                .decoder(ServerboundCodexSetDuplicateModePacket::decode)
                .consumerMainThread(ServerboundCodexSetDuplicateModePacket::handle)
                .add();

        INSTANCE.messageBuilder(ServerboundOpenCodexPacket.class, 9, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundOpenCodexPacket::encode)
                .decoder(ServerboundOpenCodexPacket::decode)
                .consumerMainThread(ServerboundOpenCodexPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG msg) {
        INSTANCE.sendToServer(msg);
    }

    public static <MSG> void sendToPlayer(MSG msg, ServerPlayer player) {
        // GameTest mock players are placed in the player list with a channel-less
        // Connection; Forge's distributor reads a channel attribute and would NPE.
        if (player.connection == null || !player.connection.connection.isConnected()) return;
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
