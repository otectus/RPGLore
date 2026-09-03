package com.rpglore.codex;

import com.rpglore.RpgLoreMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Persists per-player Codex collection data to the world's data folder.
 * All access is on the server main thread (Forge events, command handlers, enqueueWork).
 */
public class CodexTrackingData extends SavedData {

    private static final String DATA_NAME = RpgLoreMod.MODID + "_codex";

    /** Upper bound on banked spare copies per book — guards against unbounded NBT growth from farming. */
    private static final int MAX_SPARE_COPIES = 99;

    /** Current save format. Version 1 (implicit, untagged) had no read/favorite/discovery state. */
    private static final int CURRENT_VERSION = 2;

    @Nullable
    private static CodexTrackingData instance;

    private final Map<UUID, PlayerCodexData> playerCodexes = new HashMap<>();

    public CodexTrackingData() {}

    public static void setInstance(@Nullable CodexTrackingData data) {
        instance = data;
    }

    @Nullable
    public static CodexTrackingData getInstance() {
        return instance;
    }

    // --- Core API ---

    public boolean hasBook(UUID player, String bookId) {
        PlayerCodexData data = playerCodexes.get(player);
        return data != null && data.isCollected(bookId);
    }

    /**
     * Adds a book to the player's collection, stamping its discovery time.
     * The book starts unread.
     * @return true if the book was newly added, false if already present
     */
    public boolean addBook(UUID player, String bookId, long gameTime) {
        PlayerCodexData data = playerCodexes.computeIfAbsent(player, k -> new PlayerCodexData());
        boolean added = data.addCollected(bookId, gameTime);
        if (added) setDirty();
        return added;
    }

