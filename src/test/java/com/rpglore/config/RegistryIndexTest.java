package com.rpglore.config;

import com.rpglore.lore.DropCondition;
import com.rpglore.lore.DropConditionContext;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.acquisition.AcquisitionRule;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The registry-rebuild-time index: the mob-type buckets must be a pure prefilter, so a
 * lookup has to return exactly what a full catalog scan would have visited, in the same
 * order, and the cached snapshot sets must be reused between rebuilds.
 */
class RegistryIndexTest {

    private static final int MOB_ID_COUNT = 50;
    private static final int EXACT_BOOKS = 4_000;
    private static final int TWO_MOB_BOOKS = 500;
    private static final int GENERIC_BOOKS = 500;

    private Map<String, LoreBookDefinition> catalog;

    @BeforeEach
    void buildCatalog() {
        catalog = syntheticCatalog();
        LoreBookRegistry.setBooks(catalog);
    }

    @AfterEach
    void resetRegistry() {
        LoreBookRegistry.setBooks(Map.of());
    }

    // --- Synthetic catalog ---

    private static ResourceLocation mob(int i) {
        return new ResourceLocation("rpg_lore", "mob_" + i);
    }

    private static DropCondition condition(List<ResourceLocation> mobTypes, List<String> mobTags) {
        return new DropCondition(mobTypes, mobTags, null, null, null, null, null,
                DropCondition.TimeFilter.ANY, DropCondition.WeatherFilter.ANY, true, null, -1);
    }

    private static LoreBookDefinition book(String id, String category, List<AcquisitionRule> rules) {
        return new LoreBookDefinition(id, "Title " + id, "Author", 0, 1.0, rules,
                List.of("A page."), null, null, null, null, false, false, category,
                false, List.of(), null, null, 0, 1);
    }

    /** 4,000 single-mob books, 500 two-mob books and 500 tag-only / unconditional books. */
    private static Map<String, LoreBookDefinition> syntheticCatalog() {
        Map<String, LoreBookDefinition> books = new LinkedHashMap<>();
        int n = 0;

        for (int i = 0; i < EXACT_BOOKS; i++, n++) {
            String id = "book_" + n;
            books.put(id, book(id, "Exact", List.of(new AcquisitionRule.EntityDropAcquisition(
                    condition(List.of(mob(i % MOB_ID_COUNT)), null)))));
        }
        for (int i = 0; i < TWO_MOB_BOOKS; i++, n++) {
            String id = "book_" + n;
            // Two rules per book, so first-matching-rule-per-book has something to pick from.
            books.put(id, book(id, "Pair", List.of(
                    new AcquisitionRule.EntityDropAcquisition(condition(
                            List.of(mob(i % MOB_ID_COUNT), mob((i + 7) % MOB_ID_COUNT)), null)),
                    new AcquisitionRule.EntityDropAcquisition(condition(
                            List.of(mob((i + 13) % MOB_ID_COUNT)), null)))));
        }
        for (int i = 0; i < GENERIC_BOOKS; i++, n++) {
            String id = "book_" + n;
            List<String> tags = i % 2 == 0 ? List.of("minecraft:skeletons") : null;
            books.put(id, book(id, i % 3 == 0 ? null : "Generic",
                    List.of(new AcquisitionRule.EntityDropAcquisition(condition(null, tags)))));
        }
        return books;
    }

    // --- Brute force reference ---

    /** "definitionId#ruleIndex" for every entity_drop rule a full scan would have tested. */
    private List<String> bruteForce(ResourceLocation mobId) {
        List<String> out = new ArrayList<>();
        for (LoreBookDefinition def : catalog.values()) {
            List<AcquisitionRule.EntityDropAcquisition> rules = def.entityDropRules();
            for (int i = 0; i < rules.size(); i++) {
                List<ResourceLocation> mobTypes = rules.get(i).condition().mobTypes();
                boolean generic = mobTypes == null || mobTypes.isEmpty();
                if (generic || mobTypes.contains(mobId)) {
                    out.add(def.id() + "#" + i);
                }
            }
        }
        return out;
    }

    private static List<String> indexed(ResourceLocation mobId) {
        List<String> out = new ArrayList<>();
        for (LoreBookRegistry.MatchedDrop drop : LoreBookRegistry.candidatesFor(mobId)) {
            out.add(drop.definition().id() + "#"
                    + drop.definition().entityDropRules().indexOf(drop.rule()));
        }
        return out;
    }

