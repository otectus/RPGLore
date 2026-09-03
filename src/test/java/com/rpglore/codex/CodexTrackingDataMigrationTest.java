package com.rpglore.codex;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load/save behaviour of the Codex save data, in particular the 2.1.x -> 2.2.0
 * migration: a save with no read state must come back with everything already
 * collected marked read.
 */
class CodexTrackingDataMigrationTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String BOOK_A = "rpg_lore:fallen_kingdom";
    private static final String BOOK_B = "rpg_lore:sunken_city";

    /** A root tag shaped exactly as 2.1.x wrote it: no version, no read/favorites/discovered. */
    private static CompoundTag legacyTag() {
        CompoundTag playerTag = new CompoundTag();

        ListTag collected = new ListTag();
        collected.add(StringTag.valueOf(BOOK_A));
        collected.add(StringTag.valueOf(BOOK_B));
        playerTag.put("collected", collected);

        CompoundTag copies = new CompoundTag();
        copies.putInt(BOOK_A, 3);
        playerTag.put("copies", copies);

        playerTag.putBoolean("prevent_duplicates", true);
        playerTag.putBoolean("has_codex", true);

        CompoundTag players = new CompoundTag();
        players.put(PLAYER.toString(), playerTag);

        CompoundTag root = new CompoundTag();
        root.put("players", players);
        return root;
    }

    // --- Migration ---

    @Test
    void legacySaveMarksEverythingCollectedAsRead() {
        CodexTrackingData data = CodexTrackingData.load(legacyTag());

        assertEquals(Set.of(BOOK_A, BOOK_B), data.getCollectedBooks(PLAYER));
        assertEquals(Set.of(BOOK_A, BOOK_B), data.getReadBooks(PLAYER),
                "a 2.1.x save has no read state, so collected books must migrate as read");
    }

    @Test
    void legacySaveKeepsSparesAndPreferences() {
        CodexTrackingData data = CodexTrackingData.load(legacyTag());

        assertEquals(3, data.getCopies(PLAYER, BOOK_A));
        assertEquals(0, data.getCopies(PLAYER, BOOK_B));
        assertTrue(data.isPreventDuplicates(PLAYER));
        assertTrue(data.hasEverReceivedCodex(PLAYER));
    }

    @Test
    void legacySaveHasNoFavoritesOrDiscoveryTimes() {
        CodexTrackingData data = CodexTrackingData.load(legacyTag());

        assertEquals(Set.of(), data.getFavoriteBooks(PLAYER));
        assertEquals(0L, data.getDiscoveredAt(PLAYER, BOOK_A));
        assertEquals(0L, data.getDiscoveredAt(PLAYER, BOOK_B));
    }

    // --- Round trip ---

    @Test
    void newKeysSurviveSaveAndLoad() {
        CodexTrackingData original = new CodexTrackingData();
        original.addBook(PLAYER, BOOK_A, 1234L);
        original.addBook(PLAYER, BOOK_B, 5678L);
        original.addCopy(PLAYER, BOOK_A);
        original.markRead(PLAYER, BOOK_A);
        original.setFavorite(PLAYER, BOOK_B, true);
        original.setPreventDuplicates(PLAYER, true);
        original.markCodexGranted(PLAYER);

        CodexTrackingData reloaded = CodexTrackingData.load(original.save(new CompoundTag()));

        assertEquals(Set.of(BOOK_A, BOOK_B), reloaded.getCollectedBooks(PLAYER));
        assertEquals(Set.of(BOOK_A), reloaded.getReadBooks(PLAYER));
        assertEquals(Set.of(BOOK_B), reloaded.getFavoriteBooks(PLAYER));
        assertEquals(1234L, reloaded.getDiscoveredAt(PLAYER, BOOK_A));
        assertEquals(5678L, reloaded.getDiscoveredAt(PLAYER, BOOK_B));
        assertEquals(1, reloaded.getCopies(PLAYER, BOOK_A));
        assertTrue(reloaded.isPreventDuplicates(PLAYER));
        assertTrue(reloaded.hasEverReceivedCodex(PLAYER));
    }

    @Test
    void saveTagsTheCurrentVersion() {
        CompoundTag saved = new CodexTrackingData().save(new CompoundTag());
        assertEquals(2, saved.getInt("version"));
    }

    // --- Invariants ---

    @Test
    void readAndFavoriteAreRejectedForUncollectedBooks() {
        CodexTrackingData data = new CodexTrackingData();
        data.addBook(PLAYER, BOOK_A, 100L);

        assertFalse(data.markRead(PLAYER, BOOK_B));
        assertFalse(data.setFavorite(PLAYER, BOOK_B, true));
        assertEquals(Set.of(), data.getFavoriteBooks(PLAYER));
        assertEquals(Set.of(), data.getReadBooks(PLAYER));
    }

    @Test
    void collectedBookStartsUnread() {
        CodexTrackingData data = new CodexTrackingData();
        data.addBook(PLAYER, BOOK_A, 100L);

        assertFalse(data.isRead(PLAYER, BOOK_A));
        assertTrue(data.markRead(PLAYER, BOOK_A));
        assertFalse(data.markRead(PLAYER, BOOK_A), "marking read twice is not a change");
    }

    @Test
    void removeClearsEveryStructure() {
        CodexTrackingData data = new CodexTrackingData();
        data.addBook(PLAYER, BOOK_A, 100L);
        data.addCopy(PLAYER, BOOK_A);
        data.markRead(PLAYER, BOOK_A);
        data.setFavorite(PLAYER, BOOK_A, true);

        assertTrue(data.removeBook(PLAYER, BOOK_A));

        assertFalse(data.hasBook(PLAYER, BOOK_A));
        assertEquals(0, data.getCopies(PLAYER, BOOK_A));
        assertFalse(data.isRead(PLAYER, BOOK_A));
        assertFalse(data.isFavorite(PLAYER, BOOK_A));
        assertEquals(0L, data.getDiscoveredAt(PLAYER, BOOK_A));
    }

    @Test
    void clearPlayerKeepsTheGrantFlag() {
        CodexTrackingData data = new CodexTrackingData();
        data.addBook(PLAYER, BOOK_A, 100L);
        data.markRead(PLAYER, BOOK_A);
        data.setFavorite(PLAYER, BOOK_A, true);
        data.addCopy(PLAYER, BOOK_A);
        data.markCodexGranted(PLAYER);

        data.clearPlayer(PLAYER);

        assertEquals(Set.of(), data.getCollectedBooks(PLAYER));
        assertEquals(Set.of(), data.getReadBooks(PLAYER));
        assertEquals(Set.of(), data.getFavoriteBooks(PLAYER));
        assertEquals(0, data.getCopies(PLAYER, BOOK_A));
        assertTrue(data.hasEverReceivedCodex(PLAYER),
                "resetting a collection must not make the Codex grantable again");
    }
}
