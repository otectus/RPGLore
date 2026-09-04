# RPG Lore 2.2.0 — Refinement & Discovery Specification

**Project:** RPG Lore  
**Repository:** `otectus/RPGLore`  
**Target release:** **2.2.0**  
**Primary platform:** Minecraft 1.20.1, Forge 47.x, Java 17  
**Scope:** Refinement, quality-of-life, discovery, authoring ergonomics, compatibility, performance, and reliability  
**Document purpose:** Implementation specification for a coding agent

---

# 1. Executive Summary

RPG Lore already has a strong and unusually coherent identity: pack authors define collectible lore books as data, those books enter the world according to environmental conditions, and players gradually build a persistent personal library in the Lore Codex. The current system already supports configurable mob drops, custom book presentation, per-player collection tracking, duplicate banking, Curios integration, lecterns, chiseled bookshelves, commands, and localization. 

The next release should **not** turn RPG Lore into another Patchouli-style general-purpose documentation system. Its differentiating purpose should remain:

> **Discover pieces of a world's history through play, then organize and revisit those discoveries through a persistent Codex.**

The best 2.2.0 update is therefore a **Refinement & Discovery** release built around six priorities:

1. **Make the Codex a genuinely useful collection browser.**
   Add categories, search, sorting, filters, unread state, favorites, better duplicate-copy controls, keyboard navigation, and contextual metadata.

2. **Make discovering lore broader than killing mobs.**
   Retain all existing entity-drop behavior while adding controlled support for chest/loot-table and advancement-based acquisition.

3. **Make pack authoring dramatically easier to diagnose.**
   Introduce structured validation, file/path-aware errors, a `/rpglore validate` command, better reload reports, and safer reload behavior.

4. **Support datapack-distributed lore in addition to legacy config files.**
   This enables modpacks, servers, world datapacks, and compatibility packs to ship lore without modifying the user's config directory.

5. **Harden the underlying architecture.**
   Separate common network DTOs from client screen classes, split stable catalog data from frequently changing player state, improve registry indexing, and add real regression tests.

6. **Preserve compatibility.**
   Existing JSON definitions and existing player Codex saves must continue to work without manual migration.

This is deliberately a refinement release. Features such as narrated lore audio, arbitrary command execution, a Markdown engine, recipes, multiblock visualization, quest systems, or a general-purpose guidebook framework should remain outside 2.2.0.

---

# 2. Mandatory Pre-Implementation Baseline Reconciliation

## 2.1 Repository/release mismatch

Before implementing any 2.2.0 work, reconcile the source repository with the newest released build.

The current `main` branch identifies itself as **2.1.1** for Minecraft 1.20.1 / Forge 47.3.0. 

However, the public Forge release line has advanced to **2.1.2**. The 2.1.2 release includes additional fixes involving:

- Codex death persistence.
- Crash-safe restoration behavior.
- Curios-slot restoration after respawn.
- Localized Curios slot presentation.
- Creative-tab behavior when the Codex is disabled.
- Reduced per-frame Codex text work.



### Required action

**Do not begin 2.2.0 feature development on the current 2.1.1 repository state.**

First:

1. Obtain/reconstruct the exact 2.1.2 Forge source changes.
2. Apply them to `main`.
3. Verify the repository builds as the released 2.1.2 baseline.
4. Update `gradle.properties`, `CHANGELOG.md`, and any metadata necessary to accurately identify that baseline.
5. Tag or otherwise preserve the reconciled baseline before starting 2.2.0.

### Acceptance criteria

- `main` contains every behavior shipped in Forge 2.1.2.
- No 2.2.0 commit accidentally reintroduces a bug fixed in 2.1.2.
- `./gradlew build` passes before feature work begins.
- Curios and non-Curios death/respawn behavior is manually verified before continuing.

---

# 3. Current Architecture Assessment

## 3.1 Strengths to preserve

RPG Lore's existing design has several strong foundations.

### Data-driven content

Lore definitions are external JSON rather than hard-coded Java. The loader already validates IDs, generation values, weights, page counts, colors, Y ranges, drop chances, and other common configuration errors. 

This is the correct foundation and should be expanded rather than replaced.

### Server-authoritative collection state

The Codex uses persistent server-side saved data and a centralized `CodexService`. Collection changes, duplicate banking, extraction, removal, reset, duplicate handling, grants, pruning, and synchronization are already routed through that service. 

Keep that pattern.

### Physical-world integration

Lore books and the Codex work with lecterns and chiseled bookshelves rather than existing solely inside a custom GUI. This helps RPG Lore feel like a Minecraft system rather than an external menu.

### Collection-oriented duplicate handling

The 2.1.x spare-copy bank is a good distinction from simple guidebook mods. First copies become permanent collection entries; duplicates become extractable inventory. Preserve this model.

### Soft Curios integration

Curios remains optional rather than becoming a hard dependency.

---

# 4. Current Gaps

## 4.1 Category support is only half implemented

`LoreBookDefinition` already contains a `category` field. 

The README describes categories as useful for grouping books in the Codex. 

But the current client synchronization deliberately omits both `author` and `category`, filling them with empty/null values because the UI does not use them. 

And `LoreCodexScreen.applyFilter()` is effectively a placeholder: it simply copies the entire catalog into `filteredEntries`. There is currently no search, actual filtering, category view, or sorting UI. 

**2.2.0 should finally complete the category feature rather than adding another dormant metadata field.**

---

## 4.2 Acquisition is too combat-centric

The current acquisition pipeline is fundamentally a `GlobalLootModifier` operating on dead `LivingEntity` loot contexts. All current environmental conditions ultimately answer the question:

> Should this mob death be eligible to drop this book?



That is excellent for monster lore, bestiaries, battlefield journals, etc., but too restrictive for:

- Ancient histories.
- Archaeological journals.
- Lost correspondence.
- Religious texts.
- Dungeon records.
- Explorer journals.
- Village histories.
- Dimension-specific records.
- Quest/progression rewards.

The mod needs a few additional **diegetic acquisition channels**, not a wholesale quest framework.

---

## 4.3 The Codex tracks possession, but not player intent

Persistent Codex state currently consists primarily of:

- Collected IDs.
- Spare-copy counts.
- Duplicate-pickup preference.
- Starter-Codex grant state.



There is no concept of:

- Unread vs. read.
- Favorites/bookmarks.
- Discovery order.
- Recently found books.
- Last-viewed book.
- Series completion.

These are inexpensive features with disproportionately large QoL value.

---

## 4.4 The book reader loses vanilla interactive text behavior

RPG Lore's custom `LoreBookScreen` recreates vanilla book rendering so it can use a custom texture and custom title page. However, it draws split lines directly and does not reproduce vanilla's complete hover/click handling for interactive text components. 

The format accepts JSON text components, so authors reasonably expect features such as hover text and safe click events to function.

This should be corrected.

