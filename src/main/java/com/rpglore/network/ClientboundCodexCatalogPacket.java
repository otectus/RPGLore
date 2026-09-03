package com.rpglore.network;

import com.rpglore.codex.CodexCatalogEntry;
import com.rpglore.codex.LoreCodexClientHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The book catalog as this player is allowed to see it. Only sent when the client's
 * cached revision is stale, or when the redaction of an entry changed for this player.
 */
public class ClientboundCodexCatalogPacket {

    private final int revision;
    private final List<CodexCatalogEntry> entries;

    public ClientboundCodexCatalogPacket(int revision, List<CodexCatalogEntry> entries) {
        this.revision = revision;
        this.entries = entries;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(revision);
        buf.writeCollection(entries, (b, e) -> e.write(b));
    }

    public static ClientboundCodexCatalogPacket decode(FriendlyByteBuf buf) {
        int revision = buf.readVarInt();
        List<CodexCatalogEntry> entries = buf.readCollection(ArrayList::new, CodexCatalogEntry::read);
        return new ClientboundCodexCatalogPacket(revision, List.copyOf(entries));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> LoreCodexClientHelper.updateCatalog(revision, entries)));
        ctx.get().setPacketHandled(true);
    }
}
