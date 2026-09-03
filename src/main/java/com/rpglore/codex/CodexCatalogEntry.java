package com.rpglore.codex;

import com.rpglore.lore.LoreBookDefinition;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One book as it appears in the Codex catalog sent to a client. Common code only —
 * this must never reference the screen or any client class, because the catalog is
 * built on a dedicated server.
 *
 * <p>Uncollected books are sent in {@link #redacted} form when the server config
 * hides uncollected names, so a client cannot read titles it has not earned.
 *
 * <p>{@code tags}, {@code discoveryHint}, {@code series} and {@code seriesOrder} are
 * always empty/null/0 for now; the wire shape is fixed here so a later phase can
 * populate them without another protocol bump.
 */
public record CodexCatalogEntry(
        String id,
        @Nullable String title,
        @Nullable String author,
        @Nullable String category,
        @Nullable String titleColor,
        List<String> tags,
        @Nullable String discoveryHint,
        @Nullable String series,
        int seriesOrder,
        boolean hidden
) {

    /** Full form: everything the client may show about a book. */
    public static CodexCatalogEntry of(LoreBookDefinition def) {
        return new CodexCatalogEntry(
                def.id(),
                def.title(),
                def.author(),
                def.category(),
                def.titleColor(),
                List.of(),
                null,
                null,
                0,
                false
        );
    }

    /** Redacted form: the client learns a book exists, but not its title or author. */
    public static CodexCatalogEntry redacted(String id, @Nullable String category,
                                             @Nullable String discoveryHint, int seriesOrder) {
        return new CodexCatalogEntry(id, null, null, category, null,
                List.of(), discoveryHint, null, seriesOrder, true);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeNullable(title, FriendlyByteBuf::writeUtf);
        buf.writeNullable(author, FriendlyByteBuf::writeUtf);
        buf.writeNullable(category, FriendlyByteBuf::writeUtf);
        buf.writeNullable(titleColor, FriendlyByteBuf::writeUtf);
        buf.writeCollection(tags, FriendlyByteBuf::writeUtf);
        buf.writeNullable(discoveryHint, FriendlyByteBuf::writeUtf);
        buf.writeNullable(series, FriendlyByteBuf::writeUtf);
        buf.writeVarInt(seriesOrder);
        buf.writeBoolean(hidden);
    }

    public static CodexCatalogEntry read(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String title = buf.readNullable(FriendlyByteBuf::readUtf);
        String author = buf.readNullable(FriendlyByteBuf::readUtf);
        String category = buf.readNullable(FriendlyByteBuf::readUtf);
        String titleColor = buf.readNullable(FriendlyByteBuf::readUtf);
        List<String> tags = buf.readCollection(java.util.ArrayList::new, FriendlyByteBuf::readUtf);
        String discoveryHint = buf.readNullable(FriendlyByteBuf::readUtf);
        String series = buf.readNullable(FriendlyByteBuf::readUtf);
        int seriesOrder = buf.readVarInt();
        boolean hidden = buf.readBoolean();
        return new CodexCatalogEntry(id, title, author, category, titleColor,
                List.copyOf(tags), discoveryHint, series, seriesOrder, hidden);
    }
}