---

## 4.5 Author diagnostics remain too weak

The loader catches parse failures and generally emits a filename plus `Exception#getMessage()`. 

There is already a real-world report in the repository where one book causes an "unexpected error" and the author specifically cannot find useful information in logs to diagnose the malformed content. 

For a mod whose primary audience includes pack authors writing JSON, diagnostics are part of the product.

---

## 4.6 Network design is beginning to couple unrelated layers

`ClientboundCodexSyncPacket` currently imports and constructs the nested client-screen type `LoreCodexScreen.CodexBookEntry`. 

Network/common model code should not depend on a client UI class.

Furthermore, `CodexService.syncAll()` sends the entire Codex sync after small mutations such as banking one duplicate. 

This works for small packs, but it becomes increasingly inefficient as authors add hundreds or thousands of books.

---

## 4.7 Matching still scales linearly with the entire catalog

`LoreBookRegistry.getMatchingBooks()` loops over every loaded definition and evaluates its conditions for each eligible mob death. 

For a dozen books this is irrelevant.

For a lore-heavy modpack containing hundreds or thousands of definitions and a mob-heavy server, this becomes avoidable background work.

---

## 4.8 Automated regression coverage is effectively absent

The project's Gradle configuration enables GameTest namespaces, but the repository's own audit notes that there are no actual automated tests/GameTests. 

The build already contains the hooks necessary to start using GameTests. 

Given RPG Lore's history of subtle persistence, networking, Curios, reload, and death bugs, 2.2.0 should establish regression tests rather than relying exclusively on manual inspection.

---

# 5. Comparative Mod Research

The purpose of this research is to identify proven UX patterns, not to copy unrelated feature sets.

## 5.1 Lore Expansion

Older releases of **Lore Expansion** used collection journals, numbered/missing lore slots, dimension-specific grouping, and replayable discoveries. Some releases also experimented with audio and command-triggering lore.

### Adopt

- Visible gaps/completion.
- Grouping lore into meaningful sets.
- Strong "collection journal" presentation.
- Ability to revisit old discoveries.

### Do not adopt in 2.2.0

- Arbitrary command execution from lore.
- Background audio playback.
- Audio-file management.

Those add security, compatibility, and scope complexity without addressing RPG Lore's current QoL gaps.

---

## 5.2 Lore Books

**Lore Books** revolves around finding pieces/pages of a story in generated loot and gradually assembling the story.

### Adopt

- Lore should be discoverable from world loot, not exclusively mobs.
- Ordered series can make collection progress more satisfying.
- Missing entries should communicate that a sequence is incomplete.

### Avoid

Do not require every RPG Lore story to use fragments/pages. Existing full books must remain first-class.

---

## 5.3 LootStories

**LootStories** demonstrates a straightforward approach to placing authored stories into chest loot with configurable probabilities and loot-table targeting.

### Adopt

Add native loot-table acquisition rules to RPG Lore.

This should complement, not replace, current entity-drop conditions.

---

## 5.4 Patchouli

Patchouli is the dominant example of mature Minecraft book navigation: nested categories, advancement-gated entries, bookmarks, reusable templates, localization, rich pages, and large-scale content organization.

Patchouli also demonstrates the usefulness of using vanilla advancements as a generic unlock mechanism.

### Adopt

- Categories.
- Strong entry navigation.
- Advancement-based unlock/acquisition rules.
- Bookmark/favorite state.
- Clear locked/unlocked presentation.
- Searchability.

### Explicitly do not adopt

- Recipe documentation.
- Multiblock visualization.
- General guidebook APIs.
- Arbitrary custom page engines.
- A Patchouli dependency.

RPG Lore should stay a lore collection mod.

---

## 5.5 GuideME

GuideME demonstrates several excellent modern document-browser QoL patterns: full-text navigation, cross-links, contextual entry jumping, link tooltips, smooth browsing, and extensibility.

### Adopt selectively

- Search field.
- Rich link/hover handling.
- Returning users to their previous browsing context.
- Clean separation between content model and rendering.

### Defer

- Markdown.
- Recipe indexing.
- 3D scenes.
- Contextual item documentation.
- General guide API.

---

## 5.6 Wanderer's Codex

Wanderer's Codex records discoveries and associated context, such as when and where exploration milestones occurred.

### Adopt selectively

Store a lightweight discovery order/time for newly collected lore so the Codex can support a **Recently Found** sort.

Do not expand RPG Lore into a general player-statistics journal.

---

## 5.7 Morrowless

Morrowless emphasizes environmental storytelling through recovered journals, documents, and clues found during exploration.

### Adopt

Use world exploration and vanilla loot/progression systems as additional paths for lore discovery.

---

## 5.8 Localized Written Books

Localized Written Books demonstrates the value of allowing written-book metadata/content to come from translation keys rather than forcing a single language into data files.

### Adopt partially

RPG Lore already permits JSON text components containing `translate` components. Improve documentation and examples for this.

Optional translation-key metadata can be considered, but it should not block 2.2.0.

---

## 5.9 Akashic Tome

Akashic Tome's principal value is aggregating many different documentation books into one "book of books."

RPG Lore's Codex already provides its own collection/aggregation layer.

**No equivalent feature is needed.**

---

# 6. Release Identity

Use the release theme:

# RPG Lore 2.2.0 — Refinement & Discovery

The release should answer four player-facing questions better than 2.1.x:

1. **What lore have I found?**
2. **What haven't I found?**
3. **How do I find the entry I want to reread?**
4. **Can lore appear naturally in more places than mob drops?**

And three pack-author-facing questions:

1. **Why isn't my book loading?**
2. **Why isn't my book appearing?**
3. **How can I distribute lore without editing a player's config folder?**

---

# 7. Compatibility Requirements

These requirements are mandatory.

## 7.1 Existing book JSON

Every valid 2.1.x book definition must continue to load unchanged.

Do not require a `format_version` field for existing files.

If `format_version` is added, absent means legacy/current format.

---

## 7.2 Existing saves

Existing:

- Collected books.
- Spare-copy counts.
- Duplicate-handling preferences.
- Starter-Codex status.

must migrate automatically.

No reset command should be required.

---

## 7.3 Existing commands

Existing `/rpglore` commands must remain functional unless explicitly deprecated with a compatibility alias.

---

## 7.4 Existing physical lore books

Already-generated physical `rpg_lore:lore_book` items should remain readable.

Do not migrate every existing item merely to add new metadata.

---

## 7.5 Existing Curios behavior

All 2.1.2 Curios behavior must survive unchanged.

Curios must remain optional.

---

# 8. Workstream A — Complete the Lore Codex

This is the highest-value player-facing workstream.

---

## 8.1 Search

Add a search field to `LoreCodexScreen`.

Search locally against already-synced catalog data.

### Searchable fields

For collected books:

- Title.
- Author.
- Category.
- Search tags.

