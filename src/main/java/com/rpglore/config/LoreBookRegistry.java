package com.rpglore.config;

import com.rpglore.data.LoreTrackingData;
import com.rpglore.lore.DropConditionContext;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.acquisition.AcquisitionRule;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Central registry for loaded lore book definitions.
 * Provides query methods and delegates per-player tracking to LoreTrackingData.
 *
 * <p>Every query answers from a single immutable {@link Index} snapshot that is built once
 * per {@link #setBooks(Map)} and swapped in atomically, so lookups on the hot paths never
 * scan the catalog or allocate.
 */
public final class LoreBookRegistry {

    @Nullable
    private static volatile LoreTrackingData trackingData;

    /** One book matched by one of its entity_drop rules. */
    public record MatchedDrop(LoreBookDefinition definition,
                              AcquisitionRule.EntityDropAcquisition rule) {}

    /** One book that injects into a loot table, with the rule that says how. */
    public record LootTableCandidate(LoreBookDefinition definition,
                                     AcquisitionRule.LootTableAcquisition rule) {}

    /** One book granted by an advancement, with the rule that says how it is delivered. */
    public record AdvancementCandidate(LoreBookDefinition definition,
                                       AcquisitionRule.AdvancementAcquisition rule) {}

    /**
     * An entity_drop candidate tagged with its position in a full catalog scan. The order
     * lets the exact-mob and generic buckets be merged back into exactly the sequence the
     * pre-index linear scan produced.
     */
    private record EntityCandidate(int order, MatchedDrop drop) {}

    /** Everything the registry can be asked, precomputed. Replaced wholesale, never mutated. */
    private record Index(Map<String, LoreBookDefinition> books,
                         Set<String> allBookIds,
                         Set<String> codexEligibleIds,
                         int codexEligibleCount,
                         List<String> categories,
                         Map<ResourceLocation, List<EntityCandidate>> byExactMob,
                         List<EntityCandidate> genericEntityRules,
                         Map<ResourceLocation, List<LootTableCandidate>> byLootTable,
                         Map<ResourceLocation, List<AdvancementCandidate>> byAdvancement,
                         int revision) {}

    private static volatile Index index = buildIndex(Map.of(), 1);

    public static void setBooks(Map<String, LoreBookDefinition> books) {
        Index current = index;
        Map<String, LoreBookDefinition> newBooks = Collections.unmodifiableMap(books);
        boolean changed = !newBooks.equals(current.books());
        // Clients cache the Codex catalog against the revision, so a reload that produced
        // identical books must not invalidate it.
        index = buildIndex(newBooks, changed ? current.revision() + 1 : current.revision());
    }

    private static Index buildIndex(Map<String, LoreBookDefinition> books, int revision) {
        Set<String> codexEligibleIds = new HashSet<>();
        Set<String> categories = new TreeSet<>();
        Map<ResourceLocation, List<EntityCandidate>> byExactMob = new HashMap<>();
        List<EntityCandidate> generic = new ArrayList<>();
        Map<ResourceLocation, List<LootTableCandidate>> byLootTable = new HashMap<>();
        Map<ResourceLocation, List<AdvancementCandidate>> byAdvancement = new HashMap<>();

        int order = 0;
        for (LoreBookDefinition def : books.values()) {
            if (!def.codexExclude()) codexEligibleIds.add(def.id());
            if (def.category() != null && !def.category().isBlank()) categories.add(def.category());

            for (AcquisitionRule rule : def.acquisition()) {
                if (rule instanceof AcquisitionRule.EntityDropAcquisition drop) {
                    EntityCandidate candidate = new EntityCandidate(order++, new MatchedDrop(def, drop));
                    List<ResourceLocation> mobTypes = drop.condition().mobTypes();
                    if (mobTypes == null || mobTypes.isEmpty()) {
                        // Tag-only, biome-only or unconditional: a candidate for every mob.
                        generic.add(candidate);
                    } else {
                        for (ResourceLocation mob : mobTypes) {
                            List<EntityCandidate> bucket =
                                    byExactMob.computeIfAbsent(mob, key -> new ArrayList<>());
                            // A rule may list the same mob twice; it is still one candidate.
                            if (bucket.isEmpty() || bucket.get(bucket.size() - 1) != candidate) {
                                bucket.add(candidate);
                            }
                        }
                    }
                } else if (rule instanceof AcquisitionRule.LootTableAcquisition loot) {
                    for (ResourceLocation table : loot.lootTables()) {
                        byLootTable.computeIfAbsent(table, key -> new ArrayList<>())
                                .add(new LootTableCandidate(def, loot));
                    }
                } else if (rule instanceof AcquisitionRule.AdvancementAcquisition advancement) {
                    for (ResourceLocation id : advancement.advancements()) {
                        byAdvancement.computeIfAbsent(id, key -> new ArrayList<>())
                                .add(new AdvancementCandidate(def, advancement));
                    }
                }
            }
        }

        return new Index(
                books,
                Set.copyOf(books.keySet()),
                Set.copyOf(codexEligibleIds),
                codexEligibleIds.size(),
                List.copyOf(categories),
                freeze(byExactMob),
                List.copyOf(generic),
                freeze(byLootTable),
                freeze(byAdvancement),
                revision);
    }

    private static <K, V> Map<K, List<V>> freeze(Map<K, List<V>> map) {
        Map<K, List<V>> frozen = new HashMap<>(map.size());
        map.forEach((key, values) -> frozen.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(frozen);
    }

    /** @return the current catalog revision; increments only on a real content change. */
    public static int getRevision() {
        return index.revision();
    }

    public static void setTrackingData(@Nullable LoreTrackingData data) {
        trackingData = data;
    }

    @Nullable
    public static LoreTrackingData getTrackingData() {
        return trackingData;
    }

    // --- Query methods ---

    /**
     * The entity_drop rules that could possibly apply to this mob: the ones naming it
     * explicitly plus the ones with no mob_types filter, merged back into catalog order.
     * This is a prefilter only — the caller still has to run the full condition check.
     */
    public static List<MatchedDrop> candidatesFor(@Nullable ResourceLocation mobId) {
        Index snapshot = index;
        List<EntityCandidate> exact = mobId == null
                ? List.of()
                : snapshot.byExactMob().getOrDefault(mobId, List.of());
        List<EntityCandidate> generic = snapshot.genericEntityRules();
        if (exact.isEmpty() && generic.isEmpty()) return List.of();

        // Both buckets are built in ascending catalog order, so a merge restores the exact
        // sequence a full scan would have visited.
        List<MatchedDrop> merged = new ArrayList<>(exact.size() + generic.size());
        int i = 0, j = 0;
        while (i < exact.size() && j < generic.size()) {
            merged.add(exact.get(i).order() < generic.get(j).order()
                    ? exact.get(i++).drop()
                    : generic.get(j++).drop());
        }
        while (i < exact.size()) merged.add(exact.get(i++).drop());
        while (j < generic.size()) merged.add(generic.get(j++).drop());
        return merged;
    }

    /**
     * Books whose entity_drop rules match this kill. A book with several entity_drop
     * rules is returned at most once: the first rule that matches wins.
     */
    public static List<MatchedDrop> getMatchingBooks(DropConditionContext ctx) {
        List<MatchedDrop> matching = new ArrayList<>();
        String lastMatchedId = null;
        for (MatchedDrop candidate : candidatesFor(ctx.victimType())) {
            if (candidate.definition().id().equals(lastMatchedId)) continue;
            if (ctx.matches(candidate.rule().condition())) {
                matching.add(candidate);
                lastMatchedId = candidate.definition().id();
            }
        }
        return matching;
    }

    /** @return the books that inject into this loot table, or an empty list */
    public static List<LootTableCandidate> getLootTableRules(ResourceLocation tableId) {
        return index.byLootTable().getOrDefault(tableId, List.of());
    }

    /** @return the books granted by this advancement, or an empty list */
    public static List<AdvancementCandidate> getAdvancementRules(ResourceLocation advancementId) {
        return index.byAdvancement().getOrDefault(advancementId, List.of());
    }

    public static Collection<LoreBookDefinition> getAllBooks() {
        return index.books().values();
    }

    public static Optional<LoreBookDefinition> getById(String id) {
        return Optional.ofNullable(index.books().get(id));
    }

    public static int getBookCount() {
        return index.books().size();
    }

    public static Set<String> getAllBookIds() {
        return index.allBookIds();
    }

    /**
     * Returns the count of books that are not excluded from the Codex.
     */
    public static int getCodexEligibleCount() {
        return index.codexEligibleCount();
    }

    /**
     * Returns the set of book IDs that are eligible for Codex inclusion (not excluded).
     */
    public static Set<String> getCodexEligibleIds() {
        return index.codexEligibleIds();
    }

    /** @return the distinct non-blank categories in the catalog, sorted */
    public static List<String> getCategories() {
        return index.categories();
    }

    // --- Per-player copy tracking (delegated to LoreTrackingData) ---

    public static boolean canPlayerReceive(UUID playerUuid, String bookId, int maxCopies) {
        LoreTrackingData data = trackingData;
        if (data == null) return true;
        return data.canPlayerReceive(playerUuid, bookId, maxCopies);
    }

    public static void recordPlayerReceived(UUID playerUuid, String bookId) {
        LoreTrackingData data = trackingData;
        if (data != null) {
            data.recordPlayerReceived(playerUuid, bookId);
        }
    }

    private LoreBookRegistry() {}
}
