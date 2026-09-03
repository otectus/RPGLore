package com.rpglore.codex;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Client-side join of a {@link CodexCatalogEntry} with the player's own state for
 * that book. Built once per sync rather than per frame, and carries pre-lowercased
 * copies of the searchable fields so a later search/filter pass never lowercases in
 * the render loop.
 *
 * <p>Only the client builds these, but the type itself is common-safe on purpose:
 * {@link CodexEntryFilter} operates on it and is unit tested outside a client.
 */
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

    /** Display author, or "" when the server redacted it. */
    public String author() {
        return entry.author() == null ? "" : entry.author();
    }

    @Nullable
    public String category() {
        return entry.category();
    }

    @Nullable
    public String series() {
        return entry.series();
    }

    public int seriesOrder() {
        return entry.seriesOrder();
    }

    @Nullable
    public String discoveryHint() {
        return entry.discoveryHint();
    }

    /** True when the server withheld this entry's name; it must never match a search. */
    public boolean hidden() {
        return entry.hidden();
    }
}
