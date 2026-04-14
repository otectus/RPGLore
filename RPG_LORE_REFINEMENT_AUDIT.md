# RPG Lore Refinement Audit

Scope: reviewed all Java sources under `src/main/java`, packaged resources under `src/main/resources`, the Curios integration assets, and build wiring. Verification performed with `./gradlew compileJava` and `./gradlew build`; both succeeded. There are currently no automated tests or GameTests in the project, so all findings below are from static review plus build validation.

## Critical Bugs

### 1. First-join Codex grants can be lost permanently when inventory is full
Refs: `src/main/java/com/rpglore/codex/CodexEventHandler.java:101-105`

Impact: a player can be marked as having received their starter Codex even though no item was actually delivered. After that, they will never be auto-granted one again.

Root cause: `serverPlayer.getInventory().add(codex)` ignores the return value, and `markCodexGranted` is called unconditionally.

Recommended fix: use the same inventory-or-drop fallback that the admin give command already uses, and only call `markCodexGranted` after the item has either entered inventory or been safely dropped with ownership/pickup metadata.

### 2. `maxBooksPerKill` is bypassed by books that define `base_chance`
Refs: `src/main/java/com/rpglore/loot/LoreBookLootModifier.java:91-117`

Impact: a single kill can drop more books than the server-configured maximum whenever multiple matching definitions use `base_chance`. This also makes balancing highly unpredictable.

Root cause: books with `base_chance` are rolled independently after the capped selection path has already run, so they are never counted against `MAX_BOOKS_PER_KILL`.

Recommended fix: unify all matching books into one post-roll candidate list, then enforce `MAX_BOOKS_PER_KILL` across the combined result. If `base_chance` must remain an override, treat it as an eligibility roll, not as a second uncapped drop pipeline.

### 3. Codex progress counts can become impossible or misleading
Refs: `src/main/java/com/rpglore/codex/LoreCodexItem.java:103-114`, `src/main/java/com/rpglore/network/ClientboundCodexSyncPacket.java:38-66`, `src/main/java/com/rpglore/codex/CodexTrackingData.java:112-117`, `src/main/java/com/rpglore/command/RpgLoreCommands.java:196-212`, `src/main/java/com/rpglore/command/RpgLoreCommands.java:237-263`

Impact: players can see `collected > total`, hidden books counted in progress, or stale `(unknown)` entries after reloads. This breaks the core collection loop.

Root cause: collected counts are based on the raw saved ID set, while catalog and totals are filtered to `!codexExclude()`. Stale pruning only removes unknown IDs, not now-excluded ones. Admin commands can also inject excluded books directly into Codex data.

Recommended fix: always compute Codex counts from `collected ∩ currentEligibleIds`. Reject `codex_exclude` books in `/rpglore codex add`. During startup and `/rpglore reload`, prune Codex entries against the eligible set, not just the loaded-ID set.

### 4. Soulbound death handling can delete extra Codices
Refs: `src/main/java/com/rpglore/codex/CodexEventHandler.java:43-65`, `src/main/java/com/rpglore/codex/CodexEventHandler.java:68-74`

Impact: if a player has more than one Codex, only the first one found is restored on respawn while all Codex drops are removed. Additional copies are silently destroyed. A Curios-held Codex can also be lost if reinserted into a full inventory.

Root cause: `onPlayerClone` returns after restoring the first inventory Codex, while `onPlayerDrops` removes every Codex entity. The Curios branch does not check the result of `newInv.add(...)`.

Recommended fix: either enforce a single-Codex invariant everywhere, or restore all owned Codices deterministically. Do not remove Codex drops unless the replacement path succeeded.

## Major Issues

### 1. Copying from the Codex can create an unpickable dropped book
Refs: `src/main/java/com/rpglore/network/ServerboundCodexCopyBookPacket.java:56-64`, `src/main/java/com/rpglore/codex/CodexEventHandler.java:137-145`

Impact: when inventory is full, a copied book is dropped on the ground. If duplicate prevention is enabled, the player can then be blocked from picking up the very copy they just created.

Root cause: overflow copies are dropped as ordinary lore books, and the pickup handler does not distinguish Codex-generated copies from world drops.

Recommended fix: reject the copy action when inventory is full, or mark Codex-generated copies with an exemption tag that bypasses auto-collect/duplicate-prevention logic on first pickup.

### 2. `CODEX_ENABLED` does not actually disable the Codex feature "entirely"
Refs: `src/main/java/com/rpglore/config/ServerConfig.java:58-60`, `src/main/java/com/rpglore/RpgLoreMod.java:65-68`, `src/main/java/com/rpglore/codex/LoreCodexItem.java:57-76`, `src/main/java/com/rpglore/command/RpgLoreCommands.java:67-104`

