package com.rpglore.config;

import com.rpglore.lore.LoreBookParser;
import com.rpglore.lore.LoreBookSource;
import com.rpglore.lore.LoreValidationMessage;
import com.rpglore.lore.LoreValidationReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layer merging: datapack definitions are the base, config definitions win over them,
 * and the live registry only moves when the merged catalog really changed.
 */
class LoreCatalogBuilderTest {

    @BeforeEach
    @AfterEach
    void resetState() {
        LoreCatalogBuilder.setDatapackLayer(List.of());
        LoreCatalogBuilder.setConfigLayer(List.of());
        LoreBookRegistry.setBooks(Map.of());
    }

    // --- Helpers ---

    private static LoreCatalogBuilder.LayerEntry datapack(String key, String title) {
        LoreBookSource source = new LoreBookSource(LoreBookSource.SourceKind.DATAPACK,
                key + " (datapack)", key);
        return new LoreCatalogBuilder.LayerEntry(source, LoreBookParser.parse(json(title, null), source));
    }

    private static LoreCatalogBuilder.LayerEntry config(String filename, String title) {
        return config(filename, title, null);
    }

    private static LoreCatalogBuilder.LayerEntry config(String filename, String title, String explicitId) {
        LoreBookSource source = LoreBookSource.ofConfigFile(filename);
        return new LoreCatalogBuilder.LayerEntry(source,
                LoreBookParser.parse(json(title, explicitId), source));
    }

    private static String json(String title, String explicitId) {
        String idField = explicitId == null ? "" : "\"id\": \"" + explicitId + "\",\n";
        return "{\n" + idField + "\"title\": \"" + title + "\",\n\"pages\": [\"A page.\"]\n}";
    }

    private static boolean hasMessage(LoreReloadReport report,
                                      LoreValidationMessage.Severity severity, String needle) {
        return report.messages().stream()
                .anyMatch(m -> m.severity() == severity && m.message().contains(needle));
    }

    // --- Tests ---

    @Test
    void datapackOnlyDefinitionLoads() {
        LoreCatalogBuilder.setDatapackLayer(List.of(
                datapack("towns_and_dragons:history/fall_of_ardath", "The Fall of Ardath")));

        LoreReloadReport report = LoreCatalogBuilder.rebuild();

        assertEquals(1, report.loaded());
        assertEquals(1, report.added());
        assertEquals(0, report.overrides());
        assertTrue(LoreBookRegistry.getById("towns_and_dragons:history/fall_of_ardath").isPresent());
    }

    @Test
    void configOnlyDefinitionLoads() {
        LoreCatalogBuilder.setConfigLayer(List.of(config("fallen_kingdom.json", "The Fallen Kingdom")));

        LoreReloadReport report = LoreCatalogBuilder.rebuild();

        assertEquals(1, report.loaded());
        assertEquals(0, report.overrides());
        assertEquals("The Fallen Kingdom",
                LoreBookRegistry.getById("rpg_lore:fallen_kingdom").orElseThrow().title());
    }

    @Test
    void configWinsOverDatapackAndRecordsTheOverride() {
        LoreCatalogBuilder.setDatapackLayer(List.of(
                datapack("towns_and_dragons:fallen_kingdom", "Datapack Title")));
        LoreCatalogBuilder.setConfigLayer(List.of(
                config("fallen_kingdom.json", "Config Title", "towns_and_dragons:fallen_kingdom")));

        LoreReloadReport report = LoreCatalogBuilder.rebuild();

        assertEquals(1, report.loaded());
        assertEquals(1, report.overrides());
        assertTrue(hasMessage(report, LoreValidationMessage.Severity.INFO,
                        "overrides datapack definition from towns_and_dragons"),
                "expected an INFO override message");
        assertEquals("Config Title",
                LoreBookRegistry.getById("towns_and_dragons:fallen_kingdom").orElseThrow().title());
    }

    @Test
    void removingTheConfigEntryRevealsTheDatapackDefinitionAgain() {
        LoreCatalogBuilder.setDatapackLayer(List.of(
                datapack("towns_and_dragons:fallen_kingdom", "Datapack Title")));
        LoreCatalogBuilder.setConfigLayer(List.of(
                config("fallen_kingdom.json", "Config Title", "towns_and_dragons:fallen_kingdom")));
        LoreCatalogBuilder.rebuild();

        LoreCatalogBuilder.setConfigLayer(List.of());
        LoreReloadReport report = LoreCatalogBuilder.rebuild();

        assertEquals(1, report.loaded());
        assertEquals(0, report.added());
        assertEquals(1, report.changed());
        assertEquals(0, report.removed());
        assertEquals(0, report.overrides());
        assertEquals("Datapack Title",
                LoreBookRegistry.getById("towns_and_dragons:fallen_kingdom").orElseThrow().title());
    }

    @Test
    void removingTheOnlyConfigEntryCountsAsRemoved() {
        LoreCatalogBuilder.setConfigLayer(List.of(config("fallen_kingdom.json", "The Fallen Kingdom")));
        LoreCatalogBuilder.rebuild();

        LoreCatalogBuilder.setConfigLayer(List.of());
        LoreReloadReport report = LoreCatalogBuilder.rebuild();

        assertEquals(0, report.loaded());
        assertEquals(1, report.removed());
    }

    @Test
    void duplicateIdsInOneLayerPickTheFirstSortedDisplayPath() {
        // b_second.json sorts after a_first.json, so a_first.json wins regardless of list order.
        LoreCatalogBuilder.setConfigLayer(List.of(
                config("b_second.json", "Loser", "rpg_lore:shared"),
                config("a_first.json", "Winner", "rpg_lore:shared")));

        LoreReloadReport report = LoreCatalogBuilder.rebuild();

        assertEquals(1, report.loaded());
        assertEquals("Winner", LoreBookRegistry.getById("rpg_lore:shared").orElseThrow().title());
        assertEquals(1, report.messages().stream()
                .filter(m -> m.severity() == LoreValidationMessage.Severity.WARNING
                        && m.message().contains("Duplicate lore book ID"))
                .count());
    }

    @Test
    void aSourceWithErrorsNeverEntersTheRegistry() {
        LoreBookSource source = LoreBookSource.ofConfigFile("broken.json");
        LoreValidationReport broken = LoreBookParser.parse("{ \"pages\": [\"A page.\"] }", source);
        assertTrue(broken.hasErrors());

        LoreCatalogBuilder.setConfigLayer(List.of(
                new LoreCatalogBuilder.LayerEntry(source, broken)));
        LoreReloadReport report = LoreCatalogBuilder.rebuild();

        assertEquals(0, report.loaded());
        assertTrue(report.errors() > 0);
        assertTrue(LoreBookRegistry.getAllBooks().isEmpty());
    }

    @Test
    void revisionOnlyMovesOnARealChange() {
        LoreCatalogBuilder.setConfigLayer(List.of(config("fallen_kingdom.json", "The Fallen Kingdom")));
        LoreCatalogBuilder.rebuild();
        int afterFirst = LoreBookRegistry.getRevision();

        // Same content parsed again: identical definitions, so the catalog did not move.
        LoreCatalogBuilder.setConfigLayer(List.of(config("fallen_kingdom.json", "The Fallen Kingdom")));
        LoreCatalogBuilder.rebuild();
        assertEquals(afterFirst, LoreBookRegistry.getRevision());

        LoreCatalogBuilder.setConfigLayer(List.of(config("fallen_kingdom.json", "A Different Title")));
        LoreCatalogBuilder.rebuild();
        assertEquals(afterFirst + 1, LoreBookRegistry.getRevision());
    }
}
