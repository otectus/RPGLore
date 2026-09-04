# Changelog

## [Unreleased] - 2.2.0

### New Features
- **Lore Codex browser** -- search across titles, authors, categories, and tags with live filtering; organize by category; filter by All, Collected, Unread, Favorites, or Missing; sort by Default, Title, Category, or Recently found; browse with arrow keys and Enter; press Esc to close or exit search; all view state (search text, category, filter, sort, page, selection) is restored when you return from reading a book
- **Unread markers and favorites** -- collected books show an unread indicator (toggleable in client config) when not yet read; click the star icon to mark any book as a favorite; favorite status persists across sessions
- **Codex spare copy display** -- duplicates appear as `×n` with a copy icon (greyed when no spares remain); extraction is blocked when the spare bank is empty
- **Entry tooltips** -- hover over a Codex entry to see the collected book's full details, category, and series position
- **Optional "Open Lore Codex" keybind** -- unbound by default; opens the Codex from inventory or Curios slot when set (server verifies possession)
- **Interactive text in books** -- hover tooltips with `show_text` and click events (`suggest_command`, `copy_to_clipboard`, `open_url`, `change_page`) work in page text; `run_command` requires the server config `reader.allowRunCommandClicks=true` (default false) for security
- **Book title wrapping** -- titles up to two lines auto-scale (1.0× down to 0.75×) and truncate with ellipsis if needed, preventing poor display on long titles
- **Pack validation command** -- `/rpglore validate [book_id]` (OP level 2) checks JSON syntax, unknown fields, field types and ranges, and `format_version` without reloading; bulk validation lists each definition, single-book validation shows full diagnostics
- **Structured validation diagnostics** -- validation errors and warnings include JSON path, line/column position, and actionable suggestions; strict JSON parsing with lenient fallback
- **Reload report** -- `/rpglore reload` displays loaded/added/changed/removed counts, config overrides of datapack definitions, and warning/error counts in chat; `/reload` logs a summary to the server log
- **Datapack lore support** -- define books at `data/<namespace>/rpg_lore/books/<path>.json` with automatic ID generation (`<namespace>:<path>` unless the JSON declares `id`); config definitions override datapack ones (logged at INFO)
- **Multiple acquisition routes** -- books acquire through additive `entity_drop`, `loot_table`, and `advancement` rules, allowing the same book to drop from mobs, appear in chests, and be granted by advancements simultaneously; legacy `drop_conditions` still works and becomes a single entity_drop rule
- **Loot-table injection** -- rules with type `loot_table` inject books into named tables (chests, fishing, gameplay) with configurable chance and weight; at most one book per table generation
- **Advancement delivery** -- rules with type `advancement` grant books when players earn advancements; delivery mode chooses between Codex (permanent) or inventory (once per player per book; a revoke and re-grant cannot mint a duplicate)
- **Book discovery metadata** -- `tags` are searchable keywords; `discovery_hint` shows on uncollected entries when enabled; `series` and `series_order` organize related books
- **Acquisition rules public API** -- mods call `LoreAcquisitionService.collect()` to add books with source attribution; `LoreCollectedEvent` fires on the Forge event bus (server-side, first-time collection only) carrying player, book ID, and source
- **Automated regression tests** -- JUnit tests cover data parsing and save format migration; Forge GameTests cover Codex gameplay including entity-drop conditions, Codex pruning and reset, player granting, and death/respawn scenarios (run `./gradlew runGameTestServer` or `./gradlew runData` with Curios excluded from both headless runs)

### Bug Fixes
- **Starter Codex no longer lost on grant when inventory is full** -- previously, the inventory-full path used the standard item-drop route, triggering the soulbound toss guard which cancelled the drop and pushed the item back at the full inventory, destroying the Codex while the grant flag was already set; the Codex is now placed directly on the ground at the player's feet via ItemEntity creation, so it can be picked up without re-triggering the guard

