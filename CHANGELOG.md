# Changelog

## [2.0.1] - 2026-03-26

### Localization
- Added 58 language files covering 60 locales total, including all European languages, top 10 Asian languages, top 10 African languages, plus Japanese, Korean, Filipino, and Traditional Chinese

### Art
- Updated Lore Book item texture
- Updated Lore Codex item texture

## [2.0.0] - 2026-03-26

### New Feature: Lore Codex
- **Lore Codex item** -- a soul-bound personal collection tracker for lore books
- **Auto-collection** -- picking up a lore book for the first time automatically registers it in your Codex
- **Soul-binding** -- the Codex is granted on first login and persists through death; cannot be dropped when soul-bound
- **Browsable GUI** -- custom screen with paginated book list, search filtering, collection counter (n/N), and per-book Read/Copy buttons
- **Duplicate prevention toggle** -- when enabled, prevents picking up lore books already recorded in the Codex
- **Copy mechanic** -- create a physical copy of any collected book (consumes one book from inventory, increments generation)
- **Codex commands** -- `/rpglore codex give/reset/add/remove/status` for server administration
- **Self-service collection view** -- `/rpglore collection` (no OP required) shows your own collected books
- **Full networking** -- Forge SimpleChannel with server-authoritative data; client never modifies tracking
- **7 server config options** -- enable/disable Codex, soul-binding, auto-collect, first-join grant, copy, duplicate prevention, reveal uncollected names
- **2 client config options** -- collection notification and sound toggles

### New Book Definition Fields
- `show_glint` (bool) -- per-book enchantment glint toggle (default: true)
- `category` (string) -- optional category for grouping books in the Codex and `/rpglore list`
- `codex_exclude` (bool) -- exclude a book from appearing in the Codex

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
