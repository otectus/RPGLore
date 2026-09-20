Data-driven lore books that drop from mobs—collect them all in the Lore Codex.

## Overview

RPG Lore is a Forge mod that brings rich, customizable lore to your server or world. Pack makers and server owners can define lore books entirely through simple JSON files—no coding required. Mobs drop books based on configurable conditions (entity type, biome, dimension, time of day, weather, Y-level), and players collect them in the soul-bound **Lore Codex**, a custom-built collection browser.

## Features

**JSON-Defined Lore Books**
- Add, edit, or remove books without recompiling; each `.json` file in the config folder becomes a lore book.
- Customize title, author, page content, colors, glint, category, and searchable tags per book.

**Multiple Acquisition Routes**
- **Entity drops**: books drop from mobs with configurable conditions (entity type, biome, dimension, time, weather, Y-level).
- **Loot-table injection**: insert books into named loot tables (chests, fishing, gameplay tables) with configurable chance and weight.
- **Advancements**: grant books when players earn specific advancements.
- Rules are additive—the same book can acquire through any or all methods.

**Lore Codex Browser**
- Collect books directly into the soul-bound Codex item (not your inventory); duplicates are banked as extractable spare copies.
- **Live search** across titles, authors, categories, and tags.
- **Organize by category** with filters: All, Collected, Unread, Favorites, or Missing.
- **Sort by**: Default (unread first), Title, Category, or Recently found.
- **Unread tracking** — see which books you haven't read yet (toggleable in client config).
- **Mark favorites** — click the star icon to remember your favorites across sessions.
- **8 entries per page** with keyboard navigation (arrow keys + Enter).
- **Red ribbon tabs** for Category, Filter, and Sort controls stick out from the book's edge.
- Open any book with the small **open-book icon** on each row; your search, category, filter, sort, and page are restored when you return.

**Custom Book Reader**
- Auto-generated **title page** showing title (auto-scaled bold, colored) and author (bold, colored), centered.
- Custom book texture distinct from vanilla written books.
- **Interactive text**: hover tooltips (`show_text`) and click events (`suggest_command`, `copy_to_clipboard`, `open_url`, `change_page`, and `run_command` with server-config gating).
- **Styled tooltips** with bold colored title and author, italic description, series position, and generation label.

**Curios API Support**
- Equip the Codex in a dedicated **Curios slot** (optional; works without Curios installed).

**In-Game Commands**
- `/rpglore reload` — reload book definitions and merge with datapack layer.
- `/rpglore validate [book_id]` — check definitions for syntax errors without reloading.
- `/rpglore give <players> <book_id> [track]` — give books to players.
- `/rpglore list` — list all loaded books with categories.
- `/rpglore collection` — view your own collection.
- `/rpglore codex give/add/remove/reset/status` — admin Codex management.

**Advanced Features**
- **Datapack support**: define books at `data/<namespace>/rpg_lore/books/<path>.json` in datapacks; config books override datapack ones.
- **Per-player copy limits**: restrict how many times a player can receive a specific book.
- **Looting enchantment scaling**: optionally increase drop chance with Looting.
- **Spare copy extraction**: pull physical copies from your Codex (generation incremented); spare bank is capped at 99 per book.
- **Discovery hints**: books can include hints for uncollected entries, shown when enabled.
- **Series tracking**: organize related books with `series` and `series_order` metadata.

**Localization**
- Fully translated into 60 languages: English, Chinese (Simplified & Traditional), Spanish, Hindi, Arabic, French, German, Italian, Portuguese, Dutch, Polish, Russian, Ukrainian, Swedish, Danish, Norwegian, Finnish, Czech, Slovak, Hungarian, Romanian, Bulgarian, Greek, Turkish, Croatian, Serbian, Slovenian, Macedonian, Albanian, Estonian, Latvian, Lithuanian, Icelandic, Maltese, Catalan, Galician, Basque, Irish, Welsh, Belarusian, Japanese, Korean, Indonesian, Vietnamese, Thai, Bengali, Tamil, Filipino, Afrikaans, Swahili, Hausa, Amharic, Yoruba, Oromo, Igbo, Zulu, Somali, and more.

## Requirements

- **Minecraft**: 1.20.1
- **Forge**: 47.3.0 or later
- **Java**: 17
- **Curios API** 5.4.7+ (optional; enables a dedicated Codex equipment slot)

## Getting Started

1. Install the mod in your `mods/` folder.
2. Start the server or singleplayer world once. The mod creates:
   - `config/rpg_lore/server.toml` — server settings (drops, Codex features)
   - `config/rpg_lore/client.toml` — client display settings
   - `config/rpg_lore/books/` — folder for your lore book definitions with an example book
3. Add your own `.json` files to `config/rpg_lore/books/`.
4. Use `/rpglore reload` in-game to hot-reload book definitions without restarting.
5. Players receive a Lore Codex on first join (configurable). Right-click to browse.

## For Pack Authors

Datapacks can define lore books alongside configuration files. Place JSON at `data/<namespace>/rpg_lore/books/<path>.json`; the book ID becomes `<namespace>:<path>` unless overridden in the JSON itself.

Use `/rpglore validate` or `/rpglore validate <book_id>` to check definitions for syntax errors, unknown fields, invalid values, and unsupported schema versions—without reloading.

The `config/rpg_lore/books/` directory always takes precedence over datapacks. Config definitions with the same ID override datapack books (all overrides are logged for transparency).

See the README at https://github.com/otectus/RPGLore for complete JSON book definition format, acquisition rules, and configuration options.

## Links

- **GitHub**: https://github.com/otectus/RPGLore
- **Issue Tracker**: https://github.com/otectus/RPGLore/issues

## License

MIT
