package com.rpglore.codex;

import com.rpglore.config.ClientConfig;
import com.rpglore.lore.LoreBookScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Client-only helper for the Lore Codex.
 * Kept in a separate class so that LoreCodexItem can load on a dedicated server
 * without pulling in client-only classes.
 */
@OnlyIn(Dist.CLIENT)
public final class LoreCodexClientHelper {

    @Nullable
    private static LoreCodexScreen.CodexScreenData cachedData;

    public static void openCodexScreen() {
        if (cachedData != null) {
            Minecraft.getInstance().setScreen(new LoreCodexScreen(cachedData));
        } else {
            // No data yet; open with empty data, will refresh when sync arrives
            Minecraft.getInstance().setScreen(new LoreCodexScreen(LoreCodexScreen.CodexScreenData.empty()));
        }
    }

    public static void updateCachedData(LoreCodexScreen.CodexScreenData data) {
        cachedData = data;
        // If a LoreCodexScreen is currently open, refresh it
        if (Minecraft.getInstance().screen instanceof LoreCodexScreen screen) {
            screen.refreshData(data);
        }
    }

    /**
     * Shows the collection notification / plays the collection sound for a book
     * absorbed into the Codex, honoring the client display config.
     */
    public static void showCollectionEvent(String bookTitle, boolean duplicate) {
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
    }

    public static void openBookFromCodex(ItemStack bookStack) {
        Minecraft mc = Minecraft.getInstance();
        Screen parentScreen = mc.screen;
        mc.setScreen(new LoreBookScreen(bookStack) {
            @Override
            public void onClose() {
                mc.setScreen(parentScreen);
            }
        });
    }

    private LoreCodexClientHelper() {}
}