### Improvements
- **Reading marks books as read** -- opening a book from the Codex or from your hand immediately records read state; read state persists with discovery timestamp
- **Save format 2 with auto-migration** -- existing 2.1.x saves migrate automatically; all pre-2.2.0 books are marked read so returning players are not flooded with "new" entries
- **Network protocol 3** -- clients and servers must both update; traffic is reduced by sending the full catalog only when it changes or when a book switches from hidden to revealed
- **Format version validation** -- `format_version: 1` is supported; any other value produces an error
- **Codex read state and discovery timestamps** -- tracked per book per player and persisted to `data/rpg_lore_codex.dat`
- **Entity drop indexing** -- registry builds an immutable index at load time with exact-mob buckets, tag/biome buckets, and loot-table/advancement lookups, so mob deaths only evaluate applicable rules instead of scanning all books
- **Load resilience** -- if the books directory becomes unreadable, the previous catalog remains active instead of being replaced with an empty one
- **Duplicate handling naming** -- "duplicate prevention" terminology replaced with "duplicates" control offering two modes: store spares (default) or leave on ground
- **New server config options** -- `acquisition.enableEntityDrops` (default true), `acquisition.enableLootTables` (default true), `acquisition.enableAdvancements` (default true), `codex.enableDiscoveryHints` (default true), `reader.allowRunCommandClicks` (default false)
- **New client config options** -- `codex_display.rememberSearch` (default true) and `codex_display.showUnreadMarkers` (default true)
- **Two-layer reload workflow** -- `/reload` rescans both datapack and config; `/rpglore reload` rescans config only and merges with the existing datapack layer
- **Auto-regenerating config README** -- `config/rpg_lore/books/_README.txt` regenerates when documentation version increments, keeping field docs current without user intervention
- **Ribbon controls for Codex browsing** -- category, filter, and sort are now interactive red ribbon tabs that stick out from the book's top-right edge, each showing its role and current value (e.g. "Category: All", "Filter: Collected", "Sort: Default"); left-click cycles forward, Shift-click cycles backward, and all ribbons remain keyboard-accessible and remembered when returning from a book
- **Codex header layout with 8 books per page** -- removing the button row reflowed the header so the book list now displays eight entries per page instead of seven
- **Codex progress counter removed** -- the "x / y" collected-count display at the top of the parchment is removed; only the bottom page counter remains
- **Spare-copy indicator simplified** -- the duplicate-count display on each row now shows only the copy icon with spare count in tooltip, removing the textual "×n" annotation
- **Read action replaced with icon** -- the "Read" text link on each Codex entry is replaced by a small open-book icon with tooltip (new translation key `rpg_lore.codex.open_book`, added to all 60 locales)
- **Title truncation with ellipsis** -- row titles receive approximately 30 px additional width, and any title that still does not fit is truncated with "..." instead of being clipped mid-glyph
- **Textured duplicate-handling icon button** -- the spare/duplicate-prevention toggle is now a styled icon drawn from `textures/gui/codex.png` (stacked books with a green tick = store spares; single book with a red cross = leave duplicates on the ground) instead of a vanilla button, keeping the same tooltip and behavior
- **Styled search field with magnifier glyph** -- the search input gained a magnifier glyph and thin underline so it reads as an input field on the parchment rather than a plain vanilla box
- **Ribbon translation keys for all 60 locales** -- three new translation keys `rpg_lore.codex.ribbon.category`, `rpg_lore.codex.ribbon.filter`, `rpg_lore.codex.ribbon.sort` (format "Category: %s" etc.) are added to all 60 locale files (translated where the locale already translates the Codex controls, English elsewhere); very long category names are automatically trimmed with the full label preserved in the tooltip
- **Curios slot icon texture** -- the "codex" slot's empty-slot icon is a 16x16 greyscale silhouette of the Lore Codex item, following Curios's convention for empty-slot icons; `data/rpg_lore/curios/slots/codex.json` points `icon` to `rpg_lore:slot/empty_codex_slot`.

## [2.1.2] - 2026-09-03

### Bug Fixes
- **Soulbound Codex properly restored through death** -- the Codex is now parked in persistent tracking data during `onLivingDeath` (before the inventory is emptied into drops) and restored on respawn via `onPlayerClone` or on the next login via `onPlayerLogin`, preserving the Curios slot it was equipped in when possible. `keepInventory` skips stashing entirely (vanilla and Curios already carry everything over). A safety net in `onPlayerDrops` catches any Codex that reaches the drop list.

### Improvements
- **Curios slot integration hardened** -- new `extractCodexFromCurios` and `equipCodexInCurios` helpers in `CuriosCompat` improve slot tracking and restoration.
- **Creative tab no longer reads unloaded server config** -- guarded `codex.enabled` access with `SPEC.isLoaded()` when the tab builds outside a world.
- **Codex screen components built once** -- title, "Read" label, and uncollected-title components are now built in the constructor instead of every render frame.
- **Curios slot properly localized** -- new translation key `curios.identifier.codex` adds localization support for all 60 languages.

## [2.1.1] - 2026-07-11