For uncollected books:

- Search title/author only when `revealUncollectedNames=true`.
- Never allow search to reveal hidden metadata when names are supposed to remain secret.

### Behavior

- Case-insensitive.
- Trim leading/trailing whitespace.
- Search updates as the player types.
- Do not send network packets per keystroke.
- `Ctrl+F` focuses the search box.
- Empty query restores the normal view.

### Non-goal

Do **not** full-text index every book page in 2.2.0.

Catalog metadata search is sufficient and substantially cheaper.

---

## 8.2 Categories

Make the existing `category` field functional.

### Required behavior

- Display an **All** category.
- Dynamically derive category choices from the loaded catalog.
- Books without a category appear under **Uncategorized**.
- Selecting a category filters entries.
- Show category-specific progress, e.g. `8 / 14`.
- Category names should sort alphabetically by default.
- Preserve the current category when returning from a book.

No new JSON format is required for basic category support.

---

## 8.3 Filters

Add:

- **All**
- **Collected**
- **Unread**
- **Favorites**
- **Missing**

Filters combine with search and category.

Example:

`History + Unread + "king"`

should show unread collected History entries matching "king".

---

## 8.4 Sorting

Support:

- **Default**
- **Title**
- **Category**
- **Recently Found**

### Default sort

Recommended:

1. Unread collected.
2. Other collected.
3. Missing.
4. Alphabetical within each group.

This makes new discoveries visible without making the Codex feel chaotic.

---

## 8.5 Unread state

Add persistent read state.

### Rules

- Newly collected books start as **unread**.
- Opening the book marks it read.
- Reading either from the Codex or a physical copy should mark the corresponding known lore ID as read when practical.
- Unread books receive a subtle visual marker.

### Save migration

Existing saves do not contain read state.

On first migration:

> Every book already collected before the 2.2.0 migration must be initialized as **read**.

Otherwise veteran players would suddenly receive hundreds of false "new" entries.

---

## 8.6 Favorites

Allow players to favorite collected books.

Persist favorites server-side.

### UX

Use a star/bookmark icon with a textual tooltip.

Do not depend exclusively on color to convey favorite state.

Favorites should be available as a filter.

---

## 8.7 Discovery order

Store a discovery ordinal or game-time value when a book is first collected.

Purpose:

- `Recently Found` sorting.
- Optional "Found recently" metadata.
- Future-proofing.

Do not attempt to reconstruct precise historical dates for old entries.

Migrated entries can use `0`/unknown and sort after entries with real discovery metadata.

---

## 8.8 Better spare-copy presentation

Current `C(n)` presentation is unnecessarily cryptic.

Replace or augment it with something like:

- `×3` beside a small copy icon, with tooltip **3 spare copies**.
- Or a textual `Spare ×3` if texture space allows.

When no spare exists:

- Disable extraction.
- Tooltip: **No spare copies available.**
- Never imply the permanent Codex master is being consumed.

---

## 8.9 Rename "duplicate prevention" in the UI

Current behavior is not really duplicate prevention anymore.

The setting determines what happens to duplicate pickups:

- **Store as spare copies**
- **Leave duplicates on ground**

Use terminology such as:

**Duplicate handling**

rather than:

**Duplicate prevention**

Retain old internal config/save keys if doing so simplifies backward compatibility.

The current server configuration comments also contain stale language implying Codex copying consumes a physical copy; update these descriptions to match the spare-bank implementation. 

---

## 8.10 Entry tooltips

Hovering a Codex entry should be able to show:

For collected books:

- Full title if visually truncated.
- Author.
- Category.
- Read/unread status.
- Spare count.
- Favorite status.

For missing books:

- `???` when names are hidden.
- Optional discovery hint, if defined and allowed.
- Category if revealing the category does not undermine intended secrecy.

---

## 8.11 Keyboard QoL

Where possible:

- `Ctrl+F` → focus search.
- `Esc` → close or return.
- Left/right arrow → previous/next Codex page when search box is not consuming the key.
- Enter → open selected/highlighted collected entry.
- Tab → normal widget navigation.

All important actions must remain mouse-accessible.

---

## 8.12 Optional Codex keybind

Add an unbound-by-default:

**Open Lore Codex**

key mapping.

### Rules

- Default: unbound, avoiding modpack key collisions.
- Server must verify the player currently possesses a Codex in inventory or Curios.
- The key cannot magically access the collection without the item unless a future config explicitly allows it.
- A Codex placed on a lectern does not count as possessed.

---

# 9. Workstream B — Better Reading Experience

## 9.1 Restore interactive text components

Preserve RPG Lore's custom book texture and generated title page, but restore vanilla behavior for interactive JSON components.

Support the same safe interaction semantics available in vanilla book pages, including:

- Hover events.
- Styled text detection under the pointer.
- Appropriate click events.
- Tooltips produced by component hover styles.

Do not invent a custom scripting mechanism.

### Implementation direction

When recreating the vanilla `BookViewScreen` rendering path, also reproduce or delegate to the vanilla logic that:

- Determines the `Style` under the mouse.
- Renders component hover effects.
- Passes clicked component styles to the normal screen click handler.

The current screen only draws pre-split text and therefore does not provide this path. 

---

## 9.2 Return to Codex after reading

Current Codex reading should become a true browse→read→browse loop.

When a player opens a book from the Codex:

1. Capture current Codex UI state.
2. Open the lore book.
3. Closing the lore book returns to the Codex.
4. Restore:
   - Search query.
   - Category.
   - Filter.
   - Sort.
   - Page.
   - Preferably selected entry.

Physical books opened from inventory should continue closing normally.

### Suggested client type

`CodexViewState`

Containing immutable/client-only navigation state.

This does not need server persistence.

---

## 9.3 Long-title handling

Keep title-page scaling, but improve extreme cases.

If a title still cannot fit legibly on one line:

- Wrap to a maximum of two title lines.
- Recalculate vertical placement.
- Never scale text to an unreadably tiny size merely to fit.

The existing loader may continue warning on exceptionally long titles.

---

## 9.4 Authoring example for interactive text

Add documentation showing:

- Hover text.
- `suggest_command`.
- `copy_to_clipboard`.
- URLs if vanilla allows them under normal security behavior.
- `translate` components.

Clarify that RPG Lore follows vanilla component behavior rather than creating a separate markup language.

---

# 10. Workstream C — Persistent Codex State Extensions

Extend `CodexTrackingData.PlayerCodexData`.

## Add

```text
readBookIds
favoriteBookIds
discoveredAt / discoveryOrdinal
```

Exact collection types are implementation-defined.

---

## 10.1 Invariants

A favorite ID must also be collected.

A read ID must also be collected.

A discovery record should only exist for a collected book.

Removing/pruning a book clears:

- Collection membership.
- Spare copies.
- Read state.
- Favorite state.
- Discovery metadata.

