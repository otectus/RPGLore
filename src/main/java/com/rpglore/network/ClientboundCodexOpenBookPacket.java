package com.rpglore.network;

import com.rpglore.codex.LoreCodexClientHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientboundCodexOpenBookPacket {

    private final ItemStack bookStack;

    public ClientboundCodexOpenBookPacket(ItemStack bookStack) {
        this.bookStack = bookStack;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(bookStack);
    }

    public static ClientboundCodexOpenBookPacket decode(FriendlyByteBuf buf) {
        return new ClientboundCodexOpenBookPacket(buf.readItem());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LoreCodexClientHelper.openBookFromCodex(bookStack);
        });
        ctx.get().setPacketHandled(true);
    }
}
