# RPG Lore

**Data-driven lore books that drop from mobs. Collect them all in the Lore Codex.**

RPG Lore lets modpack makers and server owners add custom lore books to their world using simple JSON files. Books drop from mobs based on configurable conditions -- entity type, biome, dimension, time of day, weather, Y-level, and more. No coding required.

---

## Key Features

### Lore Books
- **Fully data-driven** -- each `.json` file in the config folder becomes a lore book. Add, edit, or remove books without recompiling.
- **Conditional drops** -- control exactly which mobs drop which books with 11 different filter types.
- **Custom styling** -- per-book title color, author color, description, enchantment glint toggle, and generation labels.
- **Stylized title page** -- every book opens with an auto-generated title page featuring the book's title (scaled, bold, colored) and author.
- **Custom book GUI** -- lore books use a unique texture distinct from vanilla written books.
- **Per-player copy limits** -- optionally restrict how many times each player can receive a specific book.
- **Looting scaling** -- optionally increase drop chance with the Looting enchantment.

### Lore Codex (New in 2.0)
- **Personal collection tracker** -- a soul-bound item that records every lore book you've found.
- **Auto-collection** -- picking up a lore book for the first time automatically adds it to your Codex.
- **Soul-bound** -- granted on first login, kept on death, cannot be dropped.
- **Browsable GUI** -- paginated book list with search, collection counter (n/N), and per-book Read and Copy buttons.
- **Duplicate prevention** -- toggle to block picking up lore books you've already collected.
- **Copy mechanic** -- duplicate any collected book by consuming a physical copy from your inventory.

### For Pack Makers
- **Hot-reloadable** -- use `/rpglore reload` to apply changes without restarting.
- **11 drop condition types** -- entity type, entity tags, biome, biome tags, dimension, Y-range, time of day, weather, player kill requirement, per-book chance override, and per-player copy limits.
- **Book categories** -- organize books with the `category` field for clean Codex grouping.
- **Codex exclusion** -- hide debug or temporary books from the Codex with `codex_exclude`.
- **Per-book glint** -- toggle the enchantment shimmer on/off per book.
- **Comprehensive README** -- auto-generated `_README.txt` in the config folder documents every field.

### Administration
- `/rpglore reload` -- hot-reload all book definitions
- `/rpglore give` -- give books to players (with optional copy-limit tracking)
- `/rpglore list` -- list all loaded books with categories
- `/rpglore collection` -- players can view their own collection (no OP required)
- `/rpglore codex give/reset/add/remove/status` -- full Codex administration

---

## Quick Start

1. Drop the JAR into your `mods/` folder.
2. Start the game once -- the mod generates example configs and a sample book.
3. Add your `.json` book files to `config/rpg_lore/books/`.
4. Use `/rpglore reload` to apply changes.

---

## Example Book Definition

```json
{
  "title": "The Ancient Battle",
  "author": "Unknown Chronicler",
  "weight": 5,
  "title_color": "FFD700",
  "description": "A faded account of a great battle.",
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

---

## Configuration

All settings are in `config/rpg_lore/server.toml` and `config/rpg_lore/client.toml`. Every Codex feature can be individually toggled on or off.

## Localization

Localized in **60 languages** including English, Chinese, Spanish, Hindi, Arabic, French, German, Russian, Japanese, Korean, and 50 more.

---

**Requires:** Minecraft 1.20.1 | Forge 47.3.0+ | Java 17
**License:** MIT
**Source:** [GitHub](https://github.com/otectus/RPGLore)
