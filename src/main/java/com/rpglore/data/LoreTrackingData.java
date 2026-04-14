package com.rpglore.data;

import com.rpglore.RpgLoreMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists per-player lore book copy counts to the world's data folder.
 * All access is on the server main thread (Forge events, command handlers, enqueueWork).
 */
public class LoreTrackingData extends SavedData {

    private static final String DATA_NAME = RpgLoreMod.MODID + "_tracking";

    private final Map<UUID, Map<String, Integer>> playerCopies = new HashMap<>();

    public LoreTrackingData() {}

    public static LoreTrackingData load(CompoundTag tag) {
        LoreTrackingData data = new LoreTrackingData();
        CompoundTag players = tag.getCompound("players");
        for (String uuidStr : players.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                CompoundTag books = players.getCompound(uuidStr);
                Map<String, Integer> bookCounts = new HashMap<>();
                for (String bookId : books.getAllKeys()) {
                    bookCounts.put(bookId, books.getInt(bookId));
                }
                data.playerCopies.put(uuid, bookCounts);
            } catch (IllegalArgumentException e) {
                RpgLoreMod.LOGGER.warn("Invalid UUID in tracking data: {}", uuidStr);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        for (Map.Entry<UUID, Map<String, Integer>> entry : playerCopies.entrySet()) {
            CompoundTag books = new CompoundTag();
            for (Map.Entry<String, Integer> bookEntry : entry.getValue().entrySet()) {
                books.putInt(bookEntry.getKey(), bookEntry.getValue());
            }
            players.put(entry.getKey().toString(), books);
        }
        tag.put("players", players);
        return tag;
    }

    public boolean canPlayerReceive(UUID playerUuid, String bookId, int maxCopies) {
        if (maxCopies < 0) return true;
        Map<String, Integer> books = playerCopies.get(playerUuid);
        if (books == null) return true;
        return books.getOrDefault(bookId, 0) < maxCopies;
    }

    public void recordPlayerReceived(UUID playerUuid, String bookId) {
        playerCopies.computeIfAbsent(playerUuid, k -> new HashMap<>())
                .merge(bookId, 1, Integer::sum);
        setDirty();
    }

    /**
     * Removes tracking entries for book IDs that are no longer loaded.
     */
    public void pruneStaleEntries(java.util.Set<String> validBookIds) {
        for (Map<String, Integer> books : playerCopies.values()) {
            books.keySet().retainAll(validBookIds);
        }
        // Remove players with no remaining entries
        playerCopies.entrySet().removeIf(e -> e.getValue().isEmpty());
        setDirty();
    }

    public static LoreTrackingData getOrCreate(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                LoreTrackingData::load,
                LoreTrackingData::new,
                DATA_NAME
        );
    }
}
