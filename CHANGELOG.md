# Changelog

## [1.1.0] - 2026-03-23

### Bug Fixes
- Fixed per-player copy limit logic — `max_copies_per_player` now works correctly for all values, not just 1
- Fixed `/rpglore reload` erasing per-player copy tracking data
- Fixed page navigation desync in lore book screen when rapidly clicking forward on the last page
- Fixed loot modifier creating a new `Random` instance per mob kill — now uses the loot context's seeded random

### Improvements
- Per-player copy tracking now persists across server restarts via world SavedData
- `/rpglore give` now records against per-player copy limits for consistency
- Book weight is now validated at load time — zero or negative weights are clamped to 0.01 with a warning
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
