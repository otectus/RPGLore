package com.rpglore.config;

import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookParser;
import com.rpglore.lore.LoreBookSource;
import com.rpglore.lore.LoreValidationReport;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The loot-table and advancement lookup maps the registry rebuilds on every setBooks.
 */
class LoreBookRegistryAcquisitionTest {

    @BeforeEach
    @AfterEach
    void resetRegistry() {
        LoreBookRegistry.setBooks(Map.of());
    }

    private static LoreBookDefinition book(String filename, String acquisitionJson) {
        LoreBookSource source = LoreBookSource.ofConfigFile(filename);
        LoreValidationReport report = LoreBookParser.parse(
                "{\"title\": \"T\", \"acquisition\": [" + acquisitionJson + "], \"pages\": [\"A page.\"]}",
                source);
        assertTrue(report.isLoaded(), () -> "book did not load: " + report.messages());
        return report.definition();
    }

    @Test
    void lootTableRulesAreIndexedByEveryListedTable() {
        LoreBookDefinition def = book("chest_book.json",
                "{\"type\": \"loot_table\", \"loot_tables\": "
                        + "[\"minecraft:chests/simple_dungeon\", \"minecraft:chests/stronghold_library\"],"
                        + " \"chance\": 0.5, \"weight\": 2.0}");
        LoreBookRegistry.setBooks(Map.of(def.id(), def));

        var dungeon = LoreBookRegistry.getLootTableRules(
                new ResourceLocation("minecraft:chests/simple_dungeon"));
        assertEquals(1, dungeon.size());
        assertEquals(def.id(), dungeon.get(0).definition().id());
        assertEquals(0.5, dungeon.get(0).rule().chance());
        assertEquals(2.0, dungeon.get(0).rule().weight());

        assertEquals(1, LoreBookRegistry.getLootTableRules(
                new ResourceLocation("minecraft:chests/stronghold_library")).size());
        assertTrue(LoreBookRegistry.getLootTableRules(
                new ResourceLocation("minecraft:chests/igloo_chest")).isEmpty());
    }

    @Test
    void advancementRulesAreIndexedByAdvancementId() {
        LoreBookDefinition def = book("adv_book.json",
                "{\"type\": \"advancement\", \"advancements\": [\"minecraft:story/root\"],"
                        + " \"delivery\": \"inventory\"}");
        LoreBookRegistry.setBooks(Map.of(def.id(), def));

        var matches = LoreBookRegistry.getAdvancementRules(
                new ResourceLocation("minecraft:story/root"));
        assertEquals(1, matches.size());
        assertEquals(def.id(), matches.get(0).definition().id());
        assertTrue(LoreBookRegistry.getAdvancementRules(
                new ResourceLocation("minecraft:story/mine_stone")).isEmpty());
    }

    @Test
    void reloadingWithoutRulesClearsTheIndexes() {
        LoreBookDefinition def = book("chest_book.json",
                "{\"type\": \"loot_table\", \"loot_tables\": [\"minecraft:chests/simple_dungeon\"]}");
        LoreBookRegistry.setBooks(Map.of(def.id(), def));
        LoreBookRegistry.setBooks(Map.of());

        assertTrue(LoreBookRegistry.getLootTableRules(
                new ResourceLocation("minecraft:chests/simple_dungeon")).isEmpty());
    }
}
