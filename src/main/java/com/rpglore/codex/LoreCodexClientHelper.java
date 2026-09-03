package com.rpglore.codex;

import com.rpglore.config.ClientConfig;
import com.rpglore.lore.LoreBookScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-only helper for the Lore Codex.
 * Kept in a separate class so that LoreCodexItem can load on a dedicated server
 * without pulling in client-only classes.
 */
@OnlyIn(Dist.CLIENT)
public final class LoreCodexClientHelper {

    /** Last catalog received. Kept between screen opens; only resent when it changes. */
    private static int catalogRevision = 0;
    private static List<CodexCatalogEntry> catalog = List.of();

    /** Last player state received. */
    private static CodexPlayerState playerState = CodexPlayerState.empty();

    @Nullable
    private static LoreCodexScreen.CodexScreenData cachedData;

    /** Search/filter/sort/page the player last left the Codex on. */
    private static CodexViewState lastViewState = CodexViewState.DEFAULT;

    public static void openCodexScreen() {
        LoreCodexScreen.CodexScreenData data = cachedData != null
                ? cachedData
                // No data yet; open with empty data, will refresh when sync arrives
                : LoreCodexScreen.CodexScreenData.empty();
        Minecraft.getInstance().setScreen(new LoreCodexScreen(data, viewStateForOpen()));
    }

    /** Stores the view state a closing (or book-opening) Codex screen was left in. */
    public static void saveViewState(CodexViewState state) {
        lastViewState = state;
    }

    private static CodexViewState viewStateForOpen() {
        if (ClientConfig.CODEX_REMEMBER_SEARCH.get()) return lastViewState;
        // The rest of the view (category, filter, sort, page) is always remembered;
        // only the typed query is dropped when the player asked for that.
        return lastViewState.withQuery("");
    }

    public static void updateCatalog(int revision, List<CodexCatalogEntry> entries) {
        catalogRevision = revision;
        catalog = entries;
        rebuildScreenData();
    }

    public static void updatePlayerState(CodexPlayerState state) {
        playerState = state;
        rebuildScreenData();
    }

    /**
     * Joins the cached catalog with the cached player state into the flat view list
     * the screen renders, then pushes it to an open screen.
     */
    private static void rebuildScreenData() {
        Map<String, CodexPlayerState.Entry> byId = new HashMap<>();
        for (CodexPlayerState.Entry entry : playerState.entries()) {
            byId.put(entry.id(), entry);
        }

        List<CodexEntryView> views = new ArrayList<>(catalog.size());
        int collectedCount = 0;
        for (CodexCatalogEntry entry : catalog) {
            CodexPlayerState.Entry state = byId.get(entry.id());
            boolean collected = state != null;
            if (collected) collectedCount++;
            views.add(CodexEntryView.of(
                    entry,
                    collected,
                    collected && state.read(),
                    collected && state.favorite(),
                    collected ? state.spares() : 0,
                    collected ? state.discoveredAt() : 0L));
        }

        // Same ordering the server used before: collected first, then by title
        views.sort(Comparator.comparing((CodexEntryView v) -> !v.collected())
                .thenComparing(CodexEntryView::title)
                .thenComparing(CodexEntryView::id));

        LoreCodexScreen.CodexScreenData data = new LoreCodexScreen.CodexScreenData(
                views,
                !playerState.storeDuplicatesAsSpares(),
                collectedCount,
                views.size(),
                playerState.allowCopy(),
                playerState.allowDuplicatePrevention(),
                playerState.revealUncollectedNames()
        );

        cachedData = data;
        // If a LoreCodexScreen is currently open, refresh it
        if (Minecraft.getInstance().screen instanceof LoreCodexScreen screen) {
            screen.refreshData(data);
        }
    }

    /** @return the catalog revision the client currently holds. */
    public static int getCatalogRevision() {
        return catalogRevision;
    }

    /**
     * Shows the collection notification / plays the collection sound for a book
     * absorbed into the Codex, honoring the client display config.
     */
    public static void showCollectionEvent(String bookTitle, boolean duplicate) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (ClientConfig.CODEX_SHOW_NOTIFICATION.get()) {
            String key = duplicate ? "rpg_lore.codex.duplicate_stored" : "rpg_lore.codex.collected";
            mc.player.displayClientMessage(
                    Component.translatable(key,
                            Component.literal(bookTitle).withStyle(ChatFormatting.GOLD)),
                    true);
        }

        if (ClientConfig.CODEX_PLAY_SOUND.get()) {
            // Duplicates use a lower pitch to distinguish a banked spare from a new master
            float pitch = duplicate ? 0.8f : 1.2f;
            mc.player.level().playLocalSound(
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS,
                    0.5f, pitch, false);
        }
    }

    public static void openBookFromCodex(ItemStack bookStack) {
        Minecraft mc = Minecraft.getInstance();
        Screen parentScreen = mc.screen;
        mc.setScreen(new LoreBookScreen(bookStack) {
            @Override
            public void onClose() {
                if (parentScreen instanceof LoreCodexScreen) {
                    // Reopen rather than reuse: the read flag just changed, so the
                    // Codex must rebuild from the fresh sync, on the remembered view
                    openCodexScreen();
                } else {
                    mc.setScreen(parentScreen);
                }
            }
        });
    }

    private LoreCodexClientHelper() {}
}
