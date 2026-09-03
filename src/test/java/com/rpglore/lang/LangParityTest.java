package com.rpglore.lang;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every locale must carry exactly the key set of en_us: a missing key renders as a
 * raw translation key in game, and a stale key is dead weight that hides a typo.
 */
class LangParityTest {

    private static final Path LANG_DIR = Path.of("src/main/resources/assets/rpg_lore/lang");
    private static final Gson GSON = new Gson();

    @Test
    void everyLocaleMatchesEnUs() throws IOException {
        // Gradle runs tests with the project root as the working directory
        assertTrue(Files.isDirectory(LANG_DIR),
                "Lang directory not found at " + LANG_DIR.toAbsolutePath()
                        + " (tests expect the project root as working directory)");

        Set<String> canonical = keysOf(LANG_DIR.resolve("en_us.json"));
        assertFalse(canonical.isEmpty(), "en_us.json has no keys");

        List<String> problems = new ArrayList<>();
        int checked = 0;

        try (Stream<Path> files = Files.list(LANG_DIR)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                String locale = file.getFileName().toString();
                if (locale.equals("en_us.json")) continue;
                checked++;

                Set<String> keys = keysOf(file);

                Set<String> missing = new TreeSet<>(canonical);
                missing.removeAll(keys);
                if (!missing.isEmpty()) {
                    problems.add(locale + ": missing keys " + missing);
                }

                Set<String> stale = new TreeSet<>(keys);
                stale.removeAll(canonical);
                if (!stale.isEmpty()) {
                    problems.add(locale + ": keys absent from en_us.json " + stale);
                }
            }
        }

        assertTrue(checked > 0, "No locale files found next to en_us.json");
        assertTrue(problems.isEmpty(), "Lang files out of parity with en_us.json:\n"
                + String.join("\n", problems));
    }

    private static Set<String> keysOf(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            assertNotNull(json, file + " is not a JSON object");
            return new TreeSet<>(json.keySet());
        }
    }
}
