package com.rpglore.network;

import com.rpglore.codex.CodexService;
import com.rpglore.codex.LoreCodexItem;
import com.rpglore.config.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent when the player presses the open-Codex keybind. The server re-checks that the
 * player actually carries a Codex (inventory or a Curios slot) before opening
 * anything, so the keybind cannot be used to browse a collection without the item.
 */
public class ServerboundOpenCodexPacket {

    public ServerboundOpenCodexPacket() {}

    public void encode(FriendlyByteBuf buf) {}

    public static ServerboundOpenCodexPacket decode(FriendlyByteBuf buf) {
        return new ServerboundOpenCodexPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!ServerConfig.CODEX_ENABLED.get()) return;

            CodexService service = CodexService.get();
            if (service == null) return;

            // findCodex covers the inventory and, when Curios is present, its slots.
            // Codexes in lecterns or bookshelves are deliberately out of reach here.
            if (LoreCodexItem.findCodex(player).isEmpty()) {
                player.displayClientMessage(
                        Component.translatable("rpg_lore.codex.keybind.no_codex"), true);
                return;
            }

            service.resyncPlayer(player);
            ModNetwork.sendToPlayer(new ClientboundCodexOpenScreenPacket(), player);
        });
        ctx.get().setPacketHandled(true);
    }
}
