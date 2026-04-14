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

### Lore Codex
- **Personal collection book** -- a soul-bound item that stores your lore books directly inside it.
- **Books go into the Codex** -- when you pick up a new lore book, it's consumed from the world and stored in the Codex. No inventory clutter.
- **Browsable GUI** -- custom parchment-styled screen with paginated book list, collection counter (n/N), and per-book Read and Copy actions.
- **Copy mechanic** -- create a physical copy of any collected book into your inventory. Copies always go to your inventory with generation incremented.
- **Soul-bound** -- granted on first login, kept on death, cannot be dropped.
- **Duplicate prevention** -- toggle to block picking up lore books you've already collected.
- **Curios API support** -- equip the Codex in a dedicated "codex" Curios slot. Soft dependency -- works without Curios installed.

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
5. Players receive a Lore Codex on first join. Right-click to browse your collection.

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

## Compatibility

- **Curios API** (optional) -- equip the Codex in a dedicated Curios slot. All features work from either inventory or Curios slot. The mod functions fully without Curios installed.

## Localization

Localized in **60 languages** including English, Chinese, Spanish, Hindi, Arabic, French, German, Russian, Japanese, Korean, and 50 more.

---

**Requires:** Minecraft 1.20.1 | Forge 47.3.0+ | Java 17
**Optional:** Curios API 5.4.7+
**License:** MIT
**Source:** [GitHub](https://github.com/otectus/RPGLore)