Reset clears everything except the "starter Codex has previously been granted" flag unless current reset semantics intentionally include it.

---

## 10.2 Migration

On loading legacy player data:

- Existing `collected` entries → collected.
- Existing copies → unchanged.
- Existing duplicate preference → unchanged.
- Existing starter flag → unchanged.
- If the new `read` field is absent:
  - Populate `readBookIds` with all existing collected IDs.
- Missing favorite/discovery structures → empty/default.

Save the upgraded format naturally on the next dirty write.

---

# 11. Workstream D — Pack-Author Validation and Diagnostics

This should be considered a major 2.2.0 feature, not internal cleanup.

---

## 11.1 Extract a pure parser

Split parsing concerns out of `BooksConfigLoader`.

Recommended classes:

```text
LoreBookParser
LoreValidationReport
LoreValidationMessage
LoreBookSource
```

`LoreBookParser` should accept source text plus source metadata and produce either:

```text
Parsed LoreBookDefinition + warnings
```

or:

```text
errors
```

without mutating global registry state.

This makes validation unit-testable.

---

## 11.2 Structured diagnostic information

Every validation message should contain as much as possible:

- Severity: `INFO`, `WARNING`, `ERROR`.
- Source file/resource.
- Book ID if known.
- JSON field/path.
- Human-readable explanation.
- Expected value/type.
- Actual value/type.
- Line/column when supplied by the parser.
- Suggested correction when obvious.

Example:

```text
ERROR config/rpg_lore/books/fallen_kingdom.json:11:24
pages[2]
Invalid JSON string: unescaped quotation mark.
Expected the quote inside page text to be written as \".
Book was not loaded.
```

This directly addresses the real-world "unexpected error with no useful log" problem reported against the project. 

---

## 11.3 `/rpglore validate`

Add:

```text
/rpglore validate
/rpglore validate <book_id>
```

OP permission is appropriate by default.

### Full validation output

Summarize:

- Number of sources scanned.
- Valid books.
- Warnings.
- Errors.
- Overridden definitions.
- Unknown/deprecated fields.

Keep chat output concise and place detailed information in the server log.

---

## 11.4 Better reload reporting

`/rpglore reload` should report:

```text
RPG Lore reload complete:
124 loaded
3 added
2 changed
1 removed
2 warnings
0 errors
```

If there are errors:

```text
2 definitions were skipped. See latest.log or run /rpglore validate.
```

---

## 11.5 Safe reload semantics

A malformed individual book must not invalidate unrelated valid books.

For catastrophic source-layer failures:

- Prefer retaining the previous valid registry rather than replacing it with an unexpectedly empty registry.
- Log that the previous catalog remains active.

Normal intentional removal of all books should still be possible and must not be mistaken for a catastrophic failure.

Design the reload result so these cases are distinguishable.

---

## 11.6 Unknown fields

Warn about unknown top-level and acquisition-condition fields.

This catches common typos such as:

```json
"basechange": 0.2
```

instead of silently ignoring them.

Allow future extension namespaces if needed.

---

## 11.7 Generated `_README.txt`

The current `.defaults_generated` marker means generated starter documentation can remain permanently stale after the first run. 

Change this system.

Recommended:

- Preserve user-created example files.
- Version the generated documentation independently.
- Safely replace only RPG Lore's generated `_README.txt`.
- Include a generated documentation version marker.

Never overwrite a user's custom lore file merely because the mod updated.

---

# 12. Workstream E — Datapack Lore Definitions

This is the most important pack-author expansion.

Support:

```text
data/<namespace>/rpg_lore/books/<path>.json
```

Example:

```text
data/towns_and_dragons/rpg_lore/books/history/fall_of_ardath.json
```

Default ID:

```text
towns_and_dragons:history/fall_of_ardath
```

---

## 12.1 Source precedence

Support two definition layers:

1. **Datapacks**
2. **Legacy config directory**

Recommended precedence:

> Config definitions override datapack definitions with the same ID.

This lets server owners override a modpack-provided definition without editing the pack itself.

Every override must be logged explicitly:

```text
Config definition rpg_lore:ancient_battle overrides datapack definition from towns_and_dragons.
```

---

## 12.2 Preserve the legacy config folder

Do not migrate or delete:

```text
config/rpg_lore/books/
```

Existing setups depend on it.

---

## 12.3 Reload behavior

`/reload`:

- Reload datapack definitions.
- Re-merge with currently loaded config definitions.
- Rebuild indices.
- Prune stale Codex state as necessary.
- Resync clients if the catalog revision changed.

`/rpglore reload`:

- Reload config definitions.
- Re-merge against the most recent datapack layer.
- Perform the same validation/pruning/sync process.

---

## 12.4 Suggested architecture

```text
LoreBookParser
        |
        +-- ConfigLoreSource
        |
        +-- DatapackLoreSource
                 |
          LoreCatalogBuilder
                 |
          LoreBookRegistry
```

`BooksConfigLoader` should no longer be responsible for every aspect of file IO, parsing, registry ownership, and default generation.

---

## 12.5 Format version

Introduce optional:

```json
"format_version": 1
```

Rules:

- Missing = version 1.
- Unknown future version = clear validation error.
- Do not require authors to modify existing definitions.

---

# 13. Workstream F — Expanded Lore Acquisition

The goal is to broaden discovery while retaining RPG Lore's current behavior.

---

## 13.1 Preserve `drop_conditions`

Existing:

```json
"drop_conditions": { ... }
```

must continue behaving identically.

Internally it can eventually be represented as an entity-drop acquisition rule, but the old JSON syntax must remain supported.

---

## 13.2 Add optional `acquisition`

Proposed additive field:

```json
"acquisition": [
  {
    "type": "entity_drop",
    ...
  },
  {
    "type": "loot_table",
    ...
  },
  {
    "type": "advancement",
    ...
  }
]
```

A book may have more than one acquisition route.

---

# 14. Entity Drop Acquisition

New-form example:

```json
{
  "type": "entity_drop",
  "mob_tags": ["minecraft:undead"],
  "biome_tags": ["minecraft:is_overworld"],
  "time": "NIGHT_ONLY",
  "chance": 0.05,
  "max_copies_per_player": 1
}
```

Existing `drop_conditions` should be translated internally into this representation.

Do not force authors to rewrite old definitions.

---

# 15. Loot-Table Acquisition

Example:

```json
{
  "type": "loot_table",
  "loot_tables": [
    "minecraft:chests/simple_dungeon",
    "minecraft:chests/stronghold_library"
  ],
  "chance": 0.12,
  "weight": 1.0
}
```

Also support loot-table tags/pattern abstractions only if they can be implemented cleanly and predictably.

### Intended uses

- Dungeon journals.
- Archaeological records.
- Ruined-city documents.
- Stronghold histories.
- Village records.
- Dimension-specific treasure.

### Important semantic constraint

