package com.rpglore.config;

import com.google.gson.*;
import com.rpglore.RpgLoreMod;
import com.rpglore.lore.DropCondition;
import com.rpglore.lore.LoreBookDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Handles file I/O, JSON parsing, and default generation for lore book definitions.
 * The loaded book registry and tracking delegation live in {@link LoreBookRegistry}.
 */
public final class BooksConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_PAGES = 200;

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

        Files.writeString(dir.resolve("the_fallen_kingdom.json"), GSON.toJson(root), StandardCharsets.UTF_8);
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

    public static void reload() {
        Path booksDir = getBooksDir();
        if (!Files.isDirectory(booksDir)) {
            RpgLoreMod.LOGGER.warn("Books directory not found: {}", booksDir);
            LoreBookRegistry.setBooks(Map.of());
            return;
        }

        Map<String, LoreBookDefinition> newBooks = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(booksDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(file -> {
                        try {
                            LoreBookDefinition def = parseFile(file);
                            if (def != null) {
                                if (newBooks.containsKey(def.id())) {
                                    RpgLoreMod.LOGGER.warn("Duplicate lore book ID '{}' from file '{}', skipping",
                                            def.id(), file.getFileName());
                                } else {
                                    newBooks.put(def.id(), def);
                                }
                            }
                        } catch (Exception e) {
                            RpgLoreMod.LOGGER.warn("Failed to parse lore book file '{}': {}",
                                    file.getFileName(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            RpgLoreMod.LOGGER.error("Failed to scan books directory", e);
        }

        LoreBookRegistry.setBooks(newBooks);

        RpgLoreMod.LOGGER.info("Loaded {} lore book definition(s)", newBooks.size());
    }

    @Nullable
    private static LoreBookDefinition parseFile(Path file) {
        String filename = file.getFileName().toString();
        String fileId = filename.substring(0, filename.length() - ".json".length());

        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            RpgLoreMod.LOGGER.warn("Could not read lore book file '{}': {}", filename, e.getMessage());
            return null;
        }

        JsonObject root;
        try {
            root = GSON.fromJson(content, JsonObject.class);
        } catch (JsonSyntaxException e) {
            RpgLoreMod.LOGGER.warn("Invalid JSON in lore book file '{}': {}", filename, e.getMessage());
            return null;
        }

        if (root == null) {
            RpgLoreMod.LOGGER.warn("Empty lore book file: '{}'", filename);
            return null;
        }

        // id: use explicit field or derive from filename
        String id = getStringOrDefault(root, "id", "rpg_lore:" + fileId);

        // title (required)
        String title = getStringOrDefault(root, "title", "");
        if (title.isEmpty()) {
            RpgLoreMod.LOGGER.warn("Lore book '{}' has no title, skipping", filename);
            return null;
        }

        // M5: Warn on excessively long titles
        if (title.length() > 48) {
            RpgLoreMod.LOGGER.warn("Lore book '{}' has a very long title ({} chars), may display poorly",
                    filename, title.length());
        }

        String author = getStringOrDefault(root, "author", "Unknown");

        // H3: Validate and clamp generation to 0-3
        int generation = root.has("generation") ? root.get("generation").getAsInt() : 0;
        if (generation < 0 || generation > 3) {
            RpgLoreMod.LOGGER.warn("Lore book '{}' has invalid generation {}, clamping to 0-3", filename, generation);
            generation = Math.max(0, Math.min(3, generation));
        }

        double weight = root.has("weight") ? root.get("weight").getAsDouble() : 1.0;
        if (weight <= 0) {
            RpgLoreMod.LOGGER.warn("Lore book '{}' has invalid weight {}, clamping to 0.01", filename, weight);
            weight = 0.01;
        }

        // pages (required)
        if (!root.has("pages") || !root.get("pages").isJsonArray()) {
            RpgLoreMod.LOGGER.warn("Lore book '{}' has no pages array, skipping", filename);
            return null;
        }

        List<String> pages = new ArrayList<>();
        for (JsonElement elem : root.getAsJsonArray("pages")) {
            if (elem.isJsonPrimitive()) {
                String pageText = elem.getAsString();
                // M3: Use Gson for JSON escaping instead of manual implementation
                if (!pageText.trim().startsWith("{")) {
                    JsonObject comp = new JsonObject();
                    comp.addProperty("text", pageText);
                    pageText = GSON.toJson(comp);
                }
                pages.add(pageText);
            } else if (elem.isJsonObject()) {
                // Already a JSON text component object
                pages.add(GSON.toJson(elem));
            }
        }

        if (pages.isEmpty()) {
            RpgLoreMod.LOGGER.warn("Lore book '{}' has no valid pages, skipping", filename);
            return null;
        }

        // M4: Enforce page count limit
        if (pages.size() > MAX_PAGES) {
            RpgLoreMod.LOGGER.warn("Lore book '{}' has {} pages, truncating to {}",
                    filename, pages.size(), MAX_PAGES);
            pages = new ArrayList<>(pages.subList(0, MAX_PAGES));
        }

        // Aesthetic fields (all optional)
        String titleColor = parseHexColor(getStringOrDefault(root, "title_color", null), "title_color", filename);
        String authorColor = parseHexColor(getStringOrDefault(root, "author_color", null), "author_color", filename);
        String description = getStringOrDefault(root, "description", null);
        String descriptionColor = parseHexColor(getStringOrDefault(root, "description_color", null), "description_color", filename);
        boolean hideGeneration = root.has("hide_generation") && root.get("hide_generation").getAsBoolean();

        // E3: Per-book glint toggle (default: true for backward compatibility)
        boolean showGlint = !root.has("show_glint") || root.get("show_glint").getAsBoolean();

        // E4: Category field
        String category = getStringOrDefault(root, "category", null);

        // Codex exclusion
        boolean codexExclude = root.has("codex_exclude") && root.get("codex_exclude").getAsBoolean();

        // drop_conditions (optional)
        DropCondition dropCondition = parseDropCondition(root, filename);

        return new LoreBookDefinition(id, title, author, generation, weight, dropCondition, pages,
                titleColor, authorColor, description, descriptionColor, hideGeneration,
                showGlint, category, codexExclude);
    }

    private static DropCondition parseDropCondition(JsonObject root, String filename) {
        if (!root.has("drop_conditions") || !root.get("drop_conditions").isJsonObject()) {
            return DropCondition.defaultCondition();
        }

        JsonObject drop = root.getAsJsonObject("drop_conditions");

        List<ResourceLocation> mobTypes = parseResourceLocationList(drop, "mob_types");
        List<String> mobTags = parseStringList(drop, "mob_tags");
        List<ResourceLocation> biomes = parseResourceLocationList(drop, "biomes");
        List<String> biomeTags = parseStringList(drop, "biome_tags");
        List<ResourceLocation> dimensions = parseResourceLocationList(drop, "dimensions");

        Integer minY = drop.has("min_y") ? drop.get("min_y").getAsInt() : null;
        Integer maxY = drop.has("max_y") ? drop.get("max_y").getAsInt() : null;

        DropCondition.TimeFilter time = DropCondition.TimeFilter.ANY;
        if (drop.has("time")) {
            try {
                time = DropCondition.TimeFilter.valueOf(drop.get("time").getAsString().toUpperCase());
            } catch (IllegalArgumentException e) {
                RpgLoreMod.LOGGER.warn("Invalid time filter in '{}', using ANY", filename);
            }
        }

        DropCondition.WeatherFilter weather = DropCondition.WeatherFilter.ANY;
        if (drop.has("weather")) {
            try {
                weather = DropCondition.WeatherFilter.valueOf(drop.get("weather").getAsString().toUpperCase());
            } catch (IllegalArgumentException e) {
                RpgLoreMod.LOGGER.warn("Invalid weather filter in '{}', using ANY", filename);
            }
        }

        boolean requirePlayerKill = !drop.has("require_player_kill") || drop.get("require_player_kill").getAsBoolean();

        Double baseChance = drop.has("base_chance") ? drop.get("base_chance").getAsDouble() : null;

        // M9: Validate max_copies_per_player range
        int maxCopiesPerPlayer = drop.has("max_copies_per_player") ? drop.get("max_copies_per_player").getAsInt() : -1;
        if (maxCopiesPerPlayer < -1) {
            RpgLoreMod.LOGGER.warn("Lore book '{}' has invalid max_copies_per_player {}, using -1 (unlimited)",
                    filename, maxCopiesPerPlayer);
            maxCopiesPerPlayer = -1;
        }

        return new DropCondition(
                mobTypes, mobTags, biomes, biomeTags, dimensions,
                minY, maxY, time, weather,
                requirePlayerKill, baseChance, maxCopiesPerPlayer
        );
    }

    // --- Parsing helpers ---

    @Nullable
    private static List<ResourceLocation> parseResourceLocationList(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return null;
        List<ResourceLocation> list = new ArrayList<>();
        for (JsonElement elem : obj.getAsJsonArray(key)) {
            if (elem.isJsonPrimitive()) {
                String val = elem.getAsString();
                if (ResourceLocation.isValidResourceLocation(val)) {
                    list.add(new ResourceLocation(val));
                }
            }
        }
        return list.isEmpty() ? null : list;
    }

    @Nullable
    private static List<String> parseStringList(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return null;
        List<String> list = new ArrayList<>();
        for (JsonElement elem : obj.getAsJsonArray(key)) {
            if (elem.isJsonPrimitive()) {
                list.add(elem.getAsString());
            }
        }
        return list.isEmpty() ? null : list;
    }

    private static String getStringOrDefault(JsonObject obj, String key, String def) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : def;
    }

    @Nullable
    private static String parseHexColor(@Nullable String raw, String fieldName, String filename) {
        if (raw == null || raw.isEmpty()) return null;
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (!hex.matches("[0-9A-Fa-f]{6}")) {
            RpgLoreMod.LOGGER.warn("Invalid {} '{}' in '{}', ignoring", fieldName, raw, filename);
            return null;
        }
        return hex.toUpperCase();
    }

    private BooksConfigLoader() {}
}
