package com.rpglore.config;

import com.rpglore.data.LoreTrackingData;
import com.rpglore.lore.DropConditionContext;
import com.rpglore.lore.LoreBookDefinition;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Central registry for loaded lore book definitions.
 * Provides query methods and delegates per-player tracking to LoreTrackingData.
 */
public final class LoreBookRegistry {

    private static volatile Map<String, LoreBookDefinition> BOOKS = Map.of();

    @Nullable
    private static volatile LoreTrackingData trackingData;

    public static void setBooks(Map<String, LoreBookDefinition> books) {
        BOOKS = Collections.unmodifiableMap(books);
    }

    public static void setTrackingData(@Nullable LoreTrackingData data) {
        trackingData = data;
    }

    @Nullable
    public static LoreTrackingData getTrackingData() {
        return trackingData;
    }

    // --- Query methods ---

    public static List<LoreBookDefinition> getMatchingBooks(DropConditionContext ctx) {
        List<LoreBookDefinition> matching = new ArrayList<>();
        for (LoreBookDefinition def : BOOKS.values()) {
            if (ctx.matches(def.dropCondition())) {
                matching.add(def);
            }
        }
        return matching;
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