Container generation normally lacks a specific target player.

Therefore `max_copies_per_player` cannot reliably prevent generation in ordinary chest loot.

Do **not** pretend otherwise.

Document that per-player acquisition limits apply only where acquisition has an attributable player.

The Codex's duplicate/spare handling still governs what happens when the player eventually picks up the item.

---

# 16. Advancement Acquisition

Example:

```json
{
  "type": "advancement",
  "advancements": [
    "minecraft:adventure/sleep_in_bed"
  ],
  "delivery": "codex"
}
```

Supported delivery modes:

```text
codex
inventory
```

### `codex`

Directly collect the lore into the player's Codex.

If it is already collected, do not repeatedly add spare copies unless an explicit future option permits this.

### `inventory`

Give a physical copy, with sensible full-inventory fallback.

### Why advancements

Advancements already provide a generic Minecraft-native mechanism for:

- Entering biomes.
- Reaching structures.
- Killing bosses.
- Crafting items.
- Progression milestones.
- Custom modpack triggers.

Using them avoids building another quest/trigger framework.

Patchouli's advancement-gated content demonstrates how useful advancement integration can be for data-driven book systems.

---

# 17. Acquisition Features Explicitly Deferred

Do not add in 2.2.0:

- Arbitrary JavaScript/KubeJS snippets as acquisition rules.
- Arbitrary command execution.
- Custom quest objectives.
- Dialogue systems.
- Built-in structure detectors.
- Built-in biome discovery tracking separate from advancements.
- NPC conversation systems.

Other mods can integrate through the API described below.

---

# 18. Workstream G — Minimal Public Integration API

RPG Lore is now mature enough to expose a tiny stable integration surface.

Do not build a huge API.

---

## 18.1 Collection service

Expose a safe server-side method conceptually equivalent to:

```text
collect(player, loreId, source)
```

Return a result enum such as:

```text
NEWLY_COLLECTED
ALREADY_COLLECTED
NOT_FOUND
EXCLUDED
CODEX_DISABLED
```

Do not expose mutable internal maps.

---

## 18.2 Collection event

Fire a Forge event after a genuinely new collection:

```text
LoreCollectedEvent
```

Suggested fields:

```text
ServerPlayer player
ResourceLocation loreId
LoreAcquisitionSource source
```

Potential consumers:

- Quest mods.
- Advancement packs.
- Reputation systems.
- Analytics.
- Dialogue systems.
- Custom achievements.

---

## 18.3 Do not hard-depend on FTB Quests/Patchouli/etc.

The API should let those systems integrate without RPG Lore importing them.

---

# 19. Workstream H — Network and Model Refactor

This should happen before adding substantial new Codex metadata.

Current protocol version is `"2"`. 

2.2.0 should bump it to:

```text
3
```

Client/server version matching can remain strict.

---

## 19.1 Remove screen classes from packet models

Create common DTOs, for example:

```text
CodexCatalogEntry
CodexPlayerState
```

Do not use:

```text
LoreCodexScreen.CodexBookEntry
```

inside networking code.

---

## 19.2 Split catalog data from player-state data

The lore catalog changes rarely.

Player state changes frequently.

These should not require identical packet payloads.

### Catalog packet

Example:

```text
ClientboundCodexCatalogPacket
```

Send on:

- Login.
- Catalog reload/revision change.
- Explicit resync if necessary.

Contents can include:

```text
catalogRevision
id
title
author
category
titleColor
tags
discoveryHint
series
seriesOrder
```

Only include fields actually implemented.

---

## 19.3 Player-state packet

Example:

```text
ClientboundCodexPlayerStatePacket
```

Send after:

- Collecting.
- Banking duplicate.
- Extracting spare.
- Marking read.
- Toggling favorite.
- Changing duplicate handling.
- Admin mutation.

Contents:

```text
collected IDs / compact states
read IDs
favorite IDs
spare counts
discovery order/time
duplicate mode
collection counts
server feature flags
```

Choose an efficient encoding suitable for expected catalog sizes.

---

## 19.4 Delta packets

Full player-state sync is acceptable initially if reasonably compact.

Do not overengineer delta synchronization before measurement.

However, **do not resend all static titles/categories/authors every time the player banks one duplicate.**

---

## 19.5 Catalog revision

Give the merged registry a monotonically changing in-memory revision/hash.

If client revision != server revision:

- Send catalog.
- Then player state.

This makes reload handling deterministic.

---

# 20. Workstream I — Lore Definition Metadata

These additions should remain optional.

---

## 20.1 Search tags

Add:

```json
"tags": [
  "empire",
  "war",
  "ardath"
]
```

These are author-defined search keywords.

They are not Minecraft registry tags.

Validate:

- Array of strings.
- Ignore/reject blanks.
- Normalize search matching case-insensitively.

---

## 20.2 Discovery hint

Add optional:

```json
"discovery_hint": "Fragments of this account may be carried by the undead."
```

Used only for uncollected Codex entries when the server/definition allows hints.

This gives pack authors a way to make collecting lore less dependent on external wikis.

### Do not auto-generate exact hints

Do not automatically reveal:

> Kill zombies at Y 12 in biome X during thunderstorms.

Authorial hints should remain diegetic.

---

## 20.3 Series

Recommended if scope remains manageable:

```json
"series": "The Fall of Ardath",
"series_order": 2
```

Benefits:

- Ordered chronicles.
- Visible gaps.
- Easier pack-author organization.
- Inspiration from older lore journal/page-collection systems without requiring literal page fragments.

### Codex behavior

Within a series, optionally display:

```text
I
II
III
...
```

or:

```text
2 / 6
```

Do not require all books to belong to a series.

---

# 21. Example 2.2.0 Definition

```json
{
  "format_version": 1,
  "title": "The Last Chronicle of Ardath",
  "author": "Archivist Meron",
  "category": "History",
  "series": "The Fall of Ardath",
  "series_order": 3,
  "tags": [
    "ardath",
    "empire",
    "war"
  ],
  "description": "The final surviving account of the old imperial capital.",
  "discovery_hint": "Old fortresses may still contain surviving copies.",
  "show_glint": true,

  "drop_conditions": {
    "mob_types": [
      "minecraft:skeleton"
    ],
    "base_chance": 0.02,
    "max_copies_per_player": 1
  },

  "acquisition": [
    {
      "type": "loot_table",
      "loot_tables": [
        "minecraft:chests/stronghold_library"
      ],
      "chance": 0.15
    }
  ],

  "pages": [
    {
      "text": "The gates failed before dawn..."
    },
    {
      "text": "Continue to the next record",
      "underlined": true,
      "hoverEvent": {
        "action": "show_text",
        "contents": {
          "text": "The surviving chronicle ends here."
        }
      }
    }
  ]
}
```

`drop_conditions` and `acquisition` coexist in this example specifically to preserve legacy readability while adding another discovery route.

---

