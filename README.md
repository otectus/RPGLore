# RPG Lore

A data-driven lore book mod for Minecraft Forge 1.20.1. Define custom books via simple JSON files and have them drop from mobs based on configurable conditions. Collect them all in the Lore Codex.

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-green)
![Forge](https://img.shields.io/badge/Forge-47.3.0+-orange)
![Java](https://img.shields.io/badge/Java-17-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

## Features

- **JSON-defined lore books** -- add, edit, or remove books without recompiling. Each `.json` file in the config folder becomes a lore book.
- **Conditional mob drops** -- control which mobs drop which books based on entity type, biome, dimension, time of day, weather, Y-level, and more.
- **Lore Codex** -- a soul-bound personal collection tracker. Auto-collects new books on pickup, shows your progress (n/N), lets you read and copy collected books from a custom GUI.
- **Auto-generated title page** -- every lore book opens with a stylized title page showing the book's title (scaled up, bold, colored) and author (bold, colored), both centered on the page.
- **Custom book GUI** -- lore books use a unique book texture distinct from vanilla written books.
- **Tooltip styling** -- bold colored title, bold colored author, italic description, and generation label with automatic formatting.
- **Configurable appearance** -- per-book title color, author color, description, glint toggle, and category via the JSON definition.
- **Enchantment glint** -- lore books shimmer with an enchantment glint by default (configurable per-book with `show_glint`).
- **In-game commands** -- give books, reload configs, list books, view your collection, and manage Codex data.
- **Per-player copy limits** -- optionally restrict how many times a player can receive a specific book.
- **Looting scaling** -- optionally increase drop chance with the Looting enchantment.
- **60 languages** -- localized in English, Chinese, Spanish, Hindi, Arabic, French, German, Russian, Japanese, Korean, and 50 more.

## Requirements

- Minecraft 1.20.1
- Forge 47.3.0+
- Java 17

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
| `title` | string | Yes | -- | Book title displayed in-game |
| `author` | string | No | `"Unknown"` | Author name |
| `generation` | int | No | `0` | 0 = Original, 1 = Copy, 2 = Copy of Copy, 3 = Tattered |
| `weight` | number | No | `1.0` | Selection weight when multiple books match a drop |
| `pages` | array | Yes | -- | Page content (JSON text components or plain strings) |

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

Nested inside a `"drop_conditions"` object. Omitted fields match everything.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `require_player_kill` | bool | `true` | Must the mob be killed by a player? |
| `base_chance` | number | *(global)* | Override drop chance (0.0--1.0) for this book |
| `max_copies_per_player` | int | `-1` | Max times a player can receive this book (-1 = unlimited) |
| `mob_types` | array | *(any)* | Entity type IDs, e.g. `["minecraft:zombie"]` |
| `mob_tags` | array | *(any)* | Entity type tags, e.g. `["minecraft:undead"]` |
| `biomes` | array | *(any)* | Biome IDs, e.g. `["minecraft:plains"]` |
| `biome_tags` | array | *(any)* | Biome tags, e.g. `["minecraft:is_overworld"]` |
| `dimensions` | array | *(any)* | Dimension IDs, e.g. `["minecraft:overworld"]` |
| `min_y` / `max_y` | int | *(any)* | Y-coordinate range for the kill location |
| `time` | string | `"ANY"` | `"ANY"`, `"DAY_ONLY"`, or `"NIGHT_ONLY"` |
| `weather` | string | `"ANY"` | `"ANY"`, `"CLEAR_ONLY"`, `"RAIN_ONLY"`, or `"THUNDER_ONLY"` |

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

## Lore Codex

The Lore Codex is a soul-bound item that tracks your lore book collection.

- **Auto-granted** on first login (configurable)
- **Auto-collects** new lore books when you pick them up
- **Soul-bound** -- kept on death, cannot be dropped
- **Collection counter** -- shows how many books you've found vs. total available
- **Browse & read** -- click any collected book to read it directly from the Codex
- **Copy books** -- duplicate a collected book by consuming a physical copy from your inventory
- **Duplicate prevention** -- toggle to block picking up lore books you've already collected

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/rpglore reload` | OP | Reload all book definitions from disk |
| `/rpglore give <players> <book_id> [track]` | OP | Give a book to player(s); optional `true` to count against copy limits |
| `/rpglore list` | OP | List all loaded book definitions |
| `/rpglore collection` | All | View your own lore book collection |
| `/rpglore codex give <players>` | OP | Give a Lore Codex item to player(s) |
| `/rpglore codex status <player>` | OP | View a player's Codex collection |
| `/rpglore codex add <players> <book_id>` | OP | Add a book to player(s)' Codex |
| `/rpglore codex remove <players> <book_id>` | OP | Remove a book from player(s)' Codex |
| `/rpglore codex reset <players>` | OP | Clear all collected books from player(s)' Codex |

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

### Codex Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `enabled` | `true` | Enable the Lore Codex feature |
| `soulbound` | `true` | Codex is kept on death and cannot be dropped |
| `autoCollect` | `true` | Auto-register new books in the Codex on pickup |
| `grantOnFirstJoin` | `true` | Give players a Codex on first login |
| `allowCopy` | `true` | Allow copying books from the Codex |
| `allowDuplicatePrevention` | `true` | Allow the duplicate prevention toggle |
| `revealUncollectedNames` | `false` | Show uncollected book names (vs. "???") |

## Client Configuration

`config/rpg_lore/client.toml`

| Setting | Default | Description |
|---------|---------|-------------|
| `showLoreIdInTooltip` | `false` | Show internal lore_id in tooltip (for pack authors) |
| `showCollectionNotification` | `true` | Show action bar message on new collection |
| `playCollectionSound` | `true` | Play sound on new collection |

## Supported Languages

60 locales including: English, Chinese (Simplified & Traditional), Spanish, Hindi, Arabic, French, German, Italian, Portuguese, Dutch, Polish, Russian, Ukrainian, Swedish, Danish, Norwegian, Finnish, Czech, Slovak, Hungarian, Romanian, Bulgarian, Greek, Turkish, Croatian, Serbian, Slovenian, Macedonian, Albanian, Estonian, Latvian, Lithuanian, Icelandic, Maltese, Catalan, Galician, Basque, Irish, Welsh, Belarusian, Japanese, Korean, Indonesian, Vietnamese, Thai, Bengali, Tamil, Filipino, Afrikaans, Swahili, Hausa, Amharic, Yoruba, Oromo, Igbo, Zulu, Somali, and more.

## Building from Source

```bash
git clone https://github.com/otectus/RPGLore.git
cd RPGLore
./gradlew build
```

The compiled JAR will be in `build/libs/`.

### Development

```bash
./gradlew runClient    # Launch a dev client
./gradlew runServer    # Launch a dev server
```

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests.

## License

[MIT](LICENSE)
