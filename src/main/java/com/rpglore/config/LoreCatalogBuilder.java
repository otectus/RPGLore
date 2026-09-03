package com.rpglore.config;

import com.rpglore.RpgLoreMod;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookSource;
import com.rpglore.lore.LoreValidationMessage;
import com.rpglore.lore.LoreValidationReport;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Merges the two definition layers into the live catalog.
 *
 * <p>Datapack definitions form the base layer; config definitions are applied on top, so a
 * pack shipping {@code data/&lt;ns&gt;/rpg_lore/books/x.json} can always be overridden by the
 * server owner's {@code config/rpg_lore/books} file with the same id. Every override is
 * recorded as an INFO diagnostic and counted in the reload report.
 *
 * <p>Pure Java plus {@link ResourceLocation}: the datapack listener, the server start hook and
 * the reload command all feed layers in from the outside, and none of that machinery is imported
 * here.
 */
public final class LoreCatalogBuilder {

    /** One parsed source in a layer. The report carries the definition when it loaded. */
    public record LayerEntry(LoreBookSource source, LoreValidationReport report) {}

    private static volatile List<LayerEntry> datapackLayer = List.of();
    private static volatile List<LayerEntry> configLayer = List.of();

    private LoreCatalogBuilder() {}

    public static void setDatapackLayer(List<LayerEntry> entries) {
        datapackLayer = List.copyOf(entries);
    }

    public static void setConfigLayer(List<LayerEntry> entries) {
        configLayer = List.copyOf(entries);
    }

    public static List<LayerEntry> getDatapackLayer() {
        return datapackLayer;
    }

    public static List<LayerEntry> getConfigLayer() {
        return configLayer;
    }

    /** Every parsed source across both layers, datapack first, for {@code /rpglore validate}. */
    public static List<LayerEntry> getAllReports() {
        List<LayerEntry> all = new ArrayList<>(datapackLayer);
        all.addAll(configLayer);
        return all;
    }

    /** Finds the source that produced the given book id, preferring the winning config entry. */
    public static Optional<LayerEntry> findReport(String bookId) {
        Optional<LayerEntry> fromConfig = findIn(configLayer, bookId);
        return fromConfig.isPresent() ? fromConfig : findIn(datapackLayer, bookId);
    }

    private static Optional<LayerEntry> findIn(List<LayerEntry> layer, String bookId) {
        for (LayerEntry entry : layer) {
            LoreBookDefinition def = entry.report().definition();
            if (def != null && def.id().equals(bookId)) {
                return Optional.of(entry);
            }
        }
        for (LayerEntry entry : layer) {
            for (LoreValidationMessage msg : entry.report().messages()) {
                if (bookId.equals(msg.bookId())) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Rebuilds the merged catalog from both layers, swaps it into {@link LoreBookRegistry}
     * and reports what changed against the catalog that was live before the call.
     */
    public static LoreReloadReport rebuild() {
        Map<String, LoreBookDefinition> previous = snapshot();
        List<LoreValidationMessage> messages = new ArrayList<>();

        Map<String, LayerEntry> datapackWinners = resolveLayer(datapackLayer, messages);
        Map<String, LayerEntry> configWinners = resolveLayer(configLayer, messages);

        Map<String, LoreBookDefinition> merged = new LinkedHashMap<>();
        for (Map.Entry<String, LayerEntry> entry : datapackWinners.entrySet()) {
            merged.put(entry.getKey(), entry.getValue().report().definition());
        }

        int overrides = 0;
        for (Map.Entry<String, LayerEntry> entry : configWinners.entrySet()) {
            String id = entry.getKey();
            LayerEntry shadowed = datapackWinners.get(id);
            if (shadowed != null) {
                overrides++;
                messages.add(LoreValidationMessage.info(entry.getValue().source().displayPath(), id, null,
                        // The pack namespace comes from the shadowed source's resource key, not
                        // from the book id, which the pack may have declared in any namespace.
                        "Config definition " + id + " overrides datapack definition from "
                                + namespaceOf(shadowed.source().defaultId())));
            }
            merged.put(id, entry.getValue().report().definition());
        }

        for (LoreValidationMessage msg : messages) {
            log(msg);
        }

        LoreBookRegistry.setBooks(merged);

        int added = 0;
        int changed = 0;
        for (Map.Entry<String, LoreBookDefinition> entry : merged.entrySet()) {
            LoreBookDefinition old = previous.get(entry.getKey());
            if (old == null) {
                added++;
            } else if (!old.equals(entry.getValue())) {
                changed++;
            }
        }
        int removed = 0;
        for (String id : previous.keySet()) {
            if (!merged.containsKey(id)) removed++;
        }

        int warnings = count(messages, LoreValidationMessage.Severity.WARNING);
        int errors = count(messages, LoreValidationMessage.Severity.ERROR);

        RpgLoreMod.LOGGER.info("Loaded {} lore book definition(s) ({} datapack, {} config, {} override(s))",
                merged.size(), datapackWinners.size(), configWinners.size(), overrides);

        return new LoreReloadReport(merged.size(), added, changed, removed,
                warnings, errors, overrides, false, List.copyOf(messages));
    }

    /**
     * Picks one entry per id inside a single layer. Sources are visited in sorted display-path
     * order so the winner never depends on filesystem or resource-pack iteration order; the
     * losers are reported and dropped. Sources that failed to load contribute their diagnostics
     * and nothing else.
     */
    private static Map<String, LayerEntry> resolveLayer(List<LayerEntry> layer,
                                                        List<LoreValidationMessage> messages) {
        List<LayerEntry> sorted = new ArrayList<>(layer);
        sorted.sort(Comparator.comparing(e -> e.source().displayPath()));

        Map<String, LayerEntry> winners = new LinkedHashMap<>();
        for (LayerEntry entry : sorted) {
            messages.addAll(entry.report().messages());

            LoreBookDefinition def = entry.report().definition();
            if (def == null || entry.report().hasErrors()) continue;

            if (winners.containsKey(def.id())) {
                messages.add(LoreValidationMessage.warning(entry.source().displayPath(), def.id(), null,
                        "Duplicate lore book ID '" + def.id() + "', this source was skipped."));
            } else {
                winners.put(def.id(), entry);
            }
        }
        return winners;
    }

    private static String namespaceOf(String bookId) {
        ResourceLocation rl = ResourceLocation.tryParse(bookId);
        return rl != null ? rl.getNamespace() : bookId;
    }

    private static Map<String, LoreBookDefinition> snapshot() {
        Map<String, LoreBookDefinition> map = new LinkedHashMap<>();
        for (LoreBookDefinition def : LoreBookRegistry.getAllBooks()) {
            map.put(def.id(), def);
        }
        return map;
    }

    private static int count(List<LoreValidationMessage> messages, LoreValidationMessage.Severity severity) {
        return (int) messages.stream().filter(m -> m.severity() == severity).count();
    }

    private static void log(@Nullable LoreValidationMessage msg) {
        if (msg == null) return;
        switch (msg.severity()) {
            case ERROR -> RpgLoreMod.LOGGER.error(msg.format());
            case WARNING -> RpgLoreMod.LOGGER.warn(msg.format());
            case INFO -> RpgLoreMod.LOGGER.info(msg.format());
        }
    }
}
