package com.rpglore.codex;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Codex list pipeline: filter, category, query, sort. Runs without a client,
 * which is why {@link CodexEntryFilter} holds the logic instead of the screen.
 */
class CodexFilterSortTest {

    private static CodexEntryView entry(String id, String title, String author, String category,
                                        List<String> tags, boolean collected, boolean read,
                                        boolean favorite, int spares, long discoveredAt) {
        CodexCatalogEntry catalog = new CodexCatalogEntry(
                id, title, author, category, null, tags, null, null, 0, false);
        return CodexEntryView.of(catalog, collected, read, favorite, spares, discoveredAt);
    }

    private static CodexEntryView hidden(String id, String category) {
        return CodexEntryView.of(CodexCatalogEntry.redacted(id, category, null, 0),
                false, false, false, 0, 0L);
    }

    // Alpha: collected, unread, favorite, History. Beta: collected+read, History.
    // Gamma: collected+read, no category. Delta: uncollected, Myths. Epsilon: hidden, History.
    private static final CodexEntryView ALPHA =
            entry("alpha", "Alpha Tales", "Mara", "History", List.of("war", "kings"),
                    true, false, true, 2, 300L);
    private static final CodexEntryView BETA =
            entry("beta", "Beta Chronicle", "Orin", "History", List.of("kings"),
                    true, true, false, 0, 100L);
    private static final CodexEntryView GAMMA =
            entry("gamma", "Gamma Notes", "Mara", null, List.of(),
                    true, true, false, 1, 200L);
    private static final CodexEntryView DELTA =
            entry("delta", "Delta Songs", "Ivo", "Myths", List.of("song"),
                    false, false, false, 0, 0L);
    private static final CodexEntryView EPSILON = hidden("epsilon", "History");

    private static final List<CodexEntryView> ALL = List.of(ALPHA, BETA, GAMMA, DELTA, EPSILON);

    private static List<String> ids(List<CodexEntryView> entries) {
        return entries.stream().map(CodexEntryView::id).toList();
    }

    private static List<String> run(CodexViewState state) {
        return ids(CodexEntryFilter.apply(ALL, state));
    }

    // --- Filters ---

    @Test
    void filterAllKeepsEverything() {
        assertEquals(5, run(CodexViewState.DEFAULT).size());
    }

    @Test
    void filterCollected() {
        assertEquals(List.of("alpha", "beta", "gamma"),
                sorted(run(CodexViewState.DEFAULT.withFilter(CodexViewState.Filter.COLLECTED))));
    }

    @Test
    void filterUnreadIsCollectedButNotRead() {
        assertEquals(List.of("alpha"),
                run(CodexViewState.DEFAULT.withFilter(CodexViewState.Filter.UNREAD)));
    }

    @Test
    void filterFavorites() {
        assertEquals(List.of("alpha"),
                run(CodexViewState.DEFAULT.withFilter(CodexViewState.Filter.FAVORITES)));
    }

    @Test
    void filterMissingKeepsUncollectedAndHidden() {
        assertEquals(List.of("delta", "epsilon"),
                sorted(run(CodexViewState.DEFAULT.withFilter(CodexViewState.Filter.MISSING))));
    }

    // --- Categories ---

    @Test
    void categoryFiltersOnExactCategory() {
        assertEquals(List.of("alpha", "beta", "epsilon"),
                sorted(run(CodexViewState.DEFAULT.withCategory("History"))));
    }

    @Test
    void categoryIsCaseInsensitive() {
        assertEquals(List.of("alpha", "beta", "epsilon"),
                sorted(run(CodexViewState.DEFAULT.withCategory("history"))));
    }

    @Test
    void uncategorizedMatchesEntriesWithoutOne() {
        assertEquals(List.of("gamma"),
                run(CodexViewState.DEFAULT.withCategory(CodexViewState.UNCATEGORIZED)));
    }

    @Test
    void categoryListingSkipsBlanksAndSorts() {
        assertEquals(List.of("History", "Myths"), CodexEntryFilter.categoriesOf(ALL));
        assertTrue(CodexEntryFilter.hasUncategorized(ALL));
        assertFalse(CodexEntryFilter.hasUncategorized(List.of(ALPHA, DELTA)));
    }

    // --- Query ---

    @Test
    void emptyQueryPassesEverything() {
        assertEquals(5, run(CodexViewState.DEFAULT.withQuery("   ")).size());
    }

    @Test
    void queryMatchesTitle() {
        assertEquals(List.of("beta"), run(CodexViewState.DEFAULT.withQuery("chronicle")));
    }

    @Test
    void queryMatchesAuthor() {
        assertEquals(List.of("alpha", "gamma"), sorted(run(CodexViewState.DEFAULT.withQuery("mara"))));
    }

    @Test
    void queryMatchesCategory() {
        assertEquals(List.of("delta"), run(CodexViewState.DEFAULT.withQuery("myth")));
    }

    @Test
    void queryMatchesTag() {
        assertEquals(List.of("alpha", "beta"), sorted(run(CodexViewState.DEFAULT.withQuery("kings"))));
    }

    @Test
    void queryIsCaseInsensitiveAndTrimmed() {
        assertEquals(List.of("alpha"), run(CodexViewState.DEFAULT.withQuery("  ALPHA Tales ")));
    }

    @Test
    void hiddenEntriesNeverMatchANonEmptyQuery() {
        // epsilon's category is History, which the query matches, but its name is withheld
        assertFalse(run(CodexViewState.DEFAULT.withQuery("history")).contains("epsilon"));
        assertFalse(run(CodexViewState.DEFAULT.withQuery("epsilon")).contains("epsilon"));
        assertTrue(run(CodexViewState.DEFAULT.withQuery("")).contains("epsilon"));
    }

    // --- Sorts ---

    @Test
    void defaultSortGroupsUnreadThenReadThenMissing() {
        assertEquals(List.of("alpha", "beta", "gamma", "delta", "epsilon"),
                run(CodexViewState.DEFAULT));
    }

    @Test
    void titleSortIsAlphabeticalWithHiddenLast() {
        assertEquals(List.of("alpha", "beta", "delta", "gamma", "epsilon"),
                run(CodexViewState.DEFAULT.withSort(CodexViewState.Sort.TITLE)));
    }

    @Test
    void categorySortOrdersByCategoryThenTitle() {
        assertEquals(List.of("alpha", "beta", "epsilon", "delta", "gamma"),
                run(CodexViewState.DEFAULT.withSort(CodexViewState.Sort.CATEGORY)));
    }

    @Test
    void recentSortIsNewestFirstWithZerosLast() {
        assertEquals(List.of("alpha", "gamma", "beta", "delta", "epsilon"),
                run(CodexViewState.DEFAULT.withSort(CodexViewState.Sort.RECENT)));
    }

    private static List<String> sorted(List<String> ids) {
        return ids.stream().sorted().toList();
    }
}