Impact: the item still appears in creative tabs, Codex commands remain registered, and the item can still be used even when the config says the feature is disabled.

Root cause: the config flag is only checked in event handlers, not in registration-time presentation, command registration, or item use logic.

Recommended fix: either gate all Codex-facing behavior behind `CODEX_ENABLED` or rename the config to reflect what it really does today.

### 3. Client notification and sound config options are dead
Refs: `src/main/java/com/rpglore/config/ClientConfig.java:25-33`, `src/main/java/com/rpglore/codex/CodexEventHandler.java:159-169`

Impact: `showCollectionNotification` and `playCollectionSound` never affect behavior. Players cannot actually disable collection messages or sounds.

Root cause: collection feedback is triggered on the server with `displayClientMessage` and `level().playSound(...)`, while the client config is never consulted anywhere except the lore-id tooltip toggle.

Recommended fix: send a dedicated clientbound collection event and let the client decide whether to show the action-bar message and play the sound locally.

### 4. Codex item NBT cache drifts out of sync with real state
Refs: `src/main/java/com/rpglore/network/ServerboundCodexToggleDuplicatePacket.java:23-38`, `src/main/java/com/rpglore/command/RpgLoreCommands.java:217-314`, `src/main/java/com/rpglore/codex/LoreCodexItem.java:103-114`

Impact: the item tooltip can show stale counts or stale duplicate-prevention state after toggles, admin commands, or reloads.

Root cause: Codex state is duplicated across saved data, client sync packets, and item NBT. Many write paths update only one or two of those representations.

Recommended fix: centralize Codex mutations behind a single service method that updates saved data, resyncs all held Codices for the affected player, and refreshes the active client screen if necessary.

### 5. `/rpglore reload` does not prune or resync player state
Refs: `src/main/java/com/rpglore/command/RpgLoreCommands.java:108-115`, `src/main/java/com/rpglore/RpgLoreMod.java:83-88`

Impact: removed or newly excluded books can remain in tracking data until a full server restart, and players keep stale Codex tooltip totals until some later event touches them.

Root cause: prune logic only runs on `ServerStartingEvent`; the reload command only reloads the registry map.

Recommended fix: after every reload, rerun both tracking-data prune passes, then resync affected Codex items and open Codex screens for online players.

### 6. The custom Lore Book screen drops vanilla interactive text behavior
Refs: `src/main/java/com/rpglore/lore/LoreBookScreen.java:134-154`, `src/main/java/com/rpglore/lore/LoreBookScreen.java:213-235`

Impact: hover events, click events, and other advanced JSON text-component interactions are not preserved inside lore books, even though the content system accepts JSON components.

Root cause: the screen fully replaces `BookViewScreen.render()` and only draws split lines. It never rebuilds or uses vanilla's component-style hover/click handling path.

Recommended fix: either extend vanilla rendering more faithfully, or copy the missing hover/click logic from `BookViewScreen` so interactive page content still works.

### 7. Soulbound and ownership semantics are only partially implemented
Refs: `src/main/java/com/rpglore/config/ServerConfig.java:62-64`, `src/main/java/com/rpglore/codex/CodexEventHandler.java:76-83`, `src/main/java/com/rpglore/codex/CodexEventHandler.java:101-103`, `src/main/java/com/rpglore/command/RpgLoreCommands.java:293-295`

Impact: the config comment says the Codex cannot be dropped or traded, but the code only blocks manual tosses and death drops. The `codex_owner` tag is written but never enforced.

Root cause: no ownership checks exist on use, pickup, container transfer, or command-based distribution.

Recommended fix: either enforce owner-bound behavior consistently or remove the owner tag and weaken the config/docs so they match reality.

### 8. Localization coverage is much lower than the asset set suggests
Refs: `src/main/java/com/rpglore/codex/LoreCodexItem.java:40-97`, `src/main/java/com/rpglore/codex/LoreCodexScreen.java:121-194`, `src/main/java/com/rpglore/command/RpgLoreCommands.java:108-338`, `src/main/resources/assets/rpg_lore/lang/en_us.json:1-14`

Impact: the mod ships 60 language files, but a large amount of Codex UI and all commands are still hardcoded in English. Several existing lang keys are unused.

Root cause: many strings use `Component.literal(...)` instead of translation keys, and the UI does not actually consume keys like `rpg_lore.codex.counter`, `rpg_lore.codex.copy`, `rpg_lore.codex.read`, or `rpg_lore.codex.no_physical_copy`.

Recommended fix: move all player-facing UI and command text to translation keys, then add a simple lang parity/data validation step so translations cannot drift silently.

### 9. Input validation is too weak for a file-backed content system
Refs: `src/main/java/com/rpglore/config/BooksConfigLoader.java:208-295`, `src/main/java/com/rpglore/config/BooksConfigLoader.java:298-365`

