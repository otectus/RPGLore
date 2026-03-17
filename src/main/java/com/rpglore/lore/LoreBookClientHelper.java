package com.rpglore.lore;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-only helper for opening lore book screens.
 * Kept in a separate class so that LoreBookItem can load on a dedicated server
 * without pulling in client-only classes like Minecraft and BookViewScreen.
 */
@OnlyIn(Dist.CLIENT)
public final class LoreBookClientHelper {

    public static void openBookScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new LoreBookScreen(stack));
    }

    private LoreBookClientHelper() {}
}
