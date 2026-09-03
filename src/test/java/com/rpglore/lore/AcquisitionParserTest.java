package com.rpglore.lore;

import com.rpglore.lore.acquisition.AcquisitionRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acquisition rules: legacy drop_conditions must keep producing exactly the rule the
 * equivalent new-form entry produces, and the new rule types must validate.
 */
class AcquisitionParserTest {

    private static final LoreBookSource SOURCE = LoreBookSource.ofConfigFile("acquisition.json");

    private static LoreValidationReport parse(String json) {
        return LoreBookParser.parse(json, SOURCE);
    }

    private static LoreBookDefinition parseOk(String json) {
        LoreValidationReport report = parse(json);
        assertTrue(report.isLoaded(), () -> "expected the book to load: " + report.messages());
        return report.definition();
    }

    private static boolean anyError(LoreValidationReport report) {
        return report.messages().stream()
                .anyMatch(m -> m.severity() == LoreValidationMessage.Severity.ERROR);
    }

    // --- Legacy compatibility ---

    @Test
    void legacyDropConditionsEqualTheEquivalentEntityDropRule() {
        LoreBookDefinition legacy = parseOk("""
                {
                  "title": "T",
                  "drop_conditions": {
                    "mob_types": ["minecraft:zombie"],
                    "base_chance": 0.25,
                    "max_copies_per_player": 2,
                    "time": "NIGHT_ONLY"
                  },
                  "pages": ["A page."]
                }
                """);

        LoreBookDefinition modern = parseOk("""
                {
                  "title": "T",
                  "acquisition": [
                    {
                      "type": "entity_drop",
                      "mob_types": ["minecraft:zombie"],
                      "base_chance": 0.25,
                      "max_copies_per_player": 2,
                      "time": "NIGHT_ONLY"
                    }
                  ],
                  "pages": ["A page."]
                }
                """);

        assertEquals(1, legacy.entityDropRules().size());
        assertEquals(legacy.entityDropRules(), modern.entityDropRules());
    }

    @Test
    void noAcquisitionAtAllYieldsTheDefaultEntityDropRule() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "T",
                  "pages": ["A page."]
                }
                """);

        assertEquals(List.of(new AcquisitionRule.EntityDropAcquisition(DropCondition.defaultCondition())),
                def.acquisition());
    }

    @Test
    void legacyAndAcquisitionRulesAreBothKept() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "T",
                  "drop_conditions": { "mob_types": ["minecraft:zombie"] },
                  "acquisition": [
                    { "type": "loot_table", "loot_tables": ["minecraft:chests/simple_dungeon"] }
                  ],
                  "pages": ["A page."]
                }
                """);

        assertEquals(2, def.acquisition().size());
        assertEquals(1, def.entityDropRules().size());
        assertEquals(1, def.lootTableRules().size());
    }

    // --- New rule types ---

    @Test
    void lootTableRuleUsesDefaultsForChanceAndWeight() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "T",
                  "acquisition": [
                    { "type": "loot_table", "loot_tables": ["minecraft:chests/simple_dungeon"] }
                  ],
                  "pages": ["A page."]
                }
                """);

        AcquisitionRule.LootTableAcquisition rule = def.lootTableRules().get(0);
        assertEquals(List.of(new net.minecraft.resources.ResourceLocation("minecraft:chests/simple_dungeon")),
                rule.lootTables());
        assertEquals(1.0, rule.chance());
        assertEquals(1.0, rule.weight());
        // The book still keeps the default entity_drop rule
        assertEquals(1, def.entityDropRules().size());
    }

    @Test
    void advancementRuleDefaultsToCodexDelivery() {
        LoreBookDefinition def = parseOk("""
                {
                  "title": "T",
                  "acquisition": [
                    { "type": "advancement", "advancements": ["minecraft:story/root"] }
                  ],
                  "pages": ["A page."]
                }
                """);

        assertEquals(AcquisitionRule.Delivery.CODEX, def.advancementRules().get(0).delivery());
    }

    @Test
    void chanceAliasWinsOverBaseChanceWithAWarning() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "acquisition": [
                    { "type": "entity_drop", "chance": 0.5, "base_chance": 0.1 }
                  ],
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        assertEquals(Double.valueOf(0.5),
                report.definition().entityDropRules().get(0).condition().baseChance());
        assertTrue(report.messages().stream()
                .anyMatch(m -> m.severity() == LoreValidationMessage.Severity.WARNING
                        && m.message().contains("Both chance and base_chance")));
    }

    // --- Errors ---

    @Test
    void unknownAcquisitionTypeIsAnError() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "acquisition": [ { "type": "trading" } ],
                  "pages": ["A page."]
                }
                """);

        assertFalse(report.isLoaded());
        assertTrue(report.messages().stream()
                .anyMatch(m -> "$.acquisition[0].type".equals(m.jsonPath())));
    }

    @Test
    void invalidLootTableIdIsAnError() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "acquisition": [
                    { "type": "loot_table", "loot_tables": ["NOT A LOOT TABLE"] }
                  ],
                  "pages": ["A page."]
                }
                """);

        assertFalse(report.isLoaded());
        assertTrue(anyError(report));
    }

    @Test
    void invalidDeliveryIsAnError() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "acquisition": [
                    { "type": "advancement", "advancements": ["minecraft:story/root"], "delivery": "mail" }
                  ],
                  "pages": ["A page."]
                }
                """);

        assertFalse(report.isLoaded());
        assertTrue(anyError(report));
    }

    // --- New top-level metadata ---

    @Test
    void blankTagsAreDroppedAndTagsAreTrimmed() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "tags": ["  ruins  ", "   ", "war"],
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        assertEquals(List.of("ruins", "war"), report.definition().tags());
        assertTrue(report.messages().stream()
                .anyMatch(m -> m.severity() == LoreValidationMessage.Severity.WARNING
                        && m.message().contains("Blank tag")));
    }

    @Test
    void negativeSeriesOrderIsClamped() {
        LoreValidationReport report = parse("""
                {
                  "title": "T",
                  "series": "Ashen Chronicles",
                  "series_order": -3,
                  "pages": ["A page."]
                }
                """);

        assertTrue(report.isLoaded());
        assertEquals(0, report.definition().seriesOrder());
        assertEquals("Ashen Chronicles", report.definition().series());
        assertTrue(report.messages().stream()
                .anyMatch(m -> m.severity() == LoreValidationMessage.Severity.WARNING
                        && m.message().contains("series_order")));
    }
}
