package com.rpglore.codex;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Client-side join of a {@link CodexCatalogEntry} with the player's own state for
 * that book. Built once per sync rather than per frame, and carries pre-lowercased
 * copies of the searchable fields so a later search/filter pass never lowercases in
 * the render loop.
 *
 * <p>Client only: the server never builds these.
 */
@OnlyIn(Dist.CLIENT)
public record CodexEntryView(
        CodexCatalogEntry entry,
        boolean collected,
        boolean read,
        boolean favorite,
        int spares,
        long discoveredAt,
        String lowerTitle,
        String lowerAuthor,
        String lowerCategory,
        List<String> lowerTags
) {

    public static CodexEntryView of(CodexCatalogEntry entry, boolean collected, boolean read,
                                    boolean favorite, int spares, long discoveredAt) {
        return new CodexEntryView(
                entry, collected, read, favorite, spares, discoveredAt,
                lower(entry.title()),
                lower(entry.author()),
                lower(entry.category()),
                entry.tags().stream().map(CodexEntryView::lower).toList()
        );
    }

    private static String lower(@Nullable String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public String id() {
        return entry.id();
    }

    /** Display title, or "" when the server redacted it. */
    public String title() {
        return entry.title() == null ? "" : entry.title();
    }

    @Nullable
    public String titleColor() {
        return entry.titleColor();
    }
}
