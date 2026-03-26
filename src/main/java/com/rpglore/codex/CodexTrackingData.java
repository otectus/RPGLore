package com.rpglore.codex;

import com.rpglore.RpgLoreMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists per-player Codex collection data to the world's data folder.
 * Thread-safe via ConcurrentHashMap.
 */
public class CodexTrackingData extends SavedData {

    private static final String DATA_NAME = RpgLoreMod.MODID + "_codex";

    @Nullable
    private static volatile CodexTrackingData instance;

    private final ConcurrentHashMap<UUID, PlayerCodexData> playerCodexes = new ConcurrentHashMap<>();

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
        return data != null && data.collectedBookIds.contains(bookId);
    }

    /**
     * Adds a book to the player's collection.
     * @return true if the book was newly added, false if already present
     */
    public boolean addBook(UUID player, String bookId) {
        PlayerCodexData data = playerCodexes.computeIfAbsent(player, k -> new PlayerCodexData());
        boolean added = data.collectedBookIds.add(bookId);
        if (added) setDirty();
        return added;
    }

    public boolean removeBook(UUID player, String bookId) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null) return false;
        boolean removed = data.collectedBookIds.remove(bookId);
        if (removed) setDirty();
        return removed;
    }

    public Set<String> getCollectedBooks(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data == null) return Set.of();
        return Set.copyOf(data.collectedBookIds);
    }

    public int getCollectedCount(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        return data != null ? data.collectedBookIds.size() : 0;
    }

    public void clearPlayer(UUID player) {
        PlayerCodexData data = playerCodexes.get(player);
        if (data != null) {
            data.collectedBookIds.clear();
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

    // --- Maintenance ---

    public void pruneStaleEntries(Set<String> validBookIds) {
        for (PlayerCodexData data : playerCodexes.values()) {
            data.collectedBookIds.retainAll(validBookIds);
        }
        setDirty();
    }

    // --- Serialization ---

    public static CodexTrackingData load(CompoundTag tag) {
        CodexTrackingData data = new CodexTrackingData();
        CompoundTag players = tag.getCompound("players");
        for (String uuidStr : players.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                CompoundTag playerTag = players.getCompound(uuidStr);
                PlayerCodexData pData = new PlayerCodexData();

                ListTag collected = playerTag.getList("collected", Tag.TAG_STRING);
                for (int i = 0; i < collected.size(); i++) {
                    pData.collectedBookIds.add(collected.getString(i));
                }

                pData.preventDuplicatePickup = playerTag.getBoolean("prevent_duplicates");
                pData.hasEverReceivedCodex = playerTag.getBoolean("has_codex");

                data.playerCodexes.put(uuid, pData);
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
            CompoundTag playerTag = new CompoundTag();
            PlayerCodexData pData = entry.getValue();

            ListTag collected = new ListTag();
            for (String bookId : pData.collectedBookIds) {
                collected.add(StringTag.valueOf(bookId));
            }
            playerTag.put("collected", collected);
            playerTag.putBoolean("prevent_duplicates", pData.preventDuplicatePickup);
            playerTag.putBoolean("has_codex", pData.hasEverReceivedCodex);

            players.put(entry.getKey().toString(), playerTag);
        }
        tag.put("players", players);
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

    private static class PlayerCodexData {
        final Set<String> collectedBookIds = ConcurrentHashMap.newKeySet();
        volatile boolean preventDuplicatePickup = false;
        volatile boolean hasEverReceivedCodex = false;
    }
}
