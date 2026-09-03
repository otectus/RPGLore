package com.rpglore.lore;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure parser for lore book definitions. Produces a {@link LoreValidationReport} instead of
 * logging, so it can be unit tested and reused by both the config loader and a datapack listener.
 *
 * <p>Validation semantics match the pre-2.2.0 config loader: same defaults, same clamps,
 * same skip rules, same filename-derived id.
 */
public final class LoreBookParser {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_PAGES = 200;
    private static final int MAX_TITLE_LENGTH = 48;

    /** Only format_version 1 is understood by this build. */
    public static final int SUPPORTED_FORMAT_VERSION = 1;

    private static final Set<String> TOP_LEVEL_KEYS = Set.of(
            "format_version", "id", "title", "author", "generation", "weight", "pages",
            "title_color", "author_color", "description", "description_color",
            "hide_generation", "show_glint", "category", "codex_exclude", "drop_conditions");

    private static final Set<String> DROP_CONDITION_KEYS = Set.of(
            "mob_types", "mob_tags", "biomes", "biome_tags", "dimensions",
            "min_y", "max_y", "time", "weather",
            "require_player_kill", "base_chance", "max_copies_per_player");

    /** Extracts position and path out of a Gson syntax error message. */
    private static final Pattern GSON_POSITION =
            Pattern.compile("at line (\\d+) column (\\d+)(?: path (\\S+))?");

    private static final String PAGE_QUOTE_SUGGESTION = "escape quotes inside page text as \\\"";

    private LoreBookParser() {}

    // --- Entry points ---

    public static LoreValidationReport parse(String json, LoreBookSource source) {
        List<LoreValidationMessage> messages = new ArrayList<>();

        JsonElement root;
        try {
            root = readStrict(json);
        } catch (Exception strictFailure) {
            JsonElement lenient;
            try {
                lenient = JsonParser.parseString(json);
            } catch (Exception lenientFailure) {
                messages.add(syntaxError(lenientFailure, strictFailure, source));
                return new LoreValidationReport(null, messages);
            }
            messages.add(nonStrictWarning(strictFailure, source));
            root = lenient;
        }

        LoreValidationReport report = parse(root, source);
        messages.addAll(report.messages());
        return new LoreValidationReport(report.definition(), messages);
    }

    public static LoreValidationReport parse(@Nullable JsonElement element, LoreBookSource source) {
        Context ctx = new Context(source);

        if (element == null || element.isJsonNull()) {
            ctx.error(null, "Empty lore book definition. Book was not loaded.");
            return ctx.toReport(null);
        }
        if (!element.isJsonObject()) {
            ctx.error(null, "Lore book definition must be a JSON object. Book was not loaded.",
                    "an object", describe(element));
            return ctx.toReport(null);
        }

        JsonObject root = element.getAsJsonObject();

        checkFormatVersion(root, ctx);
        checkUnknownKeys(root, TOP_LEVEL_KEYS, "$", ctx);

        // id: explicit field or derived from the source filename
        String id = getStringOrDefault(root, "id", source.defaultId());
        if (!ResourceLocation.isValidResourceLocation(id)) {
            ctx.error("$.id", "Invalid lore book id '" + id + "'. Book was not loaded.",
                    "a valid resource location such as rpg_lore:my_book", "'" + id + "'");
            return ctx.toReport(null);
        }
        ctx.bookId = id;

        // title (required)
        String title = getStringOrDefault(root, "title", "");
        if (title.isEmpty()) {
            ctx.error("$.title", "Lore book has no title. Book was not loaded.");
        } else if (title.length() > MAX_TITLE_LENGTH) {
            ctx.warn("$.title", "Title is very long (" + title.length() + " chars) and may display poorly.");
        }

        String author = getStringOrDefault(root, "author", "Unknown");

        int generation = ctx.readInt(root, "generation", "$.generation", 0);
        if (generation < 0 || generation > 3) {
            ctx.warn("$.generation", "Invalid generation " + generation + ", clamping to 0-3.");
            generation = Math.max(0, Math.min(3, generation));
        }

        double weight = ctx.readDouble(root, "weight", "$.weight", 1.0);
        if (weight <= 0) {
            ctx.warn("$.weight", "Invalid weight " + weight + ", clamping to 0.01.");
            weight = 0.01;
        }

        List<String> pages = readPages(root, ctx);

        String titleColor = readColor(root, "title_color", ctx);
        String authorColor = readColor(root, "author_color", ctx);
        String description = getStringOrDefault(root, "description", null);
        String descriptionColor = readColor(root, "description_color", ctx);
        boolean hideGeneration = ctx.readBoolean(root, "hide_generation", "$.hide_generation", false);
        // Per-book glint toggle defaults to true for backward compatibility.
        boolean showGlint = ctx.readBoolean(root, "show_glint", "$.show_glint", true);
        String category = getStringOrDefault(root, "category", null);
        boolean codexExclude = ctx.readBoolean(root, "codex_exclude", "$.codex_exclude", false);

        DropCondition dropCondition = readDropCondition(root, ctx);

        if (ctx.fatal || pages == null) {
            return ctx.toReport(null);
        }

        LoreBookDefinition def = new LoreBookDefinition(id, title, author, generation, weight,
                dropCondition, pages, titleColor, authorColor, description, descriptionColor,
                hideGeneration, showGlint, category, codexExclude);
        return ctx.toReport(def);
    }

