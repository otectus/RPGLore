# Changelog

## [2.0.5] - 2026-04-13

### New Features
- **Chiseled Bookshelf storage** -- lore books can now be placed in vanilla Chiseled Bookshelves and displayed alongside regular written books. Implemented via the `minecraft:bookshelf_books` item tag; comparator output and book rendering work naturally.
- **Lectern placement** -- lore books can now be placed on vanilla Lecterns. Right-click an empty lectern while holding a lore book to place it; right-click the filled lectern to open the reader UI. Reading, dropping, and comparator output all work unchanged. Implemented via a `PlayerInteractEvent.RightClickBlock` handler that replicates `LecternBlock.placeBook`, since vanilla `LecternBlock#isBook` is hardcoded to `Items.WRITTEN_BOOK`/`Items.WRITABLE_BOOK` with no tag or extension point.

### Bug Fixes
- **Stricter `lore_id` validation on pickup** -- `CodexEventHandler.onItemPickup` now rejects NBT where `lore_id` is missing, wrong type, or empty, preventing ghost entries from malformed `/give`d books.
- **Blank lore books no longer glint** -- `LoreBookItem#isFoil` now returns `false` when NBT is absent, so `/give rpg_lore:lore_book` without an `nbt` argument produces a plain template book instead of a shimmering blank.

### Improvements
- **Translation keys for all `/rpglore` command output** -- 18 new keys in `en_us.json` cover `reload`, `give`, `list`, `collection`, and all `codex` admin subcommands; all `Component.literal(...)` call sites in `RpgLoreCommands` replaced with `Component.translatable(...)`. Admin feedback is now consistent with the 60-language localization pass shipped in 2.0.1.
- **CodexService threading contract documented** -- `instance` is now `volatile`, and Javadoc spells out the main-thread-only rule and the `ctx.enqueueWork(...)` requirement for packet handlers. No behavioral change in 1.20.1 (all handlers already comply), but the contract is now explicit.
- **Data-generation scaffolding** -- added `DataGenerators`, `ModBlockTagsProvider`, and `ModItemTagsProvider` under `com.rpglore.data`. The item-tags provider emits `minecraft:bookshelf_books` containing `rpg_lore:lore_book` when `./gradlew runData` is run. (Note: `runData` currently errors in this environment due to a pre-existing Curios mixin/mappings clash; the hand-written tag JSON remains authoritative until that is resolved.)

## [2.0.4] - 2026-03-27

### Critical Bug Fixes
- **First-join Codex grant no longer lost when inventory is full** -- the Codex is now dropped on the ground if inventory is full, and only marked as granted after successful delivery
- **`maxBooksPerKill` now enforced across all drop types** -- books with `base_chance` are no longer exempt from the per-kill cap; the combined total from both override and global-pool books respects `MAX_BOOKS_PER_KILL`
- **Codex progress counts can no longer exceed total** -- collected counts now use `collected ∩ eligible` instead of the raw saved ID set; books switched to `codex_exclude=true` are properly pruned from collections
- **Soulbound death handling no longer deletes extra Codices** -- fixed early return that skipped Curios slot check, added `add()` result validation for Curios restoration, moved `invalidateCaps()` into a `finally` block

### Bug Fixes
- **Copying from Codex with a full inventory** -- now rejects the copy with an error message instead of dropping an unpickable book on the ground
- **`CODEX_ENABLED` now fully disables the Codex** -- the item no longer appears in the creative tab, `use()` shows a disabled message, and commands are guarded when the config is off
- **Client notification/sound configs now work** -- collection feedback is sent via a dedicated clientbound packet so the client can respect `showCollectionNotification` and `playCollectionSound` settings
- **`/rpglore reload` now prunes and resyncs** -- stale entries are pruned and all online players' Codex items and UI caches are refreshed after a reload
- **Admin `/rpglore codex add` rejects excluded books** -- books with `codex_exclude=true` can no longer be injected into Codex data via commands
- **Input validation strengthened** -- `base_chance` clamped to [0.0, 1.0], `min_y > max_y` warned and swapped, invalid book IDs rejected with error

### Improvements
- **Centralized Codex state management** -- new `CodexService` handles all mutations atomically across SavedData, item NBT, and client sync, eliminating tooltip/UI drift after toggles, admin commands, or reloads
- **Looting enchantment reads from loot context** -- uses `LootContextParams.TOOL` instead of always reading the main hand, fixing off-hand and projectile kill scenarios
- **Curios item tag added** -- the Codex slot now has a proper item tag at `data/curios/tags/items/codex.json` for reliable slot assignment
- **Curios compatibility hardened** -- `findCodexInCurios` wrapped with `NoClassDefFoundError` catch for defensive classloading safety
- **Sync packet slimmed** -- removed unused `author` and `category` fields from network transmission
- **Item NBT slimmed** -- removed full `codex_collected` ID list from item NBT; only lightweight tooltip fields are cached
- **Localization pass** -- moved Codex tooltip text to translation keys; added `rpg_lore.codex.tooltip.description`, `rpg_lore.codex.tooltip.hint`, `rpg_lore.codex.disabled`, and `rpg_lore.codex.copy.inventory_full`
- **Mod metadata** -- added `updateJSONURL` to mods.toml

### Removed
- Removed dead `codex_owner` NBT tag (was written but never enforced)
- Removed copied Forge source files from repository root (`net/` directory)
- Replaced `ConcurrentHashMap` with `HashMap` in tracking data classes (all access is main-thread)

