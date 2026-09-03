package com.rpglore.lore;

/**
 * Identifies where a lore book definition came from, for diagnostics and id derivation.
 *
 * @param kind        origin of the definition
 * @param displayPath human-readable path shown in validation messages
 * @param defaultId   id used when the JSON omits an explicit "id" field
 */
public record LoreBookSource(SourceKind kind, String displayPath, String defaultId) {

    public enum SourceKind { CONFIG, DATAPACK }

    /**
     * Builds a source for a config file. The default id reproduces the historic rule:
     * the filename without its .json extension, prefixed with "rpg_lore:".
     */
    public static LoreBookSource ofConfigFile(String filename) {
        String fileId = filename.endsWith(".json")
                ? filename.substring(0, filename.length() - ".json".length())
                : filename;
        return new LoreBookSource(SourceKind.CONFIG, "config/rpg_lore/books/" + filename, "rpg_lore:" + fileId);
    }
}
