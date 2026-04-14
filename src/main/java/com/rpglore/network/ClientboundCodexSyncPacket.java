package com.rpglore.network;

import com.rpglore.codex.CodexTrackingData;
import com.rpglore.codex.LoreCodexClientHelper;
import com.rpglore.codex.LoreCodexScreen;
import com.rpglore.config.LoreBookRegistry;
import com.rpglore.config.ServerConfig;
import com.rpglore.lore.LoreBookDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class ClientboundCodexSyncPacket {

    private final List<LoreCodexScreen.CodexBookEntry> catalog;
    private final boolean preventDuplicates;
    private final int collectedCount;
    private final int totalCount;
    private final boolean allowCopy;
    private final boolean allowDuplicatePrevention;
    private final boolean revealUncollectedNames;

    public ClientboundCodexSyncPacket(List<LoreCodexScreen.CodexBookEntry> catalog,
                                      boolean preventDuplicates, int collectedCount, int totalCount,
                                      boolean allowCopy, boolean allowDuplicatePrevention,
                                      boolean revealUncollectedNames) {
        this.catalog = catalog;
        this.preventDuplicates = preventDuplicates;
        this.collectedCount = collectedCount;
        this.totalCount = totalCount;
        this.allowCopy = allowCopy;
        this.allowDuplicatePrevention = allowDuplicatePrevention;
        this.revealUncollectedNames = revealUncollectedNames;
    }

    public static ClientboundCodexSyncPacket create(UUID playerUuid, CodexTrackingData data) {
        Set<String> collected = data.getCollectedBooks(playerUuid);
        Set<String> eligibleIds = LoreBookRegistry.getCodexEligibleIds();
        List<LoreCodexScreen.CodexBookEntry> catalog = new ArrayList<>();

        for (LoreBookDefinition def : LoreBookRegistry.getAllBooks()) {
            if (def.codexExclude()) continue;
            catalog.add(new LoreCodexScreen.CodexBookEntry(
                    def.id(),
                    def.title(),
                    def.author(),
                    collected.contains(def.id()),
                    def.titleColor(),
                    def.category()
            ));
        }

        // Sort: collected first, then alphabetical
        catalog.sort(Comparator.<LoreCodexScreen.CodexBookEntry, Boolean>comparing(e -> !e.collected())
                .thenComparing(LoreCodexScreen.CodexBookEntry::title));

        // Compute collected count as intersection with eligible IDs
        // (prevents collected > total when a book becomes excluded)
        int collectedEligible = 0;
        for (String id : collected) {
            if (eligibleIds.contains(id)) collectedEligible++;
        }

        return new ClientboundCodexSyncPacket(
                catalog,
                data.isPreventDuplicates(playerUuid),
                collectedEligible,
                eligibleIds.size(),
                ServerConfig.CODEX_ALLOW_COPY.get(),
                ServerConfig.CODEX_ALLOW_DUPLICATE_PREVENTION.get(),
                ServerConfig.CODEX_REVEAL_UNCOLLECTED_NAMES.get()
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(catalog.size());
        for (LoreCodexScreen.CodexBookEntry entry : catalog) {
            buf.writeUtf(entry.id());
            buf.writeUtf(entry.title());
            buf.writeBoolean(entry.collected());
            buf.writeBoolean(entry.titleColor() != null);
            if (entry.titleColor() != null) {
                buf.writeUtf(entry.titleColor());
            }
            // author and category are not transmitted (not used by the UI)
        }
        buf.writeBoolean(preventDuplicates);
        buf.writeVarInt(collectedCount);
        buf.writeVarInt(totalCount);
        buf.writeBoolean(allowCopy);
        buf.writeBoolean(allowDuplicatePrevention);
        buf.writeBoolean(revealUncollectedNames);
    }

    public static ClientboundCodexSyncPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<LoreCodexScreen.CodexBookEntry> catalog = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf();
            String title = buf.readUtf();
            boolean collected = buf.readBoolean();
            String titleColor = buf.readBoolean() ? buf.readUtf() : null;
            // author and category not transmitted; fill with defaults
            catalog.add(new LoreCodexScreen.CodexBookEntry(id, title, "", collected, titleColor, null));
        }
        boolean preventDuplicates = buf.readBoolean();
        int collectedCount = buf.readVarInt();
        int totalCount = buf.readVarInt();
        boolean allowCopy = buf.readBoolean();
        boolean allowDuplicatePrevention = buf.readBoolean();
        boolean revealUncollectedNames = buf.readBoolean();
        return new ClientboundCodexSyncPacket(catalog, preventDuplicates, collectedCount, totalCount,
                allowCopy, allowDuplicatePrevention, revealUncollectedNames);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            LoreCodexScreen.CodexScreenData screenData = new LoreCodexScreen.CodexScreenData(
                    catalog, preventDuplicates, collectedCount, totalCount,
                    allowCopy, allowDuplicatePrevention, revealUncollectedNames
            );
            LoreCodexClientHelper.updateCachedData(screenData);
        });
        ctx.get().setPacketHandled(true);
    }
}