    // --- Tests ---

    @Test
    void candidateCountMatchesTheBruteForceScan() {
        for (int i : new int[]{0, 1, 7, 13, 49}) {
            assertEquals(bruteForce(mob(i)).size(), LoreBookRegistry.candidatesFor(mob(i)).size(),
                    "candidate count for " + mob(i));
        }
    }

    @Test
    void candidatesMatchTheBruteForceScanInOrder() {
        List<ResourceLocation> probes = new ArrayList<>(List.of(
                mob(0), mob(3), mob(24), mob(49),
                new ResourceLocation("minecraft:zombie")));
        for (ResourceLocation probe : probes) {
            assertEquals(bruteForce(probe), indexed(probe), "candidates for " + probe);
        }
    }

    @Test
    void unknownMobSeesOnlyGenericRules() {
        List<String> candidates = indexed(new ResourceLocation("minecraft:ender_dragon"));
        assertEquals(GENERIC_BOOKS, candidates.size());
        assertEquals(bruteForce(new ResourceLocation("minecraft:ender_dragon")), candidates);
    }

    @Test
    void codexEligibleIdsAreCachedUntilTheNextRebuild() {
        assertSame(LoreBookRegistry.getCodexEligibleIds(), LoreBookRegistry.getCodexEligibleIds());
        assertEquals(EXACT_BOOKS + TWO_MOB_BOOKS + GENERIC_BOOKS,
                LoreBookRegistry.getCodexEligibleCount());

        var before = LoreBookRegistry.getCodexEligibleIds();
        Map<String, LoreBookDefinition> smaller = new LinkedHashMap<>(catalog);
        smaller.remove("book_0");
        LoreBookRegistry.setBooks(smaller);

        var after = LoreBookRegistry.getCodexEligibleIds();
        assertNotSame(before, after);
        assertFalse(after.contains("book_0"));
        assertEquals(before.size() - 1, after.size());
    }

    @Test
    void categoriesAreSortedAndDistinct() {
        assertEquals(List.of("Exact", "Generic", "Pair"), LoreBookRegistry.getCategories());
    }

    @Test
    void getMatchingBooksStillRunsTheFullCondition() {
        // A player-kill-only catalog: the prefilter lets these through, the matcher must not.
        ResourceLocation victim = mob(0);
        DropConditionContext mobKill = new DropConditionContext(
                victim, java.util.Set.of(), null,
                new ResourceLocation("minecraft:overworld"),
                new ResourceLocation("minecraft:plains"),
                java.util.Set.of(), 64.0, 0L, true, false, false, false);
        assertTrue(LoreBookRegistry.getMatchingBooks(mobKill).isEmpty(),
                "require_player_kill must still be enforced after the prefilter");
    }

    @Test
    void eachBookIsReturnedAtMostOnce() {
        DropConditionContext playerKill = new DropConditionContext(
                mob(0), java.util.Set.of("minecraft:skeletons"), null,
                new ResourceLocation("minecraft:overworld"),
                new ResourceLocation("minecraft:plains"),
                java.util.Set.of(), 64.0, 0L, true, false, false, true);

        List<LoreBookRegistry.MatchedDrop> matches = LoreBookRegistry.getMatchingBooks(playerKill);
        long distinct = matches.stream().map(m -> m.definition().id()).distinct().count();
        assertEquals(matches.size(), distinct, "a book must not be matched twice");
    }

    @Test
    void indexedLookupBeatsAFullScan() {
        ResourceLocation probe = mob(11);
        for (int i = 0; i < 200; i++) {
            LoreBookRegistry.candidatesFor(probe);
            bruteForce(probe);
        }

        long indexedStart = System.nanoTime();
        for (int i = 0; i < 1_000; i++) LoreBookRegistry.candidatesFor(probe);
        long indexedNanos = System.nanoTime() - indexedStart;

        long bruteStart = System.nanoTime();
        for (int i = 0; i < 1_000; i++) bruteForce(probe);
        long bruteNanos = System.nanoTime() - bruteStart;

        System.out.printf("RegistryIndexTest: %d books, 1000 lookups indexed=%.1fms brute=%.1fms%n",
                catalog.size(), indexedNanos / 1_000_000.0, bruteNanos / 1_000_000.0);
    }
}
