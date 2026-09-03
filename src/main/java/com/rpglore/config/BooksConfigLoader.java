package com.rpglore.config;

import com.google.gson.*;
import com.rpglore.RpgLoreMod;
import com.rpglore.lore.LoreBookDefinition;
import com.rpglore.lore.LoreBookParser;
import com.rpglore.lore.LoreBookSource;
import com.rpglore.lore.LoreValidationMessage;
import com.rpglore.lore.LoreValidationReport;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Handles file I/O and default generation for lore book definitions.
 * Parsing and validation live in {@link LoreBookParser}; the loaded book registry
 * and tracking delegation live in {@link LoreBookRegistry}.
 */
public final class BooksConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIRECTORY_SOURCE = "config/rpg_lore/books";

    public static Path getBooksDir() {
        return FMLPaths.CONFIGDIR.get().resolve("rpg_lore/books");
    }

    private static Path getMarkerFile() {
        return FMLPaths.CONFIGDIR.get().resolve("rpg_lore/.defaults_generated");
    }

    // --- Default generation ---

    public static void ensureDefaults() {
        Path marker = getMarkerFile();
        if (Files.exists(marker)) return;

        Path booksDir = getBooksDir();
        try {
            Files.createDirectories(booksDir);

            writeDefaultBook(booksDir);
            writeReadme(booksDir);
            Files.createFile(marker);

            RpgLoreMod.LOGGER.info("Generated default lore book example in {}", booksDir);
        } catch (IOException e) {
            RpgLoreMod.LOGGER.error("Failed to generate default lore book files", e);
        }
    }

    private static void writeDefaultBook(Path dir) throws IOException {
        Files.writeString(dir.resolve("the_fallen_kingdom.json"), defaultBookJson(), StandardCharsets.UTF_8);
    }

    /** The example book written on first launch. Exposed so tests can parse the shipped default. */
    public static String defaultBookJson() {
        JsonObject root = new JsonObject();
        root.addProperty("title", "The Fallen Kingdom");
        root.addProperty("author", "Unknown");
        root.addProperty("generation", 0);
        root.addProperty("weight", 1.0);
        root.addProperty("title_color", "FFD700");
        root.addProperty("description", "A weathered tome recounting the fall of a forgotten realm.");

        JsonObject drop = new JsonObject();
        drop.addProperty("require_player_kill", true);
        root.add("drop_conditions", drop);

        JsonArray pages = new JsonArray();
        pages.add("{\"text\":\"In ages past, a great kingdom\\nstood where only ruins remain.\\n\\nIts people were wise, its walls\\nwere strong, yet it fell all\\nthe same.\"}");
        pages.add("{\"text\":\"Some say a creeping darkness\\nconsumed it from within.\\nOthers speak of betrayal\\nby those closest to the throne.\\n\\nThe truth, as always, lies\\nsomewhere in between.\"}");
        root.add("pages", pages);

        return GSON.toJson(root);
    }

    private static void writeReadme(Path dir) throws IOException {
        String readme = """
                === RPG Lore — Book Definition Format ===

                Each .json file in this folder defines one lore book.
                The filename (without .json) becomes the book's internal ID
                (prefixed with rpg_lore:).

                --- Fields ---

                title             (string)  Book title displayed in-game
                author            (string)  Author name shown on the book. Default: "Unknown"
                generation        (int)     0 = Original, 1 = Copy, 2 = Copy of Copy, 3 = Tattered
                weight            (number)  Selection weight when multiple books match. Higher = more likely. Default: 1.0
                pages             (array)   List of page strings. Each string is a JSON text component
                                            (e.g. {"text":"Hello"}) or a plain string that will be auto-wrapped.

                --- Appearance (all optional) ---

                title_color       (string)  Hex color for the title in the tooltip, e.g. "FFD700" for gold. Default: yellow
                author_color      (string)  Hex color for the author in the tooltip. Default: gray
                description       (string)  Brief description shown below the author in the tooltip
                description_color (string)  Hex color for the description text. Default: aqua
                hide_generation   (bool)    If true, the generation label (Original, Copy, etc.) is hidden. Default: false
                show_glint        (bool)    If true, the book has an enchantment glint effect. Default: true
                category          (string)  Optional category for grouping books in the Codex (e.g. "History", "Mythology")
                codex_exclude     (bool)    If true, this book will not appear in the Lore Codex. Default: false

                --- Drop Conditions (all optional) ---

                Nested inside a "drop_conditions" object. Omitted fields match everything.

                require_player_kill   (bool)    Must the mob be killed by a player? Default: true
                base_chance           (number)  Override the global drop chance (0.0 to 1.0) for this book
                max_copies_per_player (int)     Max times a player can receive this book. -1 = unlimited. Default: -1
                mob_types             (array)   Entity type IDs that can drop this book, e.g. ["minecraft:zombie"]
                mob_tags              (array)   Entity type tags, e.g. ["minecraft:undead"]
                biomes                (array)   Biome IDs, e.g. ["minecraft:plains"]
                biome_tags            (array)   Biome tags, e.g. ["minecraft:is_overworld"]
                dimensions            (array)   Dimension IDs, e.g. ["minecraft:overworld"]
                min_y                 (int)     Minimum Y coordinate for the kill location
                max_y                 (int)     Maximum Y coordinate for the kill location
                time                  (string)  "ANY", "DAY_ONLY", or "NIGHT_ONLY"
                weather               (string)  "ANY", "CLEAR_ONLY", "RAIN_ONLY", or "THUNDER_ONLY"

                --- Example ---

                {
                  "title": "The Ancient Battle",
                  "author": "Unknown Chronicler",
                  "generation": 0,
                  "weight": 5,
                  "title_color": "FFD700",
                  "description": "A faded account of a great battle fought long ago.",
                  "category": "History",
                  "drop_conditions": {
                    "mob_types": ["minecraft:zombie", "minecraft:skeleton"],
                    "biome_tags": ["minecraft:is_overworld"],
                    "time": "NIGHT_ONLY"
                  },
                  "pages": [
                    "{\\"text\\":\\"Long ago, in these very fields...\\"}",
                    "{\\"text\\":\\"The battle raged for three days...\\"}"
                  ]
                }
                """;
        Files.writeString(dir.resolve("_README.txt"), readme, StandardCharsets.UTF_8);
    }

    // --- Loading ---

    /**
     * Rescans the books directory and swaps in the new catalog.
     * The returned report describes what changed and carries every parse diagnostic.
     */
    public static LoreReloadReport reload() {
        Path booksDir = getBooksDir();
        Map<String, LoreBookDefinition> previous = snapshot();

        if (!Files.isDirectory(booksDir)) {
            if (!previous.isEmpty()) {
                // The directory vanished under a catalog that was already live: keep what we have.
                LoreValidationMessage msg = LoreValidationMessage.error(DIRECTORY_SOURCE, null, null,
                        "Books directory not found: " + booksDir + ". Previous catalog remains active.");
                RpgLoreMod.LOGGER.error(msg.format());
                return LoreReloadReport.catastrophic(previous.size(), List.of(msg));
            }
            LoreValidationMessage msg = LoreValidationMessage.warning(DIRECTORY_SOURCE, null, null,
                    "Books directory not found: " + booksDir);
            RpgLoreMod.LOGGER.warn(msg.format());
            LoreBookRegistry.setBooks(Map.of());
            return new LoreReloadReport(0, 0, 0, 0, 1, 0, 0, false, List.of(msg));
        }

        List<Path> files;
        try {
            files = listBookFiles(booksDir);
        } catch (IOException e) {
            LoreValidationMessage msg = LoreValidationMessage.error(DIRECTORY_SOURCE, null, null,
                    "Failed to scan books directory: " + e.getMessage() + ". Previous catalog remains active.");
            RpgLoreMod.LOGGER.error(msg.format());
            return LoreReloadReport.catastrophic(previous.size(), List.of(msg));
        }

        Map<String, LoreBookDefinition> newBooks = new LinkedHashMap<>();
        List<LoreValidationMessage> messages = new ArrayList<>();

        for (Path file : files) {
            LoreValidationReport report = parseFile(file);
            messages.addAll(report.messages());

            LoreBookDefinition def = report.definition();
            if (def == null) continue;

            if (newBooks.containsKey(def.id())) {
                // Duplicate ids keep the first file in sorted order, as they always have.
                messages.add(LoreValidationMessage.warning(
                        LoreBookSource.ofConfigFile(file.getFileName().toString()).displayPath(),
                        def.id(), null,
                        "Duplicate lore book ID '" + def.id() + "', this file was skipped."));
            } else {
                newBooks.put(def.id(), def);
            }
        }

        for (LoreValidationMessage msg : messages) {
            log(msg);
        }

        LoreBookRegistry.setBooks(newBooks);

        int added = 0;
        int changed = 0;
        for (Map.Entry<String, LoreBookDefinition> entry : newBooks.entrySet()) {
            LoreBookDefinition old = previous.get(entry.getKey());
            if (old == null) {
                added++;
            } else if (!old.equals(entry.getValue())) {
                changed++;
            }
        }
        int removed = 0;
        for (String id : previous.keySet()) {
            if (!newBooks.containsKey(id)) removed++;
        }

        int warnings = count(messages, LoreValidationMessage.Severity.WARNING);
        int errors = count(messages, LoreValidationMessage.Severity.ERROR);

        RpgLoreMod.LOGGER.info("Loaded {} lore book definition(s)", newBooks.size());

        return new LoreReloadReport(newBooks.size(), added, changed, removed,
                warnings, errors, 0, false, List.copyOf(messages));
    }

    /** Re-runs the parser over every book file without touching the live registry. */
    public static List<LoreValidationReport> validateAll() {
        Path booksDir = getBooksDir();
        if (!Files.isDirectory(booksDir)) return List.of();

        List<Path> files;
        try {
            files = listBookFiles(booksDir);
        } catch (IOException e) {
            return List.of(new LoreValidationReport(null, List.of(LoreValidationMessage.error(
                    DIRECTORY_SOURCE, null, null,
                    "Failed to scan books directory: " + e.getMessage()))));
        }

        List<LoreValidationReport> reports = new ArrayList<>();
        for (Path file : files) {
            reports.add(parseFile(file));
        }
        return reports;
    }

    /** Re-parses just the source that produces the given book id, if one exists. */
    public static Optional<LoreValidationReport> validateOne(String bookId) {
        for (LoreValidationReport report : validateAll()) {
            LoreBookDefinition def = report.definition();
            if (def != null && def.id().equals(bookId)) {
                return Optional.of(report);
            }
            for (LoreValidationMessage msg : report.messages()) {
                if (bookId.equals(msg.bookId())) {
                    return Optional.of(report);
                }
            }
        }
        return Optional.empty();
    }

    private static List<Path> listBookFiles(Path booksDir) throws IOException {
        try (Stream<Path> files = Files.list(booksDir)) {
            return files.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    private static LoreValidationReport parseFile(Path file) {
        String filename = file.getFileName().toString();
        LoreBookSource source = LoreBookSource.ofConfigFile(filename);

        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new LoreValidationReport(null, List.of(LoreValidationMessage.error(
                    source.displayPath(), null, null,
                    "Could not read lore book file: " + e.getMessage() + ". Book was not loaded.")));
        }

        try {
            return LoreBookParser.parse(content, source);
        } catch (RuntimeException e) {
            return new LoreValidationReport(null, List.of(LoreValidationMessage.error(
                    source.displayPath(), null, null,
                    "Failed to parse lore book file: " + e.getMessage() + ". Book was not loaded.")));
        }
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

    private static void log(LoreValidationMessage msg) {
        switch (msg.severity()) {
            case ERROR -> RpgLoreMod.LOGGER.error(msg.format());
            case WARNING -> RpgLoreMod.LOGGER.warn(msg.format());
            case INFO -> RpgLoreMod.LOGGER.info(msg.format());
        }
    }

    private BooksConfigLoader() {}
}