    // --- Structural checks ---

    private static void checkFormatVersion(JsonObject root, Context ctx) {
        if (!root.has("format_version")) return;

        JsonElement el = root.get("format_version");
        Integer version = null;
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            try {
                version = el.getAsInt();
            } catch (RuntimeException ignored) {
                // reported below as an unknown version
            }
        }
        if (version == null || version != SUPPORTED_FORMAT_VERSION) {
            String shown = version != null ? String.valueOf(version) : el.toString();
            ctx.error("$.format_version",
                    "Unknown format_version " + shown + "; this build supports "
                            + SUPPORTED_FORMAT_VERSION + ". Book was not loaded.");
        }
    }

    private static void checkUnknownKeys(JsonObject obj, Set<String> known, String pathPrefix, Context ctx) {
        for (String key : obj.keySet()) {
            if (known.contains(key)) continue;
            // Keys starting with '_' or containing ':' are reserved as extension namespaces.
            if (key.startsWith("_") || key.contains(":")) continue;

            String nearest = nearestKey(key, known);
            LoreValidationMessage msg = LoreValidationMessage.warning(ctx.source.displayPath(), ctx.bookId,
                    pathPrefix + "." + key, "Unknown key '" + key + "' was ignored.");
            if (nearest != null) {
                msg = msg.withSuggestion("Did you mean \"" + nearest + "\"?");
            }
            ctx.messages.add(msg);
        }
    }

    @Nullable
    private static String nearestKey(String key, Set<String> known) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : known) {
            int d = levenshtein(key, candidate);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return bestDistance <= 2 ? best : null;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[b.length()];
    }

    // --- Field readers ---

    @Nullable
    private static List<String> readPages(JsonObject root, Context ctx) {
        if (!root.has("pages") || !root.get("pages").isJsonArray()) {
            ctx.error("$.pages", "Lore book has no pages array. Book was not loaded.",
                    "an array of page strings", root.has("pages") ? describe(root.get("pages")) : "nothing");
            return null;
        }

        List<String> pages = new ArrayList<>();
        JsonArray array = root.getAsJsonArray("pages");
        for (int i = 0; i < array.size(); i++) {
            JsonElement elem = array.get(i);
            String path = "$.pages[" + i + "]";
            if (elem.isJsonPrimitive()) {
                String pageText = elem.getAsString();
                if (!pageText.trim().startsWith("{")) {
                    JsonObject comp = new JsonObject();
                    comp.addProperty("text", pageText);
                    pageText = GSON.toJson(comp);
                } else if (!isWellFormedJson(pageText)) {
                    // Preserved from 2.1.x: the raw text is still kept as the page body.
                    ctx.messages.add(LoreValidationMessage.warning(ctx.source.displayPath(), ctx.bookId, path,
                                    "Page text looks like a JSON text component but is not well-formed JSON.")
                            .withSuggestion(PAGE_QUOTE_SUGGESTION));
                }
                pages.add(pageText);
            } else if (elem.isJsonObject()) {
                pages.add(GSON.toJson(elem));
            } else {
                ctx.warn(path, "Page is neither a string nor a text component object, skipping it.",
                        "a string or object", describe(elem));
            }
        }

        if (pages.isEmpty()) {
            ctx.error("$.pages", "Lore book has no valid pages. Book was not loaded.");
            return null;
        }

        if (pages.size() > MAX_PAGES) {
            ctx.warn("$.pages", "Lore book has " + pages.size() + " pages, truncating to " + MAX_PAGES + ".");
            pages = new ArrayList<>(pages.subList(0, MAX_PAGES));
        }
        return pages;
    }

    /** Well-formedness only: page components are resolved to Components at item build time. */
    private static boolean isWellFormedJson(String text) {
        try {
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setLenient(false);
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed != null && reader.peek() == JsonToken.END_DOCUMENT;
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    private static String readColor(JsonObject root, String key, Context ctx) {
        String raw = getStringOrDefault(root, key, null);
        if (raw == null || raw.isEmpty()) return null;
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (!hex.matches("[0-9A-Fa-f]{6}")) {
            ctx.warn("$." + key, "Invalid " + key + " '" + raw + "', ignoring it.",
                    "six hexadecimal digits such as FFD700", "'" + raw + "'");
            return null;
        }
        return hex.toUpperCase(Locale.ROOT);
    }

    private static DropCondition readDropCondition(JsonObject root, Context ctx) {
        if (!root.has("drop_conditions")) {
            return DropCondition.defaultCondition();
        }
        if (!root.get("drop_conditions").isJsonObject()) {
            ctx.warn("$.drop_conditions", "drop_conditions is not an object, using defaults.",
                    "an object", describe(root.get("drop_conditions")));
            return DropCondition.defaultCondition();
        }

        JsonObject drop = root.getAsJsonObject("drop_conditions");
        checkUnknownKeys(drop, DROP_CONDITION_KEYS, "$.drop_conditions", ctx);

        List<ResourceLocation> mobTypes = readResourceLocationList(drop, "mob_types");
        List<String> mobTags = readStringList(drop, "mob_tags");
        List<ResourceLocation> biomes = readResourceLocationList(drop, "biomes");
        List<String> biomeTags = readStringList(drop, "biome_tags");
        List<ResourceLocation> dimensions = readResourceLocationList(drop, "dimensions");

        Integer minY = drop.has("min_y") ? ctx.readInt(drop, "min_y", "$.drop_conditions.min_y", 0) : null;
        Integer maxY = drop.has("max_y") ? ctx.readInt(drop, "max_y", "$.drop_conditions.max_y", 0) : null;

        if (minY != null && maxY != null && minY > maxY) {
            ctx.warn("$.drop_conditions.min_y",
                    "min_y (" + minY + ") is greater than max_y (" + maxY + "), swapping values.");
            Integer temp = minY;
            minY = maxY;
            maxY = temp;
        }

        DropCondition.TimeFilter time = DropCondition.TimeFilter.ANY;
        if (drop.has("time")) {
            String raw = ctx.readString(drop, "time", "$.drop_conditions.time");
            if (raw != null) {
                try {
                    time = DropCondition.TimeFilter.valueOf(raw.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    ctx.warn("$.drop_conditions.time", "Invalid time filter '" + raw + "', using ANY.",
                            "one of ANY, DAY_ONLY, NIGHT_ONLY", "'" + raw + "'");
                }
            }
        }

        DropCondition.WeatherFilter weather = DropCondition.WeatherFilter.ANY;
        if (drop.has("weather")) {
            String raw = ctx.readString(drop, "weather", "$.drop_conditions.weather");
            if (raw != null) {
                try {
                    weather = DropCondition.WeatherFilter.valueOf(raw.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    ctx.warn("$.drop_conditions.weather", "Invalid weather filter '" + raw + "', using ANY.",
                            "one of ANY, CLEAR_ONLY, RAIN_ONLY, THUNDER_ONLY", "'" + raw + "'");
                }
            }
        }

        boolean requirePlayerKill = ctx.readBoolean(drop, "require_player_kill",
                "$.drop_conditions.require_player_kill", true);

        Double baseChance = drop.has("base_chance")
                ? ctx.readDouble(drop, "base_chance", "$.drop_conditions.base_chance", 0.0) : null;
        if (baseChance != null && (baseChance < 0.0 || baseChance > 1.0)) {
            ctx.warn("$.drop_conditions.base_chance",
                    "base_chance " + baseChance + " is outside [0.0, 1.0], clamping.");
            baseChance = Math.max(0.0, Math.min(1.0, baseChance));
        }

        int maxCopiesPerPlayer = ctx.readInt(drop, "max_copies_per_player",
                "$.drop_conditions.max_copies_per_player", -1);
        if (maxCopiesPerPlayer < -1) {
            ctx.warn("$.drop_conditions.max_copies_per_player",
                    "Invalid max_copies_per_player " + maxCopiesPerPlayer + ", using -1 (unlimited).");
            maxCopiesPerPlayer = -1;
        }

        return new DropCondition(mobTypes, mobTags, biomes, biomeTags, dimensions,
                minY, maxY, time, weather, requirePlayerKill, baseChance, maxCopiesPerPlayer);
    }

    @Nullable
    private static List<ResourceLocation> readResourceLocationList(JsonObject obj, String key) {
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
    private static List<String> readStringList(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return null;
        List<String> list = new ArrayList<>();
        for (JsonElement elem : obj.getAsJsonArray(key)) {
            if (elem.isJsonPrimitive()) {
                list.add(elem.getAsString());
            }
        }
        return list.isEmpty() ? null : list;
    }

    @Nullable
    private static String getStringOrDefault(JsonObject obj, String key, @Nullable String def) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : def;
    }

    // --- Syntax diagnostics ---

    private static LoreValidationMessage syntaxError(Exception lenientFailure, Exception strictFailure,
                                                     LoreBookSource source) {
        int line = LoreValidationMessage.UNKNOWN_POSITION;
        int column = LoreValidationMessage.UNKNOWN_POSITION;
        String path = null;

        for (Throwable t : causeChain(lenientFailure, strictFailure)) {
            String text = t.getMessage();
            if (text == null) continue;
            Matcher m = GSON_POSITION.matcher(text);
            if (m.find()) {
                line = Integer.parseInt(m.group(1));
                column = Integer.parseInt(m.group(2));
                path = m.group(3);
                break;
            }
        }

        String jsonPath = normalizeGsonPath(path);
        String detail = rootMessage(lenientFailure);
        String suggestion = suggestionFor(detail, jsonPath);

        return LoreValidationMessage.of(LoreValidationMessage.Severity.ERROR, source.displayPath(), null,
                jsonPath, "Invalid JSON: " + detail + ". Book was not loaded.",
                null, null, line, column, suggestion);
    }

    private static LoreValidationMessage nonStrictWarning(Exception strictFailure, LoreBookSource source) {
        int line = LoreValidationMessage.UNKNOWN_POSITION;
        int column = LoreValidationMessage.UNKNOWN_POSITION;
        String path = null;

        for (Throwable t : causeChain(strictFailure)) {
            String text = t.getMessage();
            if (text == null) continue;
            Matcher m = GSON_POSITION.matcher(text);
            if (m.find()) {
                line = Integer.parseInt(m.group(1));
                column = Integer.parseInt(m.group(2));
                path = m.group(3);
                break;
            }
        }

        return LoreValidationMessage.of(LoreValidationMessage.Severity.WARNING, source.displayPath(), null,
                normalizeGsonPath(path),
                "File uses non-strict JSON (" + rootMessage(strictFailure) + "); it was accepted in lenient mode.",
                null, null, line, column,
                "write strict JSON: quoted keys, no comments, no trailing commas");
    }

    @Nullable
    private static String suggestionFor(String detail, @Nullable String jsonPath) {
        boolean quoteProblem = detail.contains("Unterminated") || detail.contains("Expected")
                || detail.contains("expected");
        if (quoteProblem && jsonPath != null && jsonPath.startsWith("$.pages")) {
            return PAGE_QUOTE_SUGGESTION;
        }
        return null;
    }

    @Nullable
    private static String normalizeGsonPath(@Nullable String path) {
        if (path == null || path.isEmpty()) return null;
        String cleaned = path.endsWith(",") ? path.substring(0, path.length() - 1) : path;
        return cleaned.startsWith("$") ? cleaned : "$." + cleaned;
    }

    private static String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String text = current.getMessage();
        if (text == null || text.isEmpty()) return current.getClass().getSimpleName();
        Matcher m = GSON_POSITION.matcher(text);
        return m.find() ? text.substring(0, m.start()).trim() : text.trim();
    }

    private static List<Throwable> causeChain(Throwable... roots) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable root : roots) {
            Throwable current = root;
            while (current != null && !chain.contains(current)) {
                chain.add(current);
                current = current.getCause();
            }
        }
        return chain;
    }

    private static JsonElement readStrict(String json) throws IOException {
        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setLenient(false);
        JsonElement element = JsonParser.parseReader(reader);
        if (reader.peek() != JsonToken.END_DOCUMENT) {
            throw new JsonSyntaxException("Trailing content after the JSON document");
        }
        return element;
    }

    private static String describe(JsonElement element) {
        if (element.isJsonNull()) return "null";
        if (element.isJsonArray()) return "an array";
        if (element.isJsonObject()) return "an object";
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (p.isBoolean()) return "a boolean";
        if (p.isNumber()) return "a number";
        return "the string " + p.toString();
    }

    // --- Parse context ---

    /** Collects messages for one source and tracks whether the book must be skipped. */
    private static final class Context {
        private final LoreBookSource source;
        private final List<LoreValidationMessage> messages = new ArrayList<>();
        @Nullable
        private String bookId;
        private boolean fatal;

        private Context(LoreBookSource source) {
            this.source = source;
        }

        private void error(@Nullable String path, String message) {
            fatal = true;
            messages.add(LoreValidationMessage.error(source.displayPath(), bookId, path, message));
        }

        private void error(@Nullable String path, String message, String expected, String actual) {
            fatal = true;
            messages.add(LoreValidationMessage.error(source.displayPath(), bookId, path, message, expected, actual));
        }

        private void warn(@Nullable String path, String message) {
            messages.add(LoreValidationMessage.warning(source.displayPath(), bookId, path, message));
        }

        private void warn(@Nullable String path, String message, String expected, String actual) {
            messages.add(LoreValidationMessage.warning(source.displayPath(), bookId, path, message, expected, actual));
        }

        private LoreValidationReport toReport(@Nullable LoreBookDefinition def) {
            return new LoreValidationReport(fatal ? null : def, List.copyOf(messages));
        }

        // The numeric and boolean readers mirror 2.1.x, which called getAsInt/getAsDouble/getAsBoolean
        // directly: a value of the wrong shape threw and the whole book was skipped.
        private int readInt(JsonObject obj, String key, String path, int def) {
            if (!obj.has(key)) return def;
            try {
                return obj.get(key).getAsInt();
            } catch (RuntimeException e) {
                error(path, "Field " + key + " is not an integer. Book was not loaded.",
                        "an integer", describe(obj.get(key)));
                return def;
            }
        }

        private double readDouble(JsonObject obj, String key, String path, double def) {
            if (!obj.has(key)) return def;
            try {
                return obj.get(key).getAsDouble();
            } catch (RuntimeException e) {
                error(path, "Field " + key + " is not a number. Book was not loaded.",
                        "a number", describe(obj.get(key)));
                return def;
            }
        }

        private boolean readBoolean(JsonObject obj, String key, String path, boolean def) {
            if (!obj.has(key)) return def;
            try {
                return obj.get(key).getAsBoolean();
            } catch (RuntimeException e) {
                error(path, "Field " + key + " is not a boolean. Book was not loaded.",
                        "true or false", describe(obj.get(key)));
                return def;
            }
        }

        @Nullable
        private String readString(JsonObject obj, String key, String path) {
            if (!obj.has(key)) return null;
            try {
                return obj.get(key).getAsString();
            } catch (RuntimeException e) {
                error(path, "Field " + key + " is not a string. Book was not loaded.",
                        "a string", describe(obj.get(key)));
                return null;
            }
        }
    }
}
