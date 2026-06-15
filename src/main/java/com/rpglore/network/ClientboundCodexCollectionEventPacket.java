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
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (ClientConfig.CODEX_SHOW_NOTIFICATION.get()) {
                String key = duplicate ? "rpg_lore.codex.duplicate_stored" : "rpg_lore.codex.collected";
                mc.player.displayClientMessage(
                        Component.translatable(key,
                                Component.literal(bookTitle).withStyle(ChatFormatting.GOLD)),
                        true);
            }

            if (ClientConfig.CODEX_PLAY_SOUND.get()) {
                // Duplicates use a lower pitch to distinguish a banked spare from a new master
                float pitch = duplicate ? 0.8f : 1.2f;
                mc.player.level().playLocalSound(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                        0.5f, pitch, false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