Impact: bad configs can create unreadable books, hidden dead entries, or malformed drop rules that fail silently. Pack authors get poor feedback.

Root cause: the loader does not validate explicit `id`, does not clamp `base_chance`, does not validate `min_y <= max_y`, and silently drops invalid resource-location list entries. It also warns on long titles instead of enforcing safe bounds.

Recommended fix: validate and fail fast on invalid IDs and contradictory ranges, clamp numeric fields into expected ranges, and log every discarded resource/tag entry with file context.

## Optimizations

### 1. Lore matching is an O(n) full scan on every eligible mob death
Refs: `src/main/java/com/rpglore/config/LoreBookRegistry.java:31-38`

Impact: large lore packs will pay the full cost of testing every definition against every kill.

Recommended optimization: pre-index definitions by the most selective filters (`mob_types`, dimensions, biome tags) and keep a smaller fallback bucket for fully generic rules.

### 2. Codex sync packets send fields the UI never uses
Refs: `src/main/java/com/rpglore/network/ClientboundCodexSyncPacket.java:42-52`, `src/main/java/com/rpglore/network/ClientboundCodexSyncPacket.java:69-90`, `src/main/java/com/rpglore/codex/LoreCodexScreen.java:351-358`

Impact: every Codex sync serializes `author` and `category`, but the screen never renders or filters by either field.

Recommended optimization: either surface those fields in the UI, or stop transmitting them until the screen actually needs them.

### 3. Every Codex item stores the full collected ID list in NBT
Refs: `src/main/java/com/rpglore/codex/LoreCodexItem.java:103-114`

Impact: duplicate Codices carry duplicate copies of the same collection data, and every tooltip sync pushes more NBT than the item actually needs to render.

Recommended optimization: keep the authoritative collection in `SavedData` and cache only lightweight tooltip fields in the item NBT, or eliminate the cache entirely and drive the tooltip from a synced capability-style state object.

### 4. Concurrent collections add complexity without a clear need
Refs: `src/main/java/com/rpglore/codex/CodexTrackingData.java:26`, `src/main/java/com/rpglore/data/LoreTrackingData.java:18`

Impact: the code reads as though it is multi-threaded, but Minecraft/Forge gameplay logic here is effectively main-threaded. That makes the code harder to reason about for no clear gain.

Recommended optimization: replace `ConcurrentHashMap` structures with ordinary maps/sets unless a real async access path is introduced.

## Refactors

### 1. Centralize all Codex state transitions
Problem: collection adds, duplicate toggles, starter grants, admin mutations, and reloads all touch different subsets of saved data, NBT, packets, and UI.

Recommended refactor: introduce a `CodexService` that owns mutation methods such as `grantStarterCodex`, `collectBook`, `toggleDuplicatePrevention`, `addBookAdmin`, and `reloadCatalog`, and have those methods perform all required sync steps consistently.

### 2. Decide whether category is a real feature or dead weight
Refs: `src/main/java/com/rpglore/config/BooksConfigLoader.java:284-285`, `src/main/java/com/rpglore/lore/LoreBookItem.java:203-205`, `src/main/java/com/rpglore/codex/LoreCodexScreen.java:139-143`, `src/main/java/com/rpglore/codex/LoreCodexScreen.java:351-358`

Problem: categories are parsed, stored, synced, and carried around, but never displayed, filtered, or sorted.

Recommended refactor: either add category-driven grouping/search to the Codex UI or remove the field from the runtime model until the UI exists.

### 3. Remove dead ownership state or actually use it
Refs: `src/main/java/com/rpglore/codex/CodexEventHandler.java:101-103`, `src/main/java/com/rpglore/command/RpgLoreCommands.java:293-295`

Problem: `codex_owner` is written in two places and read nowhere.

Recommended refactor: if ownership matters, enforce it in use/pickup/transfer logic. If it does not matter, delete the tag entirely.

### 4. Either add real data generators or remove the dead datagen wiring
Refs: `build.gradle:45-52`

Problem: the project advertises a data run and includes `src/generated/resources` as a resource source, but there are no actual data providers to keep loot modifiers, lang files, or Curios assets in sync.

Recommended refactor: add providers for lang/models/Curios item tags/loot assets, or strip the dead datagen setup to reduce confusion.

### 5. Remove the copied Forge source snapshot from the repo root
Refs: `net/minecraftforge/event/entity/player/PlayerEvent.java`, `net/minecraftforge/event/entity/player/EntityItemPickupEvent.java`

Problem: the repo contains copied Forge source that is not compiled into the mod. It clutters code search and adds avoidable licensing/maintenance noise.

Recommended refactor: delete it, or move it into a clearly labeled `reference/` location outside the normal source tree.

## Compatibility Concerns

