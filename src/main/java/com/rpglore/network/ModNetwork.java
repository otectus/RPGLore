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
    private static final String PROTOCOL_VERSION = "2";

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(RpgLoreMod.MODID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        int id = 0;

        INSTANCE.messageBuilder(ClientboundCodexSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundCodexSyncPacket::encode)
                .decoder(ClientboundCodexSyncPacket::decode)
                .consumerMainThread(ClientboundCodexSyncPacket::handle)
                .add();

        INSTANCE.messageBuilder(ServerboundCodexToggleDuplicatePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundCodexToggleDuplicatePacket::encode)
                .decoder(ServerboundCodexToggleDuplicatePacket::decode)
                .consumerMainThread(ServerboundCodexToggleDuplicatePacket::handle)
                .add();

        INSTANCE.messageBuilder(ServerboundCodexCopyBookPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundCodexCopyBookPacket::encode)
                .decoder(ServerboundCodexCopyBookPacket::decode)
                .consumerMainThread(ServerboundCodexCopyBookPacket::handle)
                .add();

        INSTANCE.messageBuilder(ServerboundCodexOpenBookPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundCodexOpenBookPacket::encode)
                .decoder(ServerboundCodexOpenBookPacket::decode)
                .consumerMainThread(ServerboundCodexOpenBookPacket::handle)
                .add();

        INSTANCE.messageBuilder(ClientboundCodexOpenBookPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundCodexOpenBookPacket::encode)
                .decoder(ClientboundCodexOpenBookPacket::decode)
                .consumerMainThread(ClientboundCodexOpenBookPacket::handle)
                .add();

        INSTANCE.messageBuilder(ClientboundCodexCollectionEventPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundCodexCollectionEventPacket::encode)
                .decoder(ClientboundCodexCollectionEventPacket::decode)
                .consumerMainThread(ClientboundCodexCollectionEventPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG msg) {
        INSTANCE.sendToServer(msg);
    }

    public static <MSG> void sendToPlayer(MSG msg, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }
}