# 22. Workstream J — Performance

## 22.1 Index mob-drop candidates

Do not full-scan every book for every death once the catalog becomes large.

At registry-build time, create candidate buckets.

Minimum recommended index:

```text
exact mob type -> candidate definitions
generic/tag-based candidates -> fallback bucket
```

Optional additional indexing:

```text
dimension -> candidates
```

### Critical correctness requirement

Indices are only a **candidate prefilter**.

Always run the existing full `DropConditionContext.matches(...)` logic before accepting a book.

---

## 22.2 Cache Codex eligibility

`LoreBookRegistry.getCodexEligibleIds()` currently allocates a fresh set by scanning definitions. 

Build immutable cached values whenever the registry is rebuilt:

```text
allBookIds
codexEligibleIds
codexEligibleCount
categories
```

Do not repeatedly reconstruct them for normal player sync.

---

## 22.3 Precompute normalized search metadata client-side

When a new catalog packet arrives, precompute:

```text
lowercaseTitle
lowercaseAuthor
lowercaseCategory
lowercaseTags
```

Do not lowercase/rebuild every string every rendered frame.

This should complement the text caching already introduced in the public 2.1.2 release.

---

# 23. Workstream K — Configuration Cleanup

Keep configuration conservative.

Too many settings would make a refinement release worse.

---

## 23.1 Server configuration additions

Recommended at most:

```text
codex.enableDiscoveryHints = true
codex.enableFavorites = true
codex.enableUnreadTracking = true
```

Consider whether these really need switches before adding them; favorites and unread state are harmless enough that hard-enabled may be preferable.

For acquisition:

```text
acquisition.enableEntityDrops = true
acquisition.enableLootTables = true
acquisition.enableAdvancements = true
```

These can be useful for pack/server control.

---

## 23.2 Client configuration

Existing client config currently only covers lore-ID tooltip and collection message/sound behavior. 

Possible additions:

```text
codex.rememberSearch = true
codex.showUnreadMarkers = true
```

Avoid dozens of cosmetic toggles.

---

## 23.3 Clean up stale comments/names

Audit all config descriptions against actual 2.2.0 behavior.

In particular:

- "Copy" means extracting a banked spare.
- "Duplicate prevention" should be described as duplicate handling.
- Soulbound wording must match actual inventory/Curios/container behavior.

---

# 24. Workstream L — Localization and Accessibility

## 24.1 Eliminate remaining hardcoded player-facing English

The Codex screen still contains literal strings such as duplicate tooltip wording and "No books found." 

Move all such strings to translation keys.

New UI must not reintroduce English literals.

---

## 24.2 Translation-key parity test

RPG Lore advertises broad localization.

Add an automated check that:

- Loads `en_us.json`.
- Treats it as canonical.
- Reports keys missing from every shipped locale.

Whether CI fails on every untranslated value is a project policy decision, but **missing keys** should be detectable automatically.

---

## 24.3 Accessible state indicators

Do not communicate these only through color:

- Collected/missing.
- Favorite.
- Unread.
- Disabled extraction.
- Duplicate mode.

Use a combination of:

- Icons.
- Text/tooltips.
- Narration labels.
- Color.

---

## 24.4 Search widget narration

Use vanilla widgets such as `EditBox` wherever practical so standard keyboard focus and narration behavior are inherited.

---

# 25. Workstream M — Datagen

The build already has `runData` wiring and generated-resource support. 

Make it functional and trustworthy.

## Required

- Resolve the current Curios/mappings issue that prevented authoritative datagen use in prior development.
- `./gradlew runData` must complete.
- Generated tags must match hand-authored runtime expectations.
- Remove duplicate hand-maintained/generated assets where safe.

Do not make the development build depend on running datagen every time.

---

# 26. Workstream N — Automated Testing

2.2.0 should establish the first meaningful regression suite.

---

## 26.1 Parser unit tests

Test at minimum:

### Validity

- Minimal valid book.
- Full valid book.
- Plain-string page.
- JSON-component page.
- Quotes.
- Apostrophes.
- Backslashes.
- Newlines.
- Unicode.

### Invalid data

- Invalid JSON.
- Missing title.
- Missing pages.
- Invalid ResourceLocation.
- Wrong field type.
- Invalid color.
- Negative/zero weight.
- Out-of-range generation.
- `min_y > max_y`.
- Invalid time enum.
- Invalid weather enum.
- Invalid acquisition type.
- Invalid loot table ID.
- Unknown format version.

### Compatibility

- 2.1.x definition with no `format_version`.
- Existing `drop_conditions`.
- Definition with both legacy drop conditions and new acquisition rules.

---

## 26.2 Merge/source tests

Test:

- Datapack-only definition.
- Config-only definition.
- Same ID in both sources.
- Config correctly overrides datapack.
- Removing override reveals datapack definition again.
- Duplicate IDs within the same source produce deterministic diagnostics.

---

## 26.3 GameTests

Create regression coverage where Forge GameTest is practical.

### Entity drops

- Matching mob type.
- Wrong mob.
- Biome restriction.
- Dimension restriction.
- Time restriction.
- Weather restriction.
- Y restriction.
- Base-chance path.
- `maxBooksPerKill`.
- Per-player copy limit.

### Codex

- First collection.
- Duplicate becomes spare.
- Extraction decrements spare.
- Cannot extract at zero.
- Removed definition is pruned.
- Excluded definition is pruned.
- Reset clears related state.
- Read/favorite invariant.

### Granting

- First join.
- Full inventory.
- Already possesses Codex.

### Death

Explicit regression coverage for:

- Soulbound with `keepInventory=false`.
- Soulbound with `keepInventory=true`.
- Inventory Codex.
- Curios Codex when Curios is available.
- Multiple-Codex edge cases if supported.

---

## 26.4 Acquisition tests

If implemented in 2.2.0:

- Loot-table injection.
- Advancement collection.
- Advancement repeat does not duplicate first-time direct collection.
- Inventory delivery full-inventory handling.

---

## 26.5 Dedicated-server smoke test

Start a dedicated server without loading client UI classes.

This is particularly important after refactoring Codex network DTOs.

---

# 27. Manual QA Matrix

Before release, manually verify:

| Area | Scenarios |
|---|---|
| Codex first open | New player, returning player, login then immediate open |
| Search | Empty, partial title, author, tags, no results |
| Hidden names | Search cannot leak secret uncollected titles |
| Categories | All, uncategorized, multiple categories |
| Filters | All, collected, unread, favorites, missing |
| Sorting | Default, title, category, recent |
| Read state | New discovery → unread → open → read |
| Favorites | Toggle, reconnect, restart server |
| Back navigation | Codex → book → Codex retains state |
| Physical reader | Inventory-held book |
| Interactive JSON | Hover and supported click events |
| Copies | Zero, one, many, full inventory |
| Duplicate mode | Store spare / leave on ground |
| Curios | Equipped Codex open, death, respawn |
| Lectern | Lore book and Codex |
| Chiseled bookshelf | Book and Codex |
| Reload | Add/change/remove definitions while online |
| Datapacks | `/reload`, config override |
| GUI scaling | Small, normal, large GUI scales |
| Localization | English plus representative long-string languages |
| Dedicated server | No client-class loading failures |

