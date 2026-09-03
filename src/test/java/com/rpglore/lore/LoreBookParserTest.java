package com.rpglore.lore;

import com.rpglore.config.BooksConfigLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser behaviour, including the 2.1.x compatibility guarantees.
 */
class LoreBookParserTest {

    private static final LoreBookSource SOURCE = LoreBookSource.ofConfigFile("fallen_kingdom.json");

    private static LoreValidationReport parse(String json) {
        return LoreBookParser.parse(json, SOURCE);
    }

    private static LoreBookDefinition parseOk(String json) {
        LoreValidationReport report = parse(json);
        assertTrue(report.isLoaded(), () -> "expected the book to load, messages: " + formatAll(report));
        return report.definition();
    }

    private static String formatAll(LoreValidationReport report) {
        StringBuilder sb = new StringBuilder();
        for (LoreValidationMessage msg : report.messages()) {
            sb.append('\n').append(msg.format());
        }
        return sb.toString();
    }

    private static boolean anyMessageContains(LoreValidationReport report,
                                              LoreValidationMessage.Severity severity, String needle) {
        return report.messages().stream()
                .anyMatch(m -> m.severity() == severity && m.format().contains(needle));
    }

    // --- Valid definitions ---

    @Test
    void minimalBookParses() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "The Fallen Kingdom",
                  "pages": ["A single page."]
                }
                """);

        assertEquals("rpg_lore:fallen_kingdom", def.id());
        assertEquals("The Fallen Kingdom", def.title());
        assertEquals("Unknown", def.author());
        assertEquals(0, def.generation());
        assertEquals(1.0, def.weight());
        assertTrue(def.showGlint());
        assertFalse(def.codexExclude());
        assertEquals(1, def.pages().size());
        assertEquals(DropCondition.defaultCondition(), def.dropCondition());
    }

    @Test
    void fullBookParsesEveryField() {
        LoreBookDefinition def = parseOk("""
                {
                  "format_version": 1,
                  "id": "rpg_lore:custom_id",
                  "title": "Chronicle of Ash",
                  "author": "A Scribe",
                  "generation": 2,
                  "weight": 3.5,
                  "title_color": "#ffd700",
                  "author_color": "AAAAAA",
                  "description": "A burnt account.",
                  "description_color": "55FFFF",
                  "hide_generation": true,
                  "show_glint": false,
                  "category": "History",
                  "codex_exclude": true,
                  "drop_conditions": {
                    "mob_types": ["minecraft:zombie"],
                    "mob_tags": ["minecraft:undead"],
                    "biomes": ["minecraft:plains"],
                    "biome_tags": ["minecraft:is_overworld"],
                    "dimensions": ["minecraft:overworld"],
                    "min_y": -10,
                    "max_y": 60,
                    "time": "NIGHT_ONLY",
                    "weather": "THUNDER_ONLY",
                    "require_player_kill": false,
                    "base_chance": 0.25,
                    "max_copies_per_player": 2
                  },
                  "pages": ["One", "Two"]
                }
                """);

        assertEquals("rpg_lore:custom_id", def.id());
        assertEquals("A Scribe", def.author());
        assertEquals(2, def.generation());
        assertEquals(3.5, def.weight());
        assertEquals("FFD700", def.titleColor());
        assertEquals("AAAAAA", def.authorColor());
        assertEquals("55FFFF", def.descriptionColor());
        assertTrue(def.hideGeneration());
        assertFalse(def.showGlint());
        assertEquals("History", def.category());
        assertTrue(def.codexExclude());

        DropCondition drop = def.dropCondition();
        assertEquals(List.of(new net.minecraft.resources.ResourceLocation("minecraft:zombie")), drop.mobTypes());
        assertEquals(List.of("minecraft:undead"), drop.mobTags());
        assertEquals(Integer.valueOf(-10), drop.minY());
        assertEquals(Integer.valueOf(60), drop.maxY());
        assertEquals(DropCondition.TimeFilter.NIGHT_ONLY, drop.time());
        assertEquals(DropCondition.WeatherFilter.THUNDER_ONLY, drop.weather());
        assertFalse(drop.requirePlayerKill());
        assertEquals(Double.valueOf(0.25), drop.baseChance());
        assertEquals(2, drop.maxCopiesPerPlayer());
    }

    @Test
    void plainStringPageIsWrappedAsTextComponent() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "T",
                  "pages": ["Just words."]
                }
                """);

        assertTrue(def.pages().get(0).contains("\"text\""));
        assertTrue(def.pages().get(0).contains("Just words."));
    }

    @Test
    void jsonComponentPagesArePreserved() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "T",
                  "pages": [
                    "{\\"text\\":\\"From a string\\"}",
                    {"text": "From an object", "color": "gold"}
                  ]
                }
                """);

        assertEquals(2, def.pages().size());
        assertTrue(def.pages().get(0).contains("From a string"));
        assertTrue(def.pages().get(1).contains("From an object"));
        assertTrue(def.pages().get(1).contains("gold"));
    }

    @Test
    void escapedQuotesInPagesSurvive() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "T",
                  "pages": ["He said \\"hello\\" once."]
                }
                """);

        assertTrue(def.pages().get(0).contains("hello"));
    }

    @Test
    void apostrophesSurvive() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "The King's Fall",
                  "pages": ["It's the king's own tale."]
                }
                """);

        assertEquals("The King's Fall", def.title());
        assertTrue(def.pages().get(0).contains("king"));
    }

    @Test
    void backslashesSurvive() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "T",
                  "pages": ["A path: C:\\\\temp\\\\notes"]
                }
                """);

        assertTrue(def.pages().get(0).contains("temp"));
    }

    @Test
    void newlinesInPagesSurvive() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "T",
                  "pages": ["Line one\\nLine two"]
                }
                """);

        assertTrue(def.pages().get(0).contains("\\n"));
    }

    @Test
    void unicodeSurvives() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "Königreich ✦",
                  "pages": ["Ünïcødé ✦ text"]
                }
                """);

        assertEquals("Königreich ✦", def.title());
        assertTrue(def.pages().get(0).contains("Ünïcødé"));
    }

    // --- Invalid definitions ---

    @Test
    void invalidJsonSyntaxReportsPositionAndPagesPath() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "pages": [
                    "{"text":"unescaped"}"
                  ]
                }
                """);

        assertFalse(report.isLoaded());
        assertTrue(report.hasErrors());

        LoreValidationMessage msg = report.messages().get(0);
        assertTrue(msg.line() > 0, "line should be known");
        assertTrue(msg.column() > 0, "column should be known");
        String located = (msg.jsonPath() == null ? "" : msg.jsonPath()) + " " + msg.message();
        assertTrue(located.contains("pages"), () -> "expected a pages reference in: " + msg.format());
    }

    @Test
    void missingTitleIsAnError() {
        LoreValidationReport report = parse("""
                {
                  "pages": ["A page."]
                }
                """);

        assertFalse(report.isLoaded());
        assertTrue(anyMessageContains(report, LoreValidationMessage.Severity.ERROR, "no title"));
    }

    @Test
    void missingPagesIsAnError() {
        LoreValidationReport report = parse("""
                {
                  "title": "T"
                }
                """);

        assertFalse(report.isLoaded());
        assertTrue(anyMessageContains(report, LoreValidationMessage.Severity.ERROR, "pages"));
    }

    @Test
    void invalidResourceLocationIdIsAnError() {
        LoreValidationReport report = parse("""
                {
                  "id": "Bad ID!",
                  "title": "T",
                  "pages": ["A page."]
                }
                """);

        assertFalse(report.isLoaded());
        assertTrue(anyMessageContains(report, LoreValidationMessage.Severity.ERROR, "Invalid lore book id"));
    }

    @Test
    void wrongFieldTypeIsAnError() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "weight": "heavy",
                  "pages": ["A page."]
                }
                """);

        assertFalse(report.isLoaded());
        assertTrue(anyMessageContains(report, LoreValidationMessage.Severity.ERROR, "weight"));
    }

    @Test
    void invalidColorIsWarnedAndIgnored() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "title_color": "not-a-color",
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        assertNull(report.definition().titleColor());
        assertTrue(anyMessageContains(report, LoreValidationMessage.Severity.WARNING, "Invalid title_color"));
    }

    @Test
    void zeroWeightIsClampedWithWarning() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "weight": 0,
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        assertEquals(0.01, report.definition().weight());
        assertTrue(anyMessageContains(report, LoreValidationMessage.Severity.WARNING, "clamping to 0.01"));
    }

    @Test
    void negativeWeightIsClampedWithWarning() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "weight": -4.5,
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        assertEquals(0.01, report.definition().weight());
        assertEquals(1, report.warningCount());
    }

    @Test
    void outOfRangeGenerationIsClampedWithWarning() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "generation": 9,
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        assertEquals(3, report.definition().generation());
        assertTrue(anyMessageContains(report, LoreValidationMessage.Severity.WARNING, "clamping to 0-3"));
    }

    @Test
    void minYGreaterThanMaxYIsSwappedWithWarning() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "drop_conditions": {
                    "min_y": 80,
                    "max_y": 10
                  },
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        assertEquals(Integer.valueOf(10), report.definition().dropCondition().minY());
        assertEquals(Integer.valueOf(80), report.definition().dropCondition().maxY());
        assertTrue(anyMessageContains(report, LoreValidationMessage.Severity.WARNING, "swapping values"));
    }

    @Test
    void invalidTimeFilterFallsBackToAny() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "drop_conditions": { "time": "MIDNIGHT" },
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        assertEquals(DropCondition.TimeFilter.ANY, report.definition().dropCondition().time());
        assertTrue(anyMessageContains(report, LoreValidationMessage.Severity.WARNING, "Invalid time filter"));
    }

    @Test
    void invalidWeatherFilterFallsBackToAny() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "drop_conditions": { "weather": "SNOW" },
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        assertEquals(DropCondition.WeatherFilter.ANY, report.definition().dropCondition().weather());
        assertTrue(anyMessageContains(report, LoreValidationMessage.Severity.WARNING, "Invalid weather filter"));
    }

    @Test
    void unknownDropConditionKeySuggestsNearestKey() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "drop_conditions": { "basechance": 0.5 },
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        LoreValidationMessage msg = report.messages().stream()
                .filter(m -> "$.drop_conditions.basechance".equals(m.jsonPath()))
                .findFirst().orElseThrow();
        assertEquals(LoreValidationMessage.Severity.WARNING, msg.severity());
        assertNotNull(msg.suggestion());
        assertTrue(msg.suggestion().contains("base_chance"));
    }

    @Test
    void unknownTopLevelKeySuggestsNearestKey() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "titel_color": "FFD700",
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        LoreValidationMessage msg = report.messages().stream()
                .filter(m -> "$.titel_color".equals(m.jsonPath()))
                .findFirst().orElseThrow();
        assertNotNull(msg.suggestion());
        assertTrue(msg.suggestion().contains("title_color"));
    }

    @Test
    void extensionNamespacedKeysAreAllowed() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "_comment": "notes for the pack author",
                  "othermod:extra": 1,
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        assertEquals(0, report.warningCount());
    }

    @Test
    void unknownFormatVersionIsAnError() {
        LoreValidationReport report = parse("""
                {
                  "format_version": 7,
                  "title": "T",
                  "pages": ["A page."]
                }
                """);

        assertFalse(report.isLoaded());
        assertTrue(anyMessageContains(report, LoreValidationMessage.Severity.ERROR, "Unknown format_version 7"));
    }

    // --- Compatibility ---

    @Test
    void legacyDefinitionMatchesExplicitFormatVersionOne() {
        String legacy = """
                {
                  "title": "The Fallen Kingdom",
                  "author": "Unknown",
                  "generation": 0,
                  "weight": 1.0,
                  "title_color": "FFD700",
                  "drop_conditions": { "require_player_kill": true },
                  "pages": ["A page."]
                }
                """;
        String versioned = """
                {
                  "format_version": 1,
                  "title": "The Fallen Kingdom",
                  "author": "Unknown",
                  "generation": 0,
                  "weight": 1.0,
                  "title_color": "FFD700",
                  "drop_conditions": { "require_player_kill": true },
                  "pages": ["A page."]
                }
                """;

        assertEquals(parseOk(versioned), parseOk(legacy));
    }

    @Test
    void datapackSourceAcceptsNamespacedIdWithNestedPath() {
        LoreBookSource datapack = new LoreBookSource(LoreBookSource.SourceKind.DATAPACK,
                "towns_and_dragons:history/fall_of_ardath (datapack)",
                "towns_and_dragons:history/fall_of_ardath");

        LoreValidationReport report = LoreBookParser.parse("""
                {
                  "title": "The Fall of Ardath",
                  "pages": ["A page."]
                }
                """, datapack);

        assertTrue(report.isLoaded(), () -> "datapack book failed to parse: " + formatAll(report));
        assertEquals("towns_and_dragons:history/fall_of_ardath", report.definition().id());
    }

    @Test
    void explicitNonRpgLoreIdIsAcceptedForADatapackSource() {
        LoreBookSource datapack = new LoreBookSource(LoreBookSource.SourceKind.DATAPACK,
                "towns_and_dragons:books/x (datapack)", "towns_and_dragons:books/x");

        LoreValidationReport report = LoreBookParser.parse("""
                {
                  "id": "towns_and_dragons:history/fall_of_ardath",
                  "title": "The Fall of Ardath",
                  "pages": ["A page."]
                }
                """, datapack);

        assertTrue(report.isLoaded(), () -> "datapack book failed to parse: " + formatAll(report));
        assertEquals("towns_and_dragons:history/fall_of_ardath", report.definition().id());
    }

    @Test
    void configFileStillDerivesItsIdFromTheFilename() {
        LoreValidationReport report = LoreBookParser.parse("""
                {
                  "title": "The Fallen Kingdom",
                  "pages": ["A page."]
                }
                """, LoreBookSource.ofConfigFile("fallen_kingdom.json"));

        assertTrue(report.isLoaded(), () -> "config book failed to parse: " + formatAll(report));
        assertEquals("rpg_lore:fallen_kingdom", report.definition().id());
    }

    @Test
    void shippedDefaultBookParsesCleanly() {
        LoreValidationReport report = parse(BooksConfigLoader.defaultBookJson());

        assertTrue(report.isLoaded(), () -> "default book failed to parse: " + formatAll(report));
        assertEquals(0, report.warningCount());
        assertEquals(0, report.errorCount());
        assertEquals("The Fallen Kingdom", report.definition().title());
        assertEquals(2, report.definition().pages().size());
        assertEquals("FFD700", report.definition().titleColor());
    }
}
