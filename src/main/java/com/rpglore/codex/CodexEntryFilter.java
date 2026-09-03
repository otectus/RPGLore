package com.rpglore.codex;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * The Codex list pipeline: filter, then category, then query, then sort. Pure
 * functions over {@link CodexEntryView} so the screen holds no logic worth testing
 * and the logic is testable without a client.
 *
 * <p>Hidden entries (uncollected books whose names the server withheld) never match a
 * non-empty query — matching them on a category or tag the player cannot see would
 * leak exactly what the redaction is meant to hide.
 */
public final class CodexEntryFilter {

    public static List<CodexEntryView> apply(List<CodexEntryView> entries, CodexViewState state) {
        String query = state.query() == null ? "" : state.query().trim().toLowerCase(Locale.ROOT);

        List<CodexEntryView> result = new ArrayList<>(entries.size());
        for (CodexEntryView entry : entries) {
            if (!matchesFilter(entry, state.filter())) continue;
            if (!matchesCategory(entry, state.category())) continue;
            if (!matchesQuery(entry, query)) continue;
            result.add(entry);
        }

        result.sort(comparator(state.sort()));
        return result;
    }

    /** The distinct non-blank categories present, sorted alphabetically. */
    public static List<String> categoriesOf(List<CodexEntryView> entries) {
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (CodexEntryView entry : entries) {
            String category = entry.category();
            if (category != null && !category.isBlank()) categories.add(category);
        }
        return List.copyOf(categories);
    }

    /** True when at least one entry has no category, i.e. the Uncategorized value is useful. */
    public static boolean hasUncategorized(List<CodexEntryView> entries) {
        for (CodexEntryView entry : entries) {
            String category = entry.category();
            if (category == null || category.isBlank()) return true;
        }
        return false;
    }

    private static boolean matchesFilter(CodexEntryView entry, CodexViewState.Filter filter) {
        return switch (filter) {
            case ALL -> true;
            case COLLECTED -> entry.collected();
            case UNREAD -> entry.collected() && !entry.read();
            case FAVORITES -> entry.favorite();
            case MISSING -> !entry.collected();
        };
    }

    private static boolean matchesCategory(CodexEntryView entry, @Nullable String category) {
        if (category == null) return true;
        String own = entry.category();
        boolean blank = own == null || own.isBlank();
        if (CodexViewState.UNCATEGORIZED.equals(category)) return blank;
        return !blank && own.equalsIgnoreCase(category);
    }

    private static boolean matchesQuery(CodexEntryView entry, String query) {
        if (query.isEmpty()) return true;
        if (entry.hidden()) return false;
        if (entry.lowerTitle().contains(query)) return true;
        if (entry.lowerAuthor().contains(query)) return true;
        if (entry.lowerCategory().contains(query)) return true;
        for (String tag : entry.lowerTags()) {
            if (tag.contains(query)) return true;
        }
        return false;
    }

    private static Comparator<CodexEntryView> comparator(CodexViewState.Sort sort) {
        Comparator<CodexEntryView> byName =
                Comparator.comparing(CodexEntryFilter::sortName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CodexEntryView::id);

        return switch (sort) {
            // Unread first, then read, then still missing — the order a collector reads in
            case DEFAULT -> Comparator.comparingInt(CodexEntryFilter::defaultGroup).thenComparing(byName);
            case TITLE -> Comparator.comparing((CodexEntryView v) -> Boolean.valueOf(v.hidden()))
                    .thenComparing(byName);
            // Blank categories sort after every named one
            case CATEGORY -> Comparator.comparing(
                            (CodexEntryView v) -> Boolean.valueOf(sortCategory(v).isEmpty()))
                    .thenComparing(CodexEntryFilter::sortCategory, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(byName);
            // Newest discovery first; never-collected books sink to the bottom
            case RECENT -> Comparator.comparingLong(CodexEntryFilter::discoveryOrder)
                    .reversed().thenComparing(byName);
        };
    }

    private static int defaultGroup(CodexEntryView entry) {
        if (!entry.collected()) return 2;
        return entry.read() ? 1 : 0;
    }

    /** Hidden entries have no title to sort on, so they sort by id instead. */
    private static String sortName(CodexEntryView entry) {
        return entry.hidden() || entry.title().isEmpty() ? entry.id() : entry.title();
    }

    /** Sort key for the category column; "" for entries with no category. */
    private static String sortCategory(CodexEntryView entry) {
        String category = entry.category();
        return category == null || category.isBlank() ? "" : category;
    }

    /**
     * Recency key: collected books use their discovery timestamp, everything else
     * uses {@link Long#MIN_VALUE} so it lands after every timestamped entry.
     */
    private static long discoveryOrder(CodexEntryView entry) {
        if (!entry.collected() || entry.discoveredAt() <= 0L) return Long.MIN_VALUE;
        return entry.discoveredAt();
    }

    private CodexEntryFilter() {}
}