### 1. Curios slot data appears incomplete without a matching item tag
Refs: `src/main/resources/data/rpg_lore/curios/slots/codex.json:1-4`, `src/main/resources/data/rpg_lore/curios/entities/codex.json:1-3`

Risk: the dedicated Codex slot may exist but still reject the item if Curios expects an item tag for slot assignment. No such tag is present in the project resources.

Recommended fix: add the appropriate Curios item tag for the Codex slot and verify the slot is actually usable in a Curios-enabled dev environment.

### 2. Optional Curios integration uses a fragile direct-import pattern
Refs: `src/main/java/com/rpglore/compat/CuriosCompat.java:1-63`

Risk: the class directly imports Curios API types in common code. This usually works only if classloading remains lazy in exactly the right places.

Recommended fix: isolate Curios-only code behind a nested loader, reflection, or a dedicated integration entrypoint so missing Curios jars cannot break common-class loading.

### 3. The custom book screen is upgrade-fragile
Refs: `src/main/java/com/rpglore/lore/LoreBookScreen.java:40-48`, `src/main/java/com/rpglore/lore/LoreBookScreen.java:61-65`

Risk: it hardcodes private vanilla layout constants and shadows vanilla page state. Minor upstream changes can silently break rendering or navigation on future Minecraft/Forge ports.

Recommended fix: minimize copied vanilla behavior where possible, and keep a port checklist for this screen when upgrading versions.

### 4. Copy-limit tracking can disagree with other loot modifiers
Refs: `src/main/java/com/rpglore/loot/LoreBookLootModifier.java:139-144`

Risk: a book counts as "received" as soon as this modifier appends it to `generatedLoot`, even if a later modifier or mod removes it before the player ever sees it.

Recommended fix: if copy limits must reflect actual delivery, move tracking later in the pipeline or add a more explicit post-drop claim path.

### 5. Looting scaling is derived from the main hand only
Refs: `src/main/java/com/rpglore/loot/LoreBookLootModifier.java:79-85`

Risk: projectile kills, offhand combat, or modded weapon mechanics can produce the wrong looting bonus.

Recommended fix: use the loot-context or entity-based looting helper instead of reading only `player.getMainHandItem()`.

## Content Delivery Concerns

### 1. The mod promises advanced JSON component pages, but the custom reader only supports static rendering well
Refs: `README.md:112-118`, `src/main/java/com/rpglore/lore/LoreBookScreen.java:213-235`

Concern: rich page content is accepted, but hover/click interactions are not preserved. That limits what pack authors can safely ship.

Recommended fix: either document the supported subset clearly or restore full component interaction support.

### 2. The Codex UI ignores author and category, even though both are loaded and synced
Refs: `src/main/java/com/rpglore/network/ClientboundCodexSyncPacket.java:42-52`, `src/main/java/com/rpglore/codex/LoreCodexScreen.java:207-277`

Concern: authors and categories are part of the content model but do not improve discoverability or presentation in the UI.

Recommended fix: add category grouping/filtering and optional author lines or tooltips, or simplify the data model until those features exist.

### 3. A large part of the shipped language pack is not actually used
Refs: `src/main/resources/assets/rpg_lore/lang/en_us.json:4-14`, `src/main/java/com/rpglore/codex/LoreCodexScreen.java:160-194`, `src/main/java/com/rpglore/codex/LoreCodexItem.java:84-97`

Concern: the asset pack advertises broad localization support, but the most visible Codex strings still come from hardcoded literals.

Recommended fix: finish the localization pass before adding more locales. A smaller, actually used lang set is better than a large mostly cosmetic one.

### 4. Reload-time content changes are not reflected consistently in player-facing systems
Refs: `src/main/java/com/rpglore/command/RpgLoreCommands.java:108-115`, `src/main/java/com/rpglore/codex/LoreCodexItem.java:103-114`

Concern: book removals, new exclusions, or title changes can leave players with stale Codex tooltips and stale collection data until some unrelated event happens.

Recommended fix: make reload an end-to-end content refresh: registry reload, stale-prune, Codex/item resync, and open-screen refresh.

## Recommended Next Steps

1. Fix the state-integrity bugs first: starter grant delivery, combined drop-cap enforcement, eligible-count filtering, and multi-Codex death handling.
2. Collapse Codex state syncing into one code path, then patch every mutation site to use it.
3. Make the content/UI layer honest: finish localization, either implement or remove category/ownership semantics, and restore interactive book components if JSON pages are meant to support them.
4. Verify Curios integration in a Curios-enabled dev run and add the missing slot assignment data if required.
5. Add regression coverage. The highest-value automated checks are:
   - a drop-cap test covering `base_chance` plus non-override books
   - a reload test covering removed and newly excluded books
   - a Codex grant test with a full inventory
   - a duplicate-prevention test covering copied books and inventory overflow
   - a death-respawn test covering inventory plus Curios-held Codices