---

# 28. Proposed Code Structure

Exact names may vary, but responsibilities should become approximately:

```text
com.rpglore
|
+-- lore
|   +-- LoreBookDefinition
|   +-- DropCondition
|   +-- LoreBookParser
|   +-- LoreValidationReport
|   +-- LoreValidationMessage
|   +-- LoreBookSource
|   +-- acquisition
|       +-- AcquisitionRule
|       +-- EntityDropAcquisition
|       +-- LootTableAcquisition
|       +-- AdvancementAcquisition
|
+-- config
|   +-- ConfigLoreSource
|   +-- BooksConfigLoader
|   +-- LoreCatalogBuilder
|   +-- LoreBookRegistry
|
+-- data
|   +-- DatapackLoreReloadListener
|   +-- LoreTrackingData
|
+-- codex
|   +-- CodexService
|   +-- CodexTrackingData
|   +-- CodexCatalogEntry
|   +-- CodexPlayerState
|   +-- CodexViewState              [client only if appropriate]
|   +-- LoreCodexScreen             [client]
|   +-- LoreCodexClientHelper       [client]
|
+-- acquisition
|   +-- LoreAcquisitionService
|   +-- LoreCollectedEvent
|
+-- network
    +-- ClientboundCodexCatalogPacket
    +-- ClientboundCodexPlayerStatePacket
    +-- ClientboundCodexOpenBookPacket
    +-- ClientboundCodexCollectionEventPacket
    +-- ServerboundCodexOpenBookPacket
    +-- ServerboundCodexExtractCopyPacket
    +-- ServerboundCodexSetFavoritePacket
    +-- ServerboundCodexSetDuplicateModePacket
```

Avoid multiplying tiny classes without purpose; this is a responsibility map rather than a requirement to create every exact filename.

---

# 29. Existing Files Likely Requiring Modification

At minimum inspect and modify as appropriate:

```text
gradle.properties
CHANGELOG.md
README.md
CURSEFORGE_DESCRIPTION.md

src/main/java/com/rpglore/RpgLoreMod.java

src/main/java/com/rpglore/config/BooksConfigLoader.java
src/main/java/com/rpglore/config/LoreBookRegistry.java
src/main/java/com/rpglore/config/ServerConfig.java
src/main/java/com/rpglore/config/ClientConfig.java

src/main/java/com/rpglore/lore/LoreBookDefinition.java
src/main/java/com/rpglore/lore/LoreBookItem.java
src/main/java/com/rpglore/lore/LoreBookScreen.java
src/main/java/com/rpglore/lore/DropCondition.java
src/main/java/com/rpglore/lore/DropConditionContext.java

src/main/java/com/rpglore/loot/LoreBookLootModifier.java

src/main/java/com/rpglore/codex/CodexService.java
src/main/java/com/rpglore/codex/CodexTrackingData.java
src/main/java/com/rpglore/codex/CodexEventHandler.java
src/main/java/com/rpglore/codex/LoreCodexItem.java
src/main/java/com/rpglore/codex/LoreCodexScreen.java
src/main/java/com/rpglore/codex/LoreCodexClientHelper.java

src/main/java/com/rpglore/network/ModNetwork.java
src/main/java/com/rpglore/network/ClientboundCodexSyncPacket.java
src/main/java/com/rpglore/network/ServerboundCodexOpenBookPacket.java

src/main/java/com/rpglore/command/RpgLoreCommands.java

src/main/resources/assets/rpg_lore/lang/*.json
```

Also inspect all Curios data and generated resources after reconciling 2.1.2.

---

# 30. Implementation Sequence

Execute in this order.

## Phase 0 — Baseline

1. Reconcile public 2.1.2 with source.
2. Build.
3. Verify Curios/non-Curios behavior.
4. Establish baseline tag/commit.

**Do not continue until green.**

---

## Phase 1 — Test and validation foundation

1. Add parser unit-test infrastructure.
2. Extract `LoreBookParser`.
3. Implement structured diagnostics.
4. Add `/rpglore validate`.
5. Improve reload reporting.
6. Test all legacy JSON behavior.
7. Fix the open malformed-content diagnosability class of issue.

This phase makes every following data-format change safer.

---

## Phase 2 — Data/model and network cleanup

1. Create common Codex DTOs.
2. Remove screen-class dependency from packet code.
3. Add read/favorite/discovery state.
4. Implement legacy save migration.
5. Split catalog/player state synchronization.
6. Bump protocol `2 -> 3`.
7. Add catalog revision.

No major UI changes until the new synchronized model is stable.

---

## Phase 3 — Codex QoL

Implement:

1. Categories.
2. Search.
3. Filters.
4. Sorting.
5. Unread state.
6. Favorites.
7. Recent discovery.
8. Better spare-copy UI.
9. Duplicate-handling wording.
10. Keyboard accessibility.
11. Optional Codex keybind.
12. Entry tooltips.

---

## Phase 4 — Reader refinement

1. Restore hover/click component behavior.
2. Add Codex return state.
3. Improve long title layout.
4. Document interactive JSON components.

---

## Phase 5 — Datapack definitions

1. Add datapack reload listener.
2. Add source merge layer.
3. Implement config-over-datapack precedence.
4. Add `format_version`.
5. Make `/reload` and `/rpglore reload` cooperate correctly.
6. Add source/precedence tests.

---

## Phase 6 — Acquisition expansion

1. Formalize acquisition service.
2. Preserve legacy entity-drop semantics.
3. Add loot-table source.
4. Add advancement source.
5. Add collection event/API.
6. Test acquisition behavior.

---

## Phase 7 — Performance

1. Build registry-derived immutable caches.
2. Index entity candidate selection.
3. Measure before/after with a synthetic large catalog.
4. Ensure final condition matching remains authoritative.

---

## Phase 8 — Release hardening

1. Make `runData` green.
2. Complete GameTests.
3. Dedicated server smoke test.
4. Curios regression test.
5. Localization parity.
6. Update generated book-format README.
7. Update root README.
8. Update CurseForge description.
9. Write player-facing changelog.
10. Final clean-world and upgraded-world QA.

---

## Phase 9 — Codex visual polish

