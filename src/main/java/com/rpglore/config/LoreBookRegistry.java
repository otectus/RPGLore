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
 */
public final class LoreBookRegistry {

    private static volatile Map<String, LoreBookDefinition> BOOKS = Map.of();

    /**
     * Bumped whenever the loaded book set actually changes. Clients cache the Codex
     * catalog against this, so a reload that produced identical books must not
     * invalidate it.
     */
    private static volatile int revision = 1;

    /** Loot table id -> the candidates that inject into it. Rebuilt on every setBooks. */
    private static volatile Map<ResourceLocation, List<LootTableCandidate>> LOOT_TABLE_INDEX = Map.of();

    /** Advancement id -> the candidates it grants. Rebuilt on every setBooks. */
    private static volatile Map<ResourceLocation, List<AdvancementCandidate>> ADVANCEMENT_INDEX = Map.of();

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

    public static void setBooks(Map<String, LoreBookDefinition> books) {
        Map<String, LoreBookDefinition> newBooks = Collections.unmodifiableMap(books);
        boolean changed = !newBooks.equals(BOOKS);
        BOOKS = newBooks;
        LOOT_TABLE_INDEX = buildLootTableIndex(newBooks);
        ADVANCEMENT_INDEX = buildAdvancementIndex(newBooks);
        if (changed) revision++;
    }

    private static Map<ResourceLocation, List<LootTableCandidate>> buildLootTableIndex(
            Map<String, LoreBookDefinition> books) {
        Map<ResourceLocation, List<LootTableCandidate>> index = new HashMap<>();
        for (LoreBookDefinition def : books.values()) {
            for (AcquisitionRule.LootTableAcquisition rule : def.lootTableRules()) {
                for (ResourceLocation table : rule.lootTables()) {
                    index.computeIfAbsent(table, key -> new ArrayList<>())
                            .add(new LootTableCandidate(def, rule));
                }
            }
        }
        return freeze(index);
    }

    private static Map<ResourceLocation, List<AdvancementCandidate>> buildAdvancementIndex(
            Map<String, LoreBookDefinition> books) {
        Map<ResourceLocation, List<AdvancementCandidate>> index = new HashMap<>();
        for (LoreBookDefinition def : books.values()) {
            for (AcquisitionRule.AdvancementAcquisition rule : def.advancementRules()) {
                for (ResourceLocation advancement : rule.advancements()) {
                    index.computeIfAbsent(advancement, key -> new ArrayList<>())
                            .add(new AdvancementCandidate(def, rule));
                }
            }
        }
        return freeze(index);
    }

    private static <K, V> Map<K, List<V>> freeze(Map<K, List<V>> index) {
        Map<K, List<V>> frozen = new HashMap<>(index.size());
        index.forEach((key, values) -> frozen.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(frozen);
    }

    /** @return the current catalog revision; increments only on a real content change. */
    public static int getRevision() {
        return revision;
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
     * Books whose entity_drop rules match this kill. A book with several entity_drop
     * rules is returned at most once: the first rule that matches wins.
     */
    public static List<MatchedDrop> getMatchingBooks(DropConditionContext ctx) {
        List<MatchedDrop> matching = new ArrayList<>();
        for (LoreBookDefinition def : BOOKS.values()) {
            for (AcquisitionRule.EntityDropAcquisition rule : def.entityDropRules()) {
                if (ctx.matches(rule.condition())) {
                    matching.add(new MatchedDrop(def, rule));
                    break;
                }
            }
        }
        return matching;
    }

    /** @return the books that inject into this loot table, or an empty list */
    public static List<LootTableCandidate> getLootTableRules(ResourceLocation tableId) {
        return LOOT_TABLE_INDEX.getOrDefault(tableId, List.of());
    }

    /** @return the books granted by this advancement, or an empty list */
    public static List<AdvancementCandidate> getAdvancementRules(ResourceLocation advancementId) {
        return ADVANCEMENT_INDEX.getOrDefault(advancementId, List.of());
    }

    public static Collection<LoreBookDefinition> getAllBooks() {
        return BOOKS.values();
    }

    public static Optional<LoreBookDefinition> getById(String id) {
        return Optional.ofNullable(BOOKS.get(id));
    }

    public static int getBookCount() {
        return BOOKS.size();
    }

    public static Set<String> getAllBookIds() {
        return BOOKS.keySet();
    }

    /**
     * Returns the count of books that are not excluded from the Codex.
     */
    public static int getCodexEligibleCount() {
        return (int) BOOKS.values().stream().filter(def -> !def.codexExclude()).count();
    }

    /**
     * Returns the set of book IDs that are eligible for Codex inclusion (not excluded).
     */
    public static Set<String> getCodexEligibleIds() {
        Set<String> ids = new java.util.HashSet<>();
        for (LoreBookDefinition def : BOOKS.values()) {
            if (!def.codexExclude()) {
                ids.add(def.id());
            }
        }
        return ids;
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
