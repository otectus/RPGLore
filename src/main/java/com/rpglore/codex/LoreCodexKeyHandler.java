package com.rpglore.codex;

import com.mojang.blaze3d.platform.InputConstants;
import com.rpglore.network.ModNetwork;
import com.rpglore.network.ServerboundOpenCodexPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * The "open Codex" keybind. Unbound by default so it cannot collide with a vanilla
 * key on first install.
 *
 * <p>Client only, and reached exclusively through {@code DistExecutor}: a dedicated
 * server must never load this class.
 */
@OnlyIn(Dist.CLIENT)
public final class LoreCodexKeyHandler {

    public static final KeyMapping OPEN_CODEX = new KeyMapping(
            "key.rpg_lore.open_codex",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.rpg_lore");

    public static void register(IEventBus modBus) {
        modBus.addListener(LoreCodexKeyHandler::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(LoreCodexKeyHandler::onClientTick);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CODEX);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        // Only while actually playing: a screen of our own is already open, and the
        // server decides whether the player is carrying a Codex at all.
        if (mc.screen != null || mc.player == null) return;

        boolean pressed = false;
        while (OPEN_CODEX.consumeClick()) {
            pressed = true;
        }
        if (pressed) {
            ModNetwork.sendToServer(new ServerboundOpenCodexPacket());
        }
    }

    private LoreCodexKeyHandler() {}
}
