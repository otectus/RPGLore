package com.rpglore.codex;

import com.rpglore.lore.LoreBookScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