### Critical Bug Fixes
- **Soulbound Codex no longer destroyed on death** -- by the time `LivingDropsEvent` fires, the dying player's inventory has already been emptied into the drops list, so removing the Codex from the drops deleted it outright; the old `PlayerEvent.Clone` handler then scanned an already-empty inventory. The Codex is now parked back in the dead player's inventory during the drops event and restored to the respawned player from there. (Soulbound persistence previously only *appeared* to work with `keepInventory` on, where it does nothing.)
- **Curios "codex" slot is now actually assigned to players** -- `data/rpg_lore/curios/entities/codex.json` listed the player entity but omitted the required `"slots"` array, so the dedicated Codex slot never attached and the Curios integration was inert.

### Bug Fixes
- **Duplicate-prevention toggle now appears on the first Codex open** -- the first open of a session built the screen from empty placeholder data (the server sync arrives only after the same right-click that opens the screen), and the sync handler patched labels without creating widgets. `refreshData` now rebuilds the widget set via vanilla `rebuildWidgets()`, and the server additionally resyncs Codex data on every login so the first open shows the real collection immediately.
- **No more Codex duplication with `keepInventory`** -- the death-restore handler now skips entirely when `keepInventory` is on (vanilla and Curios already carry everything over) and when the respawned player already has a Codex, closing a duplication path for Curios-equipped Codices.
- **Localization actually used** -- the Codex item name and the Codex screen's title, "Read" action, and "???" placeholder were hardcoded in English, bypassing the translations shipped in all 60 language files; they now use their existing translation keys.
- **Creative tab no longer risks reading unloaded server config** -- guarded `codex.enabled` access with `SPEC.isLoaded()` when tab contents build outside a world.

### Improvements
- **Dedicated-server classloading safety** -- clientbound packet handlers no longer reference client-only classes directly; collection-event handling moved into the `@OnlyIn(CLIENT)` `LoreCodexClientHelper` and all clientbound handlers route through `DistExecutor`, matching the pattern used elsewhere in the mod.
- **Line-ending normalization** -- added `.gitattributes` enforcing LF for text files (a stray CRLF conversion had previously corrupted the working tree, including the `gradlew` launcher, which must be LF to run on Linux/macOS).

## [2.1.0] - 2026-06-14

### New Features
- **Duplicate lore books are absorbed as banked spare copies** -- picking up a lore book always feeds it into the Codex instead of cluttering the inventory. The first copy becomes a permanent, readable **master** entry; every additional duplicate is **banked as a spare copy** (`CodexTrackingData.bookCopies`, persisted per book). A lore book now only exists in physical form when it is dropped on the ground or deliberately extracted from the Codex.
- **Extraction is gated on the spare bank** -- the Codex "copy" action now draws down a banked spare and is blocked when none remain (the master is never consumed). The catalog shows the available spare count per book (`C(n)`) and greys the button at zero. Spare counts are capped at 99 per book to guard against unbounded save growth.
- **Distinct duplicate absorption feedback** -- absorbing a book plays the absorption sound; duplicates use a lower-pitch variant and a separate "Spare copy stored" message so banking is audibly distinct from collecting a new entry.

### Improvements
- **Prevent-duplicates toggle repurposed to "leave on ground"** -- ON leaves duplicate books on the ground (unchanged from the old block behavior); OFF (default) absorbs them into the spare bank. UI tooltip and Codex item tooltip text updated to match.
- **Extraction centralized in `CodexService.extractCopy`** -- the copy packet now routes through the service so the spare-count mutation resyncs SavedData, item NBT, and the client UI atomically (the old path bypassed `syncAll`, leaving the UI count stale).
- **`/rpglore codex give` pre-syncs the tooltip cache** -- a freshly given Codex now shows the recipient's real collected/total counts instead of `0 / 0`.
- **Removing a book also discards its banked spares** -- `/rpglore codex remove` and pruning now clear orphaned spare copies, keeping collection and copy banks consistent.

## [2.0.6] - 2026-04-16

### New Features
- **Lore Codex storable in Chiseled Bookshelves** -- the Codex can now be placed alongside regular lore books via the `minecraft:bookshelf_books` item tag.
- **Lore Codex placeable on Lecterns** -- right-click an empty lectern while holding the Codex to place it. Right-clicking a Codex-holding lectern opens the Codex GUI instead of vanilla's empty-book reader (the Codex has no `pages` NBT). The GUI shows the interacting player's own synced collection, consistent with in-hand `LoreCodexItem#use`.

### Improvements
- **Data-generation scaffolding** -- `ModItemTagsProvider` now also emits `rpg_lore:lore_codex` into `minecraft:bookshelf_books`. Hand-written JSON remains authoritative (datagen still blocked by the pre-existing Curios mixin/mappings clash).

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
