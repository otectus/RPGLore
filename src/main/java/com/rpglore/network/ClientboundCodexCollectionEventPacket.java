package com.rpglore.network;

import com.rpglore.config.ClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent from server to client when a new book is collected into the Codex.
 * The client decides whether to show the notification and/or play the sound
 * based on the client config values.
 */
public class ClientboundCodexCollectionEventPacket {

    private final String bookTitle;

    public ClientboundCodexCollectionEventPacket(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(bookTitle);
    }

    public static ClientboundCodexCollectionEventPacket decode(FriendlyByteBuf buf) {
        return new ClientboundCodexCollectionEventPacket(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (ClientConfig.CODEX_SHOW_NOTIFICATION.get()) {
                mc.player.displayClientMessage(
                        Component.translatable("rpg_lore.codex.collected",
                                Component.literal(bookTitle).withStyle(ChatFormatting.GOLD)),
                        true);
            }

            if (ClientConfig.CODEX_PLAY_SOUND.get()) {
                mc.player.level().playLocalSound(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                        0.5f, 1.2f, false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