### Network
- Protocol version bumped from `1` to `2` (clients and servers must match)

## [2.0.1] - 2026-03-26

### New Feature: Lore Codex
- **Lore Codex item** -- a soul-bound personal collection tracker that stores your lore books directly
- **Books stored in the Codex** -- when you pick up a new lore book, it goes into the Codex instead of your inventory; the physical item is consumed and the book is accessible from the Codex GUI
- **Copy mechanic** -- create physical copies of any collected book from the Codex into your inventory (copies always go to inventory, generation incremented)
- **Auto-collection** -- picking up a lore book for the first time automatically stores it in your Codex
- **Soul-binding** -- the Codex is granted on first login and persists through death; cannot be dropped when soul-bound
- **Browsable GUI** -- custom parchment-styled screen using the codex.png texture, with paginated book list, collection counter (n/N), and per-book Read/Copy actions
- **Duplicate prevention toggle** -- when enabled, prevents picking up lore books already stored in the Codex
- **Codex commands** -- `/rpglore codex give/reset/add/remove/status` for server administration
- **Self-service collection view** -- `/rpglore collection` (no OP required) shows your own collected books
- **Full networking** -- Forge SimpleChannel with server-authoritative data; client never modifies tracking
- **7 server config options** -- enable/disable Codex, soul-binding, auto-collect, first-join grant, copy, duplicate prevention, reveal uncollected names
- **2 client config options** -- collection notification and sound toggles

### Curios API Support
- **Optional Curios integration** -- the Codex can be equipped in a dedicated "codex" Curios slot (soft dependency; mod works without Curios)
- **Codex slot** -- registers a custom "codex" slot type for players via Curios data pack
- **Full compatibility** -- auto-collection, soul-binding, and all Codex features work with the Codex in either inventory or Curios slot

### New Book Definition Fields
- `show_glint` (bool) -- per-book enchantment glint toggle (default: true)
- `category` (string) -- optional category for grouping books in the Codex and `/rpglore list`
- `codex_exclude` (bool) -- exclude a book from appearing in the Codex

### Localization
- Added 58 language files covering 60 locales total, including all European languages, top 10 Asian languages, top 10 African languages, plus Japanese, Korean, Filipino, and Traditional Chinese

### Art
- Updated Lore Book item texture
- Updated Lore Codex item texture
- Custom codex.png GUI texture for the Codex screen (leather-bound parchment with page navigation sprites)

### Bug Fixes
- **Drop chance redesign** -- `base_chance` now truly overrides the global drop chance instead of stacking multiplicatively with it; books without `base_chance` still use the global chance
- **Tracking order fix** -- per-player copy tracking now records after the book is confirmed added to loot, not before
- **Generation validation** -- `generation` field is now clamped to 0-3 with a warning on invalid values
- **JSON escaping** -- replaced manual string escaping with Gson programmatic JSON building, fixing edge cases with special characters
- **Biome fallback logging** -- null biome IDs now log a warning instead of silently defaulting to `minecraft:plains`
- **Copy limit validation** -- `max_copies_per_player` values below -1 are now warned and normalized to -1

### Improvements
- **`/rpglore give` no longer tracks by default** -- admin gives no longer count against per-player copy limits; use the optional `track` argument to opt in
- **Extracted LoreBookRegistry** -- book registry, query methods, and tracking delegation split out from BooksConfigLoader for cleaner architecture
- **DropCondition converted to record** -- consistent with DropConditionContext and LoreBookDefinition
- **Weighted selection short-circuit** -- skips unnecessary weighted sampling when all candidates fit within the max
- **Page count limit** -- books are now capped at 200 pages with a warning
- **Title length warning** -- titles exceeding 48 characters produce a log warning
- **Creative tab** -- Lore Book and Lore Codex now appear in the Tools & Utilities creative tab
- **Mod metadata** -- added `displayURL` to mods.toml
- **Code documentation** -- hardcoded vanilla layout constants and fragile shadow fields are now documented with version-upgrade notes

### Changes
- Enchantment glint is now configurable per-book via the `show_glint` field (default: true, preserving existing behavior)
- `/rpglore list` now shows book categories when present
- Tooltip separator extracted to a named constant

## [1.1.0] - 2026-03-23

### Bug Fixes
- Fixed per-player copy limit logic -- `max_copies_per_player` now works correctly for all values, not just 1
- Fixed `/rpglore reload` erasing per-player copy tracking data
- Fixed page navigation desync in lore book screen when rapidly clicking forward on the last page
- Fixed loot modifier creating a new `Random` instance per mob kill -- now uses the loot context's seeded random

### Improvements
- Per-player copy tracking now persists across server restarts via world SavedData
- `/rpglore give` now records against per-player copy limits for consistency
- Book weight is now validated at load time -- zero or negative weights are clamped to 0.01 with a warning
- Per-player tracking data is thread-safe (ConcurrentHashMap)

### Changes
- Default author color on the book screen unified to dark gray (consistent with tooltip)
- Removed unused `showPickupToast` and `glintColor` client config options (were never implemented)

## [1.0.0] - Initial Release

- Data-driven lore books defined via JSON config files
- Configurable mob drop conditions (entity type, biome, dimension, time, weather, Y-level)
- Custom book GUI with auto-generated title page
- Styled tooltips with colored title, author, generation, and description
- In-game commands: `/rpglore reload`, `/rpglore give`, `/rpglore list`
- Server and client configuration via Forge config spec
- Looting enchantment scaling support
