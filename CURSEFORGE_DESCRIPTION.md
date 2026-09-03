# RPG Lore

**Data-driven lore books through multiple discovery routes. Collect them all in the interactive Lore Codex.**

RPG Lore lets modpack makers and server owners add custom lore books to their world using simple JSON files. Books can drop from mobs, appear in loot tables, or be granted by advancements -- all configurable without coding. Players collect them in a soul-bound Codex that tracks reading progress, favorites, and discovery.

---

## Key Features

### Lore Books
- **Fully data-driven** -- each `.json` file in the config folder becomes a lore book. Add, edit, or remove books without recompiling.
- **Multiple discovery routes** -- books drop from mobs, inject into chest and fishing loot, and reward advancements. Rules stack, so the same book can be discovered through any combination of methods.
- **Conditional drops** -- control exactly which mobs drop which books via entity type, biome, dimension, time of day, weather, Y-level, and more.
- **Custom styling** -- per-book title color, author color, description, enchantment glint toggle, and generation labels.
- **Stylized title page** -- every book opens with an auto-generated title page featuring the book's title (auto-scaled to two lines, bold, colored) and author.
- **Custom book GUI** -- lore books use a unique texture distinct from vanilla written books.
- **Interactive pages** -- hover tooltips and clickable text with configurable `run_command` support for immersive pack design.
- **Per-player copy limits** -- optionally restrict how many times each player can receive a specific book via entity drops.
- **Looting scaling** -- optionally increase drop chance with the Looting enchantment.
- **Searchable metadata** -- books include optional tags for Codex search, discovery hints for uncollected entries, and series grouping.

### Lore Codex Browser
- **Personal collection manager** -- a soul-bound item that stores your lore books directly inside it. No inventory clutter.
- **Books go into the Codex** -- when you pick up a lore book, it's consumed from the world and stored in the Codex. Books only exist in physical form when dropped on the ground or extracted from the Codex.
- **Banked spare copies** -- the first copy becomes a permanent readable master; every duplicate is banked as a spare (shown as `×n`, capped at 99 per book) and absorbed with a distinct lower-pitch sound.
- **Interactive browser** -- custom parchment-styled GUI with live search across titles, authors, categories, and tags. Cycle through categories or filter by Collected, Unread, Favorites, or Missing. Sort by default (unread first), title, category, or discovery order.
- **Keyboard navigation** -- browse with arrow keys and Enter; press Esc to close or exit search. View state is preserved when you return from reading.
- **Reading progress** -- collected books show an unread indicator until you open them. Mark any book as a favorite with a clickable star; status persists server-side.
- **Extract mechanic** -- pull physical copies into your inventory (generation incremented). Extraction consumes banked spares and is blocked when none remain -- the master is never consumed.
- **Duplicate control** -- toggle between absorbing duplicates into the spare bank (default) or leaving them on the ground.
- **Discovery hints** -- uncollected entries can display author-provided hints when discovery hints are enabled.
- **Soul-bound** -- granted on first login, kept on death, cannot be dropped.
- **Curios API support** -- equip the Codex in a dedicated Curios slot. Soft dependency -- works without Curios installed.

### Pack Support
- **Hot-reloadable** -- use `/rpglore reload` to apply changes without restarting. Reload reports display book counts and change summary.
- **Validation without reload** -- `/rpglore validate [book_id]` checks syntax, unknown fields, field values, and `format_version` before deployment. Reports include JSON paths and line numbers.
- **Datapack support** -- define books in datapacks at `data/<namespace>/rpg_lore/books/<path>.json`. Config books override datapack books with the same ID.
- **Book categories** -- organize books with the `category` field for clean Codex browsing.
- **Codex exclusion** -- hide debug or temporary books from the Codex with `codex_exclude`.
- **Per-book glint** -- toggle the enchantment shimmer on/off per book.
- **Auto-generated docs** -- `_README.txt` in the config folder documents all fields and auto-updates when field definitions change.

### Administration
- `/rpglore reload` -- hot-reload book definitions from config
- `/rpglore validate [book_id]` -- check syntax without reloading
- `/rpglore give <players> <book_id>` -- give books to players
- `/rpglore list` -- list all loaded books with categories
- `/rpglore collection` -- players can view their own collection (no OP required)
- `/rpglore codex give/reset/add/remove/status` -- full Codex administration

---

## Quick Start

1. Drop the JAR into your `mods/` folder.
2. Start the game once -- the mod generates example configs and a sample book.
3. Add your `.json` book files to `config/rpg_lore/books/`.
4. Use `/rpglore reload` to apply changes.
5. Use `/rpglore validate` to check your definitions.
6. Players receive a Lore Codex on first join (configurable). Right-click to browse your collection.

---

## Configuration

All settings are in `config/rpg_lore/server.toml` and `config/rpg_lore/client.toml`. Every Codex feature and acquisition method can be individually toggled on or off.

## Compatibility

- **Curios API** (optional) -- equip the Codex in a dedicated Curios slot. All features work from either inventory or Curios slot. The mod functions fully without Curios installed.
- **Optional unbound keybind** -- "Open Lore Codex" hotkey opens the browser if set (defaults to unbound).

## Localization

Localized in **60 languages** including English, Chinese, Spanish, Hindi, Arabic, French, German, Russian, Japanese, Korean, and 50 more.

---

**Requires:** Minecraft 1.20.1 | Forge 47.3.0+ | Java 17
**Optional:** Curios API 5.4.7+
**License:** MIT
**Source:** [GitHub](https://github.com/otectus/RPGLore)