    public boolean removeBook(UUID player, String bookId) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null) return false;
        boolean removed = data.remove(bookId);
        if (removed) setDirty();
        return removed;
    }

    public Set<String> getCollectedBooks(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null) return Set.of();
        return data.collectedView();
    }

    // --- Read / favorite / discovery state ---

    public boolean isRead(UUID player, String bookId) {
        PlayerCodexData data = playerCodexes.get(player);
        return data != null && data.isRead(bookId);
    }

    /**
     * Marks a collected book as read. Uncollected ids are rejected.
     * @return true if this flipped an unread collected book to read
     */
    public boolean markRead(UUID player, String bookId) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null) return false;
        boolean changed = data.markRead(bookId);
        if (changed) setDirty();
        return changed;
    }

    public Set<String> getReadBooks(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null) return Set.of();
        return data.readView();
    }

    public boolean isFavorite(UUID player, String bookId) {
        PlayerCodexData data = playerCodexes.get(player);
        return data != null && data.isFavorite(bookId);
    }

    /**
     * Sets the favorite flag on a collected book. Uncollected ids are rejected.
     * @return true if the flag changed
     */
    public boolean setFavorite(UUID player, String bookId, boolean favorite) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null) return false;
        boolean changed = data.setFavorite(bookId, favorite);
        if (changed) setDirty();
        return changed;
    }

    public Set<String> getFavoriteBooks(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null) return Set.of();
        return data.favoritesView();
    }

    /** @return the game time the book was first collected, or 0 when unknown (migrated saves). */
    public long getDiscoveredAt(UUID player, String bookId) {
        PlayerCodexData data = playerCodexes.get(player);
        return data != null ? data.discoveredAt(bookId) : 0L;
    }

    // --- Spare copy bank ---
    //
    // The first absorbed copy of a book becomes a permanent, readable "master"
    // (tracked in the collected set). Every additional duplicate absorbed is
    // banked here as a spare copy. Extraction draws this bank down; the master
    // itself is never extractable.

    /**
     * @return the number of extractable spare copies banked for this book.
     */
    public int getCopies(UUID player, String bookId) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null) return 0;
        return data.copies(bookId);
    }

    /**
     * Banks one spare copy of a book (a duplicate the player absorbed).
     */
    public void addCopy(UUID player, String bookId) {
        PlayerCodexData data = playerCodexes.computeIfAbsent(player, k -> new PlayerCodexData());
        if (data.addCopy(bookId)) setDirty();
    }

    /**
     * Consumes one banked spare copy (an extraction).
     * @return true if a spare was available and consumed, false if the bank was empty
     */
    public boolean consumeCopy(UUID player, String bookId) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null) return false;
        boolean consumed = data.consumeCopy(bookId);
        if (consumed) setDirty();
        return consumed;
    }

    /**
     * @return a snapshot of all banked spare copies for the player (bookId -> count).
     */
    public Map<String, Integer> getCopiesMap(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null) return Map.of();
        return data.copiesView();
    }

    public int getCollectedCount(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        return data != null ? data.collectedCount() : 0;
    }

    public void clearPlayer(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data != null) {
            data.clearAll();
            setDirty();
        }
    }

    // --- Duplicate prevention toggle ---

    public boolean isPreventDuplicates(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        return data != null && data.preventDuplicatePickup;
    }

    public void setPreventDuplicates(UUID player, boolean state) {
        PlayerCodexData data = playerCodexes.computeIfAbsent(player, k -> new PlayerCodexData());
        data.preventDuplicatePickup = state;
        setDirty();
    }

    // --- Initial grant tracking ---

    public boolean hasEverReceivedCodex(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        return data != null && data.hasEverReceivedCodex;
    }

    public void markCodexGranted(UUID player) {
        PlayerCodexData data = playerCodexes.computeIfAbsent(player, k -> new PlayerCodexData());
        data.hasEverReceivedCodex = true;
        setDirty();
    }

    // --- Death stash ---

    /**
     * Parks a soul-bound Codex in save data until the player respawns.
     * A non-null curioSlot records the Curios slot it was equipped in.
     */
    public void stashCodex(UUID player, ItemStack stack, @Nullable String curioSlot, int curioIndex) {
        PlayerCodexData data = playerCodexes.computeIfAbsent(player, k -> new PlayerCodexData());
        if (data.pendingCodex != null) {
            RpgLoreMod.LOGGER.warn("Overwriting existing stashed Codex for {} — duplicate collapsed", player);
        }

        CompoundTag stashTag = new CompoundTag();
        stashTag.put("stack", stack.save(new CompoundTag()));
        if (curioSlot != null) {
            stashTag.putString("curio_slot", curioSlot);
            stashTag.putInt("curio_index", curioIndex);
        }

        data.pendingCodex = stashTag;
        setDirty();
    }

    /**
     * Removes and returns the stashed Codex, if any.
     */
    @Nullable
    public StashedCodex popStashedCodex(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null || data.pendingCodex == null) return null;

        CompoundTag stashTag = data.pendingCodex;
        data.pendingCodex = null;
        setDirty();

        ItemStack stack = ItemStack.of(stashTag.getCompound("stack"));
        if (stack.isEmpty()) return null;

        String curioSlot = stashTag.contains("curio_slot", Tag.TAG_STRING)
                ? stashTag.getString("curio_slot")
                : null;
        return new StashedCodex(stack, curioSlot, stashTag.getInt("curio_index"));
    }

    public boolean hasStashedCodex(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        return data != null && data.pendingCodex != null;
    }

    // --- Maintenance ---

    /**
     * Removes collected book entries that are no longer codex-eligible.
     * Uses the eligible set (not all book IDs) so that books switched to
     * codexExclude=true are also pruned.
     */
    /** @return the players whose codex state actually changed. */
    public Set<UUID> pruneStaleEntries(Set<String> codexEligibleIds) {
        Set<UUID> pruned = new java.util.HashSet<>();
        for (Map.Entry<UUID, PlayerCodexData> entry : playerCodexes.entrySet()) {
            if (entry.getValue().retainAll(codexEligibleIds)) {
                pruned.add(entry.getKey());
            }
        }
        if (!pruned.isEmpty()) setDirty();
        return pruned;
    }

    // --- Serialization ---

    public static CodexTrackingData load(CompoundTag tag) {
        CodexTrackingData data = new CodexTrackingData();
        CompoundTag players = tag.getCompound("players");
        for (String uuidStr : players.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                data.playerCodexes.put(uuid, PlayerCodexData.load(players.getCompound(uuidStr)));
            } catch (IllegalArgumentException e) {
                RpgLoreMod.LOGGER.warn("Invalid UUID in codex tracking data: {}", uuidStr);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, PlayerCodexData> entry : playerCodexes.entrySet()) {
            players.put(entry.getKey().toString(), PlayerCodexData.save(entry.getValue()));
        }
        tag.put("players", players);
        tag.putInt("version", CURRENT_VERSION);
        return tag;
    }

    public static CodexTrackingData getOrCreate(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                CodexTrackingData::load,
                CodexTrackingData::new,
                DATA_NAME
        );
    }

    // --- Internal data class ---

    /**
     * Per-player state. Fields are private so the collected/read/favorite/discovery
     * invariants live here instead of at every call site: read and favorite are
     * always subsets of collected, and removal clears all five structures.
     */
    private static class PlayerCodexData {
        private final Set<String> collectedBookIds = new HashSet<>();
        /** Extractable spare copies banked per book id (master copy not counted here). */
        private final Map<String, Integer> bookCopies = new HashMap<>();
        /** Subset of collected: books the player has opened at least once. */
        private final Set<String> readBookIds = new HashSet<>();
        /** Subset of collected: books flagged as favorites. */
        private final Set<String> favoriteBookIds = new HashSet<>();
        /** Game time each book was first collected; absent (or 0) means unknown. */
        private final Map<String, Long> discoveredAt = new HashMap<>();

        boolean preventDuplicatePickup = false;
        boolean hasEverReceivedCodex = false;
        /** Soul-bound Codex parked at death, restored on respawn. */
        @Nullable
        CompoundTag pendingCodex = null;

        // --- Collected ---

        boolean isCollected(String bookId) {
            return collectedBookIds.contains(bookId);
        }

        /** Adds a master copy and stamps discovery. The book stays unread. */
        boolean addCollected(String bookId, long gameTime) {
            if (!collectedBookIds.add(bookId)) return false;
            if (gameTime > 0) discoveredAt.put(bookId, gameTime);
            return true;
        }

        /** Drops the book from every structure — collected, spares, read, favorite, discovery. */
        boolean remove(String bookId) {
            boolean changed = collectedBookIds.remove(bookId);
            if (bookCopies.remove(bookId) != null) changed = true;
            if (readBookIds.remove(bookId)) changed = true;
            if (favoriteBookIds.remove(bookId)) changed = true;
            if (discoveredAt.remove(bookId) != null) changed = true;
            return changed;
        }

        /** Wipes collection state; the initial-grant flag deliberately survives. */
        void clearAll() {
            collectedBookIds.clear();
            bookCopies.clear();
            readBookIds.clear();
            favoriteBookIds.clear();
            discoveredAt.clear();
        }

        /** @return true when any of the five collections actually lost an entry. */
        boolean retainAll(Set<String> keepIds) {
            boolean changed = collectedBookIds.retainAll(keepIds);
            changed |= bookCopies.keySet().retainAll(keepIds);
            changed |= readBookIds.retainAll(keepIds);
            changed |= favoriteBookIds.retainAll(keepIds);
            changed |= discoveredAt.keySet().retainAll(keepIds);
            return changed;
        }

        int collectedCount() {
            return collectedBookIds.size();
        }

        Set<String> collectedView() {
            return Set.copyOf(collectedBookIds);
        }

        // --- Read / favorite / discovery ---

        boolean isRead(String bookId) {
            return readBookIds.contains(bookId);
        }

        boolean markRead(String bookId) {
            if (!collectedBookIds.contains(bookId)) return false;
            return readBookIds.add(bookId);
        }

        Set<String> readView() {
            return Set.copyOf(readBookIds);
        }

        boolean isFavorite(String bookId) {
            return favoriteBookIds.contains(bookId);
        }

        boolean setFavorite(String bookId, boolean favorite) {
            if (!collectedBookIds.contains(bookId)) return false;
            return favorite ? favoriteBookIds.add(bookId) : favoriteBookIds.remove(bookId);
        }

        Set<String> favoritesView() {
            return Set.copyOf(favoriteBookIds);
        }

        long discoveredAt(String bookId) {
            return discoveredAt.getOrDefault(bookId, 0L);
        }

        // --- Spare copies ---

        int copies(String bookId) {
            return bookCopies.getOrDefault(bookId, 0);
        }

        boolean addCopy(String bookId) {
            int current = bookCopies.getOrDefault(bookId, 0);
            if (current >= MAX_SPARE_COPIES) return false; // already at cap; ignore further duplicates
            bookCopies.put(bookId, current + 1);
            return true;
        }

        boolean consumeCopy(String bookId) {
            int current = bookCopies.getOrDefault(bookId, 0);
            if (current <= 0) return false;
            if (current == 1) {
                bookCopies.remove(bookId);
            } else {
                bookCopies.put(bookId, current - 1);
            }
            return true;
        }

        Map<String, Integer> copiesView() {
            return Map.copyOf(bookCopies);
        }

        // --- NBT ---

        static PlayerCodexData load(CompoundTag playerTag) {
            PlayerCodexData pData = new PlayerCodexData();

            ListTag collected = playerTag.getList("collected", Tag.TAG_STRING);
            for (int i = 0; i < collected.size(); i++) {
                pData.collectedBookIds.add(collected.getString(i));
            }

            // Spare-copy bank (absent in pre-2.x.x saves -> empty map)
            CompoundTag copies = playerTag.getCompound("copies");
            for (String bookId : copies.getAllKeys()) {
                int count = copies.getInt(bookId);
                if (count > 0) pData.bookCopies.put(bookId, count);
            }

            // Version 1 saves have no read state: everything already collected counts
            // as read, so a migrating player is not flooded with unread entries.
            if (playerTag.contains("read", Tag.TAG_LIST)) {
                ListTag read = playerTag.getList("read", Tag.TAG_STRING);
                for (int i = 0; i < read.size(); i++) {
                    String id = read.getString(i);
                    if (pData.collectedBookIds.contains(id)) pData.readBookIds.add(id);
                }
            } else {
                pData.readBookIds.addAll(pData.collectedBookIds);
            }

            ListTag favorites = playerTag.getList("favorites", Tag.TAG_STRING);
            for (int i = 0; i < favorites.size(); i++) {
                String id = favorites.getString(i);
                if (pData.collectedBookIds.contains(id)) pData.favoriteBookIds.add(id);
            }

            CompoundTag discovered = playerTag.getCompound("discovered");
            for (String bookId : discovered.getAllKeys()) {
                long when = discovered.getLong(bookId);
                if (when > 0 && pData.collectedBookIds.contains(bookId)) {
                    pData.discoveredAt.put(bookId, when);
                }
            }

            pData.preventDuplicatePickup = playerTag.getBoolean("prevent_duplicates");
            pData.hasEverReceivedCodex = playerTag.getBoolean("has_codex");

            if (playerTag.contains("pending_codex", Tag.TAG_COMPOUND)) {
                pData.pendingCodex = playerTag.getCompound("pending_codex");
            }

            return pData;
        }

        static CompoundTag save(PlayerCodexData pData) {
            CompoundTag playerTag = new CompoundTag();

            playerTag.put("collected", toStringList(pData.collectedBookIds));

            CompoundTag copies = new CompoundTag();
            for (Map.Entry<String, Integer> copyEntry : pData.bookCopies.entrySet()) {
                copies.putInt(copyEntry.getKey(), copyEntry.getValue());
            }
            playerTag.put("copies", copies);

            playerTag.put("read", toStringList(pData.readBookIds));
            playerTag.put("favorites", toStringList(pData.favoriteBookIds));

            CompoundTag discovered = new CompoundTag();
            for (Map.Entry<String, Long> discoveryEntry : pData.discoveredAt.entrySet()) {
                discovered.putLong(discoveryEntry.getKey(), discoveryEntry.getValue());
            }
            playerTag.put("discovered", discovered);

            playerTag.putBoolean("prevent_duplicates", pData.preventDuplicatePickup);
            playerTag.putBoolean("has_codex", pData.hasEverReceivedCodex);

            if (pData.pendingCodex != null) {
                playerTag.put("pending_codex", pData.pendingCodex);
            }

            return playerTag;
        }

        private static ListTag toStringList(Set<String> ids) {
            ListTag list = new ListTag();
            for (String id : ids) {
                list.add(StringTag.valueOf(id));
            }
            return list;
        }
    }

    /** A stashed Codex plus the Curios slot it should be returned to, if any. */
    public record StashedCodex(ItemStack stack, @Nullable String curioSlot, int curioIndex) {}
}
