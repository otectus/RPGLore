# RPG Lore

A data-driven lore book mod for Minecraft Forge 1.20.1. Define custom books via simple JSON files and have them drop from mobs based on configurable conditions. Collect them all in the Lore Codex.

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)
![Forge](https://img.shields.io/badge/Forge-47.3.0+-orange)
![Java](https://img.shields.io/badge/Java-17-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

## Features

- **JSON-defined lore books** -- add, edit, or remove books without recompiling. Each `.json` file in the config folder becomes a lore book.
- **Multiple acquisition routes** -- books drop from mobs, inject into named loot tables (chests, fishing, gameplay), and reward advancements. Rules are additive, so the same book can acquire through any or all methods.
- **Conditional drops** -- control which mobs drop which books based on entity type, biome, dimension, time of day, weather, Y-level, and more. Loot-table injection has configurable chance and weight per rule.
- **Lore Codex** -- a soul-bound collection browser that stores your lore books directly. Books go into the Codex on pickup (not your inventory); duplicates are banked as extractable spare copies. Browse with search, category filters, favorite marking, and read state tracking.
- **Codex browser features** -- search by title, author, category, and tags; organize by category with progress counters; filter by Collected, Unread, Favorites, or Missing; sort by Default, Title, Category, or Recently found; keyboard navigation with arrow keys and Enter; all view state is preserved when you return from reading a book.
- **Auto-generated title page** -- every lore book opens with a stylized title page showing the book's title (auto-scaled to fit two lines, bold, colored) and author (bold, colored), both centered on the page.
- **Custom book GUI** -- lore books use a unique book texture distinct from vanilla written books.
- **Interactive text in pages** -- hover tooltips and click events (`suggest_command`, `copy_to_clipboard`, `open_url`, `change_page`) work in page text; `run_command` is configurable for pack-author control.
- **Tooltip styling** -- bold colored title, bold colored author, italic description, series position, and generation label with automatic formatting.
- **Configurable appearance** -- per-book title color, author color, description, glint toggle, category, and searchable tags via the JSON definition.
- **Enchantment glint** -- lore books shimmer with an enchantment glint by default (configurable per-book with `show_glint`).
- **Curios API support** -- optionally equip the Codex in a dedicated Curios slot (soft dependency; works without Curios installed).
- **In-game commands** -- give books, reload configs, list books, view your collection, validate definitions, and manage Codex data.
- **Per-player copy limits** -- optionally restrict how many times a player can receive a specific book via `drop_conditions`.
- **Looting scaling** -- optionally increase drop chance with the Looting enchantment.
- **60 languages** -- localized in English, Chinese, Spanish, Hindi, Arabic, French, German, Russian, Japanese, Korean, and 50 more.

## Requirements

- Minecraft 1.20.1
- Forge 47.3.0+
- Java 17
- Curios API 5.4.7+ (optional -- enables a dedicated Codex equipment slot)

## Getting Started

1. Install the mod in your `mods/` folder.
2. Start the server (or singleplayer world) once. The mod creates:
   - `config/rpg_lore/server.toml` -- server settings (drops + Codex)
   - `config/rpg_lore/client.toml` -- client settings
   - `config/rpg_lore/books/` -- lore book definitions folder with an example book and a `_README.txt`
3. Add your own `.json` files to `config/rpg_lore/books/`.
4. Use `/rpglore reload` in-game to hot-reload book definitions.
5. Players receive a Lore Codex on first join (configurable). Right-click to browse your collection.

## Book Definition Format

Each `.json` file in `config/rpg_lore/books/` defines one lore book. The filename (minus `.json`) becomes the book's internal ID, prefixed with `rpg_lore:`.

### Fields

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `format_version` | int | No | `1` | Schema version; only 1 is supported |
| `id` | string | No | *(filename)* | Override the book's registry ID (otherwise derived from filename) |
| `title` | string | Yes | -- | Book title displayed in-game |
| `author` | string | No | `"Unknown"` | Author name |
| `generation` | int | No | `0` | 0 = Original, 1 = Copy, 2 = Copy of Copy, 3 = Tattered |
| `weight` | number | No | `1.0` | Selection weight when multiple books match a drop |
| `pages` | array | Yes | -- | Page content (JSON text components or plain strings) |
| `acquisition` | array | No | *(none)* | Array of acquisition rule objects (entity_drop, loot_table, advancement) |
| `tags` | array | No | *(none)* | Searchable keywords for Codex search (blank entries dropped) |
| `discovery_hint` | string | No | *(none)* | Hint shown on uncollected Codex entries when discovery hints are enabled |
| `series` | string | No | *(none)* | Series name shown in the Codex entry tooltip |
| `series_order` | int | No | `0` | Position within the series (negative values clamp to 0) |

### Appearance Fields (all optional)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `title_color` | string | `"FFFF55"` (yellow) | Hex color for the title |
| `author_color` | string | `"AAAAAA"` (gray) | Hex color for the author |
| `description` | string | *(none)* | Brief description shown in the tooltip |
| `description_color` | string | `"55FFFF"` (aqua) | Hex color for the description |
| `hide_generation` | bool | `false` | Suppress the generation label in the tooltip |
| `show_glint` | bool | `true` | Show enchantment glint on the item |
| `category` | string | *(none)* | Category for grouping books in the Codex (e.g. `"History"`) |
| `codex_exclude` | bool | `false` | Exclude this book from the Lore Codex |

### Drop Conditions (all optional)

Nested inside a `"drop_conditions"` object at the book's top level (legacy). Omitted fields match everything. **Note:** `drop_conditions` is an alternative to the `acquisition` array; books with neither get a default entity_drop rule for backward compatibility.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `require_player_kill` | bool | `true` | Must the mob be killed by a player? |
| `base_chance` | number | *(global)* | Override drop chance (0.0--1.0) for this book |
| `max_copies_per_player` | int | `-1` | Max times a player can receive this book (-1 = unlimited). Note: loot-table rules do not enforce per-player caps. |
| `mob_types` | array | *(any)* | Entity type IDs, e.g. `["minecraft:zombie"]` |
| `mob_tags` | array | *(any)* | Entity type tags, e.g. `["minecraft:undead"]` |
| `biomes` | array | *(any)* | Biome IDs, e.g. `["minecraft:plains"]` |
| `biome_tags` | array | *(any)* | Biome tags, e.g. `["minecraft:is_overworld"]` |
| `dimensions` | array | *(any)* | Dimension IDs, e.g. `["minecraft:overworld"]` |
| `min_y` / `max_y` | int | *(any)* | Y-coordinate range for the kill location |
| `time` | string | `"ANY"` | `"ANY"`, `"DAY_ONLY"`, or `"NIGHT_ONLY"` |
| `weather` | string | `"ANY"` | `"ANY"`, `"CLEAR_ONLY"`, `"RAIN_ONLY"`, or `"THUNDER_ONLY"` |

## Acquisition Methods

Books acquire through one or more acquisition rules; rules are additive, so a book can drop from mobs, appear in chests, and be granted by advancements simultaneously. Legacy `drop_conditions` continues to work unchanged and becomes an `entity_drop` rule.

### Entity Drops (from mob kills)

A book may specify `drop_conditions` at the top level, which creates an implicit `entity_drop` rule. Alternatively (or additionally), declare explicit acquisition rules in an `acquisition` array; each entry with type `entity_drop` accepts all the same keys as `drop_conditions` plus `chance` as an alias for `base_chance`.

```json
{
  "type": "entity_drop",
  "mob_types": ["minecraft:zombie", "minecraft:skeleton"],
  "require_player_kill": true,
  "base_chance": 0.1
}
```

### Loot Table Injection

Insert a book into named loot tables (chests, fishing, gameplay tables). Each table generation can contain at most one book, selected by weight among candidates that passed their chance roll. Entity death tables are skipped here; mob drops stay with `entity_drop`.

```json
{
  "type": "loot_table",
  "loot_tables": ["minecraft:chests/simple_dungeon", "minecraft:chests/desert_pyramid"],
  "chance": 0.5,
  "weight": 2.0
}
```

| Key | Type | Default | Notes |
|-----|------|---------|-------|
| `loot_tables` | array | *(required)* | Loot table IDs to inject into |
| `chance` | number | `1.0` | Probability this rule activates per table generation (0.0--1.0) |
| `weight` | number | `1.0` | Selection weight when multiple books compete for one slot |

### Advancement Delivery

Grant a book when a player earns any of the listed advancements. Codex delivery adds the book permanently to the collection; inventory delivery gives a physical copy once per player per book (a revoke and re-grant of the same advancement cannot mint a duplicate), dropping it at the player's feet if the inventory is full.

```json
{
  "type": "advancement",
  "advancements": ["minecraft:story/root", "minecraft:adventure/enter_the_end"],
  "delivery": "codex"
}
```

| Key | Type | Default | Notes |
|-----|------|---------|-------|
| `advancements` | array | *(required)* | Advancement IDs to trigger on |
| `delivery` | string | `"codex"` | `"codex"` (adds to collection) or `"inventory"` (physical copy once) |

### Acquisition Array

Books without any `acquisition` array and without `drop_conditions` automatically get a default `entity_drop` rule so pre-2.2.0 books keep dropping unchanged.

### New Book Metadata Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `tags` | array | *(none)* | Searchable keywords for Codex search (e.g. `["ruins", "war"]`); blank entries dropped |
| `discovery_hint` | string | *(none)* | Hint shown on uncollected Codex entries when discovery hints are enabled |
| `series` | string | *(none)* | Series name shown in the Codex entry tooltip |
| `series_order` | int | `0` | Position within the series (negative values clamp to 0) |

### Example

```json
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
    "Long ago, in these very fields...",
    "The battle raged for three days..."
  ]
}
```

Pages can be plain strings (auto-wrapped) or full JSON text components for advanced formatting:

```json
"pages": [
  "{\"text\":\"Chapter 1\\n\\nIt began with a whisper...\",\"color\":\"dark_red\",\"bold\":true}"
]
```

## Datapack lore

Datapacks can define lore books alongside configuration files. A datapack file at `data/<namespace>/rpg_lore/books/<path>.json` becomes a book with the id `<namespace>:<path>` (e.g. `data/towns_and_dragons/rpg_lore/books/history/fall_of_ardath.json` defines `towns_and_dragons:history/fall_of_ardath`) unless the JSON itself declares an `id` field.

The `config/rpg_lore/books/` directory always takes precedence. A config definition with the same id as a datapack book overrides it; every override is logged at INFO level so you can track which packs are being shadowed.

`/reload` (server resource reload) rescans both datapack and config layers and produces a report. `/rpglore reload` (config-only reload) rescans only the `config/rpg_lore/books/` folder and merges it with the existing datapack layer; this is faster when you iterate on config books and datapacks are unchanged.

Each book definition may include an optional `format_version` field (integer). Only version 1 is supported; omitting the field defaults to version 1. Any other value is an error and blocks the book from loading.

Use `/rpglore validate` or `/rpglore validate <book_id>` (OP level 2) to validate definitions without reloading. The output reports the source of each book (datapack or config) and includes diagnostics for syntax errors, unknown fields, invalid field values, and unsupported `format_version`.

## Lore Codex

The Lore Codex is a soul-bound item that stores and tracks your lore book collection.

- **Books stored in the Codex** -- when you pick up a lore book it always goes into the Codex instead of your inventory. The physical item is consumed and the book becomes accessible from the Codex GUI. A lore book only exists in physical form when it's dropped on the ground or extracted from the Codex.
- **Banked spare copies** -- the first copy of a book becomes a permanent, readable *master* entry. Every additional duplicate you pick up is banked as a *spare copy* (shown as `×n` in the GUI, capped at 99 per book). Duplicates are absorbed with a distinct lower-pitch sound.
- **Auto-granted** on first login (configurable)
- **Soul-bound** -- kept on death, cannot be dropped
- **Browse, search, and read** -- open the Codex GUI to browse your collected books. Live-search across titles, authors, categories, and tags. The collection counter shows how many books you've found vs. total available in the current category.
- **Organize by category** -- cycle through All Categories, individual categories, or Uncategorized books; the progress counter reflects your completion in the chosen category.
- **Filter and sort** -- filter by All, Collected, Unread, Favorites, or Missing books. Sort by Default (unread first), Title, Category, or Recently found.
- **Unread tracking and favorites** -- collected books show an unread dot when not yet read (toggleable in client config). Mark any book as a favorite with a clickable star; favorite status persists server-side and across sessions. Opening a book marks it read immediately.
- **Extract copies** -- pull a physical copy of a book into your inventory (generation incremented). Extraction draws down that book's banked spares and is blocked once none remain; the master copy is never consumed. Spare count shown as `×n` with a copy icon; greyed and disabled when no spares are available.
- **Duplicate handling** -- configure whether duplicate pickups are stored as spare copies (default) or left on the ground. Toggle via the button in the Codex GUI.
- **Keyboard navigation** -- press Esc to close search or screen; use arrow keys to page through the list or select rows; press Enter to open a selected book. After closing a book, you return to the Codex with your previous search, category, filter, sort, page, and selection preserved.
- **"Open Lore Codex" keybind** -- unbound by default, this optional hotkey opens the Codex browser when you carry the Codex in your inventory or Curios slot.
- **Curios support** -- equip the Codex in a dedicated "codex" Curios slot if the Curios mod is installed. All features work from either inventory or Curios slot.
- **Discovery hints** -- books can include a hint for uncollected entries, shown in the Codex when discovery hints are enabled (toggleable in server config).

## Integrating with RPG Lore

Other mods can grant lore books to players or listen for collection events via the public API.

### Granting Books

Call `LoreAcquisitionService.collect()` on the server thread to add a book to a player's Codex:

```java
import com.rpglore.acquisition.LoreAcquisitionService;
import com.rpglore.acquisition.LoreAcquisitionService.LoreAcquisitionSource;
import net.minecraft.server.level.ServerPlayer;

ServerPlayer player = /* ... */;
LoreAcquisitionService.CollectResult result = 
    LoreAcquisitionService.collect(player, "rpg_lore:my_book", LoreAcquisitionSource.API);
if (result == LoreAcquisitionService.CollectResult.NEWLY_COLLECTED) {
    // First time — the player got the book
}
```

The `CollectResult` enum indicates what happened: `NEWLY_COLLECTED` (first acquisition, fires event), `ALREADY_COLLECTED` (no change), `NOT_FOUND` (invalid book ID), `EXCLUDED` (book marked `codex_exclude`), or `CODEX_DISABLED` (feature turned off in config).

### Listening for Collection Events

Subscribe to `LoreCollectedEvent` on the Forge event bus to react when a player collects a book for the first time:

```java
import com.rpglore.acquisition.LoreCollectedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid = MOD_ID)
public class MyEventHandler {
    @SubscribeEvent
    public static void onBookCollected(LoreCollectedEvent event) {
        ServerPlayer player = event.getPlayer();
        ResourceLocation loreId = event.getLoreId();
        LoreAcquisitionSource source = event.getSource();
        // Handle the collection
    }
}
```

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/rpglore reload` | Level 2 (OP) | Reload book definitions from the config folder and merge with the existing datapack layer; displays change counts |
| `/rpglore validate [book_id]` | Level 2 (OP) | Check all definitions (or a single book) for syntax errors, unknown fields, invalid values, and unsupported `format_version` without reloading |
| `/rpglore give <players> <book_id> [track]` | Level 2 (OP) | Give a book to player(s); optional `true` to count against per-player copy limits (default: false, no tracking) |
| `/rpglore list` | Level 2 (OP) | List all loaded book definitions with their categories |
| `/rpglore collection` | Level 0 (All) | View your own lore book collection |
| `/rpglore codex give <players>` | Level 2 (OP) | Give a Lore Codex item to player(s) |
| `/rpglore codex status <player>` | Level 2 (OP) | View a player's Codex collection and duplicate handling mode |
| `/rpglore codex add <players> <book_id>` | Level 2 (OP) | Add a book to player(s)' Codex |
| `/rpglore codex remove <players> <book_id>` | Level 2 (OP) | Remove a book from player(s)' Codex |
| `/rpglore codex reset <players>` | Level 2 (OP) | Clear all collected books from player(s)' Codex |

## Server Configuration

`config/rpg_lore/server.toml`

### Drop Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `globalDropChance` | `0.05` | Base drop chance (0.0--1.0) |
| `onlyHostileMobs` | `true` | Only hostile mobs drop books |
| `maxBooksPerKill` | `1` | Max different books per kill |
| `enablePerBookWeights` | `true` | Use per-book weight for selection |
| `lootScaling` | `false` | Looting enchantment increases drop chance |
| `allowNonPlayerKills` | `false` | Allow non-player kills to trigger drops |

### Acquisition Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `enableEntityDrops` | `true` | Allow books with `entity_drop` rules to drop from mob kills |
| `enableLootTables` | `true` | Allow books with `loot_table` rules to inject into named loot tables |
| `enableAdvancements` | `true` | Allow books with `advancement` rules to be granted when players earn advancements |

### Codex Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `enabled` | `true` | Enable the Lore Codex feature |
| `soulbound` | `true` | Codex is kept on death and cannot be dropped |
| `autoCollect` | `true` | Auto-register new books in the Codex on pickup |
| `grantOnFirstJoin` | `true` | Give players a Codex on first login |
| `allowCopy` | `true` | Allow copying books from the Codex |
| `allowDuplicatePrevention` | `true` | Allow the duplicate handling toggle |
| `revealUncollectedNames` | `false` | Show uncollected book names (vs. "???") |
| `enableDiscoveryHints` | `true` | Show discovery hints on uncollected Codex entries when defined by books |

### Reader Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `allowRunCommandClicks` | `false` | Allow `run_command` click events inside lore book text. Off by default because lore packs are third-party content. |

## Client Configuration

`config/rpg_lore/client.toml`

### Display Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `showLoreIdInTooltip` | `false` | Show internal lore_id in tooltip (for pack authors) |

### Codex Display Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `showCollectionNotification` | `true` | Show an action bar message when a new book is added to the Codex |
| `playCollectionSound` | `true` | Play a sound when a new book is collected into the Codex |
| `rememberSearch` | `true` | Keep Codex search text when the screen is closed and reopened |
| `showUnreadMarkers` | `true` | Show a dot next to collected books that have not been read yet |

## Supported Languages

60 locales including: English, Chinese (Simplified & Traditional), Spanish, Hindi, Arabic, French, German, Italian, Portuguese, Dutch, Polish, Russian, Ukrainian, Swedish, Danish, Norwegian, Finnish, Czech, Slovak, Hungarian, Romanian, Bulgarian, Greek, Turkish, Croatian, Serbian, Slovenian, Macedonian, Albanian, Estonian, Latvian, Lithuanian, Icelandic, Maltese, Catalan, Galician, Basque, Irish, Welsh, Belarusian, Japanese, Korean, Indonesian, Vietnamese, Thai, Bengali, Tamil, Filipino, Afrikaans, Swahili, Hausa, Amharic, Yoruba, Oromo, Igbo, Zulu, Somali, and more.

## Development

Build the mod and run tests:

```bash
./gradlew build                 # Full build and tests
./gradlew compileJava          # Compile only
./gradlew test                 # Run JUnit tests
./gradlew runGameTestServer    # Run Forge GameTests (Curios excluded)
./gradlew runData              # Run data generators (Curios excluded)
```

JUnit tests cover data parsing and save format migration. Forge GameTests cover Codex collection logic and spare-copy handling. Both Curios exclusions are configured in `build.gradle` because the Curios mixin in headless environments causes boot failures that do not affect the mod in actual play.

See `docs/INTERACTIVE_TEXT.md` for creating interactive click events and hover tooltips in lore books, and `docs/SAVE_FORMAT.md` for the Codex save file structure.

## Building from Source

```bash
git clone https://github.com/otectus/RPGLore.git
cd RPGLore
./gradlew build
```

The compiled JAR will be in `build/libs/`.

> **Note:** Gradle itself must run on JDK 17 — newer JDKs are not supported
> by this Gradle/ForgeGradle version. If your system default is newer, point
> Gradle at a JDK 17 install, e.g. in `~/.gradle/gradle.properties`:
> `org.gradle.java.home=/path/to/jdk-17`

### Development

```bash
./gradlew runClient    # Launch a dev client
./gradlew runServer    # Launch a dev server
```

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests.

## License

[MIT](LICENSE)