1. Replace vanilla cycle buttons with red ribbon tabs for category, filter, and sort.
2. Position ribbons to stick out from the book's top-right edge.
3. Implement fixed-width ribbon display with auto-truncation for long names; preserve full text in tooltips.
4. Implement left-click and Shift-click cycling for all ribbons.
5. Ensure ribbons remain keyboard-accessible via Tab and keyboard activation.
6. Reflow header layout to display eight books per page instead of seven.
7. Replace the duplicate-handling toggle with a textured icon button from `textures/gui/codex.png`.
8. Add magnifier glyph and underline styling to the search input field.
9. Implement `RibbonButton` client class extending `AbstractButton`.
10. Add three translation keys (`rpg_lore.codex.ribbon.category`, `rpg_lore.codex.ribbon.filter`, `rpg_lore.codex.ribbon.sort`) to all 60 locale files (translated where the locale already translates the Codex controls, English elsewhere).
11. Remove the "x / y" collected-progress counter from the top of the parchment; only the bottom page counter remains.
12. Simplify spare-copy indicator to show only the copy icon on each row, with the spare count displayed in the icon's tooltip.
13. Replace the "Read" text link on each row with a small open-book icon having an "Open Book" tooltip (new translation key `rpg_lore.codex.open_book`, added to all 60 locale files).
14. Give row titles approximately 30 px more width; any title that still does not fit is truncated with "..." instead of being clipped mid-glyph.

---

# 31. Acceptance Criteria by Feature

## Codex

- Search works without network requests.
- Existing category metadata now affects the UI.
- Unread state persists across reconnect/server restart.
- Favorites persist.
- Existing collected books migrate as read.
- Recent sort works for new discoveries.
- Hidden uncollected names cannot leak through search.
- Codex→book→Codex restores browsing state.
- All controls are translated.
- No action relies solely on color.
- Ribbon tab labels are fully visible (truncated labels show full text in tooltip) and the keys are present in every locale file.
- Progress counter is removed from the Codex header; only the bottom page counter remains.
- Spare-copy count is shown only in the icon tooltip, not as text on the row.
- "Read" text link is replaced by a small open-book icon with appropriate tooltip.
- Row titles that do not fit are truncated with an ellipsis instead of being clipped mid-glyph.

---

## Data loading

- Existing config definitions load unchanged.
- Datapack definitions load.
- Config can override datapack by ID.
- Overrides are logged.
- Malformed one-book file does not destroy the rest of the catalog.
- Validation identifies source and field.
- `/rpglore validate` is useful without reading Java stack traces.

---

## Acquisition

- Existing mob drops remain behaviorally compatible.
- Books can be placed into configured loot tables.
- Books can be awarded from configured advancements.
- Duplicate/spare behavior remains coherent.
- Playerless loot generation does not falsely claim to honor per-player caps.

---

## Network

- Protocol is version 3.
- Common packets do not depend on client screen classes.
- Static catalog metadata is not resent for every trivial player-state mutation.
- Reload catalog changes synchronize correctly.

---

## Persistence

- 2.1.x Codex saves load.
- No collected books or spare copies disappear during migration.
- Read/favorite/discovery structures obey their invariants.
- Pruning removes associated metadata for deleted/excluded books.

---

## Reader

- Existing custom texture remains.
- Existing generated title page remains.
- Hover events work.
- Supported vanilla click events work.
- Physical and Codex-opened books both remain valid.
- Book pages containing unusual escaped text do not produce opaque errors.

---

## Performance

With a synthetic large catalog:

- Entity drop lookup no longer requires blindly evaluating every exact-mob-specific book on every death.
- Codex searches are local.
- Search normalization is not rebuilt per frame.
- Eligibility sets are cached per registry revision.

---

# 32. Explicit Non-Goals for 2.2.0

Do **not** add:

- Full Markdown rendering.
- Recipe pages.
- Multiblock visualization.
- A quest engine.
- A dialogue engine.
- Player statistics journal.
- Map/waypoint framework.
- Voice narration.
- Background lore audio.
- Arbitrary command execution when lore is collected/read.
- JavaScript scripting.
- Required Patchouli dependency.
- Required GuideME dependency.
- Required Curios dependency.
- General-purpose "all mod manuals in one book" behavior.
- Major GUI art redesign unrelated to usability.
- Cross-loader architectural rewrite.

These are not inherently bad ideas; they simply do not belong in this refinement release.

---

# 33. Optional Follow-Up Candidates After 2.2.0

Once the new foundation is stable, potential future releases could evaluate:

### 2.3.x — Storytelling

- Series completion effects.
- Optional fragment/page collections.
- Book-to-book hyperlinks.
- Optional localized title/author metadata.
- Pack-defined collection achievements.

### 2.4.x — Integrations

- Optional quest-mod adapters.
- Optional map/structure integration.
- Public integration examples.
- KubeJS integration implemented externally or as a separate compatibility module.

### 3.x

Only consider a larger format/UI break once actual author use cases justify it.

---

# 34. Design Guardrails for the Coding Agent

Throughout implementation:

1. **Do not rewrite working subsystems solely for elegance.**
2. **Do not silently alter existing loot probabilities.**
3. **Do not silently change copy-limit semantics.**
4. **Do not expose hidden lore metadata through new search/filter functionality.**
5. **Do not store authoritative collection state only on an ItemStack.**
6. **Do not make the client authoritative for favorites/read state.**
7. **Do not directly reference client-only classes from common packet models.**
8. **Do not make Curios mandatory.**
9. **Do not overwrite user lore files during documentation upgrades.**
10. **Do not consider `./gradlew build` sufficient testing for persistence/network changes.**
11. **Do not break lectern or chiseled-bookshelf behavior.**
12. **Do not remove legacy config-directory loading.**
13. **Do not make players recollect their existing library after migration.**
14. **Do not turn RPG Lore into a generic guidebook system.**

---

# 35. Definition of Done

RPG Lore 2.2.0 is complete when a player can:

- Discover lore from mobs, world loot, and configured progression.
- Immediately see that a new entry is unread.
- Open the Codex.
- Search hundreds of entries quickly.
- Browse by category.
- View only unread, missing, collected, or favorited lore.
- Sort by recent discovery.
- Understand spare-copy counts without deciphering `C(n)`.
- Read an entry.
- Use its interactive JSON text.
- Return directly to the same place in the Codex.
- Favorite useful entries.
- Reconnect and retain all those states.

A pack author can:

- Ship lore through a datapack.
- Continue using old config JSON unchanged.
- Override datapack lore from server config.
- Validate every definition in-game.
- Receive useful filename/field-aware diagnostics.
- Reload safely.
- Place lore in mob drops, loot tables, or advancements.
- Integrate an external mod through a minimal collection API/event.
- Build the project with meaningful automated regression coverage.

And an existing 2.1.x user can upgrade without:

- Losing the Codex.
- Losing collected books.
- Losing spare copies.
- Having every existing entry marked unread.
- Rewriting their book JSON.
- Installing a new mandatory dependency.

That combination would make 2.2.0 a substantial update while keeping RPG Lore recognizable: **the same focused collectible-lore mod, but much easier to author, discover, organize, revisit, and maintain.**