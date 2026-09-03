package com.rpglore.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.rpglore.RpgLoreMod;
import com.rpglore.config.LoreCatalogBuilder;
import com.rpglore.config.LoreReloadReport;
import com.rpglore.lore.LoreBookParser;
import com.rpglore.lore.LoreBookSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads lore book definitions from {@code data/<namespace>/rpg_lore/books/<path>.json}.
 *
 * <p>The resource key is the book's default id, so a pack file at
 * {@code data/towns_and_dragons/rpg_lore/books/history/fall_of_ardath.json} defines
 * {@code towns_and_dragons:history/fall_of_ardath} unless the JSON names an id itself.
 *
 * <p>This runs on every server resource reload, including world start, where it fires before
 * {@code ServerStartingEvent}. It therefore never touches {@code CodexService}, which does not
 * exist yet at that point; player resyncs are driven from {@code OnDatapackSyncEvent} instead.
 */
public class DatapackLoreReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    public DatapackLoreReloadListener() {
        super(GSON, "rpg_lore/books");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<LoreCatalogBuilder.LayerEntry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> object : objects.entrySet()) {
            ResourceLocation key = object.getKey();
            LoreBookSource source = new LoreBookSource(LoreBookSource.SourceKind.DATAPACK,
                    key.getNamespace() + ":" + key.getPath() + " (datapack)", key.toString());
            entries.add(new LoreCatalogBuilder.LayerEntry(
                    source, LoreBookParser.parse(object.getValue(), source)));
        }

        LoreCatalogBuilder.setDatapackLayer(entries);
        LoreReloadReport report = LoreCatalogBuilder.rebuild();

        RpgLoreMod.LOGGER.info(
                "Datapack lore reload: {} datapack source(s), {} book(s) live, {} warning(s), {} error(s)",
                entries.size(), report.loaded(), report.warnings(), report.errors());
    }
}
