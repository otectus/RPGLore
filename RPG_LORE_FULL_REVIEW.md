# RPG Lore -- Full Technical & Design Review

**Mod:** RPG Lore v1.1.0
**Platform:** Minecraft Forge 1.20.1 (Forge 47.3.0)
**Review Date:** 2026-03-26
**Codebase:** 15 Java files, ~1,675 LOC
**License:** MIT
**Author:** crims

---

## Table of Contents

- [1. Executive Summary](#1-executive-summary)
- [2. Architecture Overview](#2-architecture-overview)
- [3. Findings by Severity](#3-findings-by-severity)
  - [3.1 Critical](#31-critical)
  - [3.2 High](#32-high)
  - [3.3 Medium](#33-medium)
  - [3.4 Low](#34-low)
  - [3.5 Enhancements](#35-enhancements)
- [4. Areas Needing Deeper Investigation](#4-areas-needing-deeper-investigation)
- [5. Release Readiness Assessment](#5-release-readiness-assessment)
- [6. Lore Codex -- Feature Implementation Plan](#6-lore-codex----feature-implementation-plan)
  - [6.1 Feature Overview](#61-feature-overview)
  - [6.2 Architecture & Data Design](#62-architecture--data-design)
  - [6.3 New Files](#63-new-files)
  - [6.4 Modified Files](#64-modified-files)
  - [6.5 Item Registration & LoreCodexItem](#65-item-registration--lorecodexitem)
  - [6.6 Data Storage -- CodexTrackingData](#66-data-storage----codextrackingdata)
  - [6.7 Networking -- ModNetwork](#67-networking----modnetwork)
  - [6.8 Event Handling -- CodexEventHandler](#68-event-handling----codexeventhandler)
  - [6.9 GUI/Screen Design -- LoreCodexScreen](#69-guiscreen-design----lorecodexscreen)
  - [6.10 Configuration](#610-configuration)
  - [6.11 Commands](#611-commands)
  - [6.12 Resource Files](#612-resource-files)
  - [6.13 Edge Cases & Thread Safety](#613-edge-cases--thread-safety)
  - [6.14 Implementation Sequence](#614-implementation-sequence)
  - [6.15 Optional Enhancements](#615-optional-enhancements)
  - [6.16 Key Design Decisions](#616-key-design-decisions)

---

## 1. Executive Summary

RPG Lore is a well-scoped, cleanly implemented data-driven lore book mod. The architecture is sound: JSON-defined books, configurable drop conditions, a custom book GUI with title page injection, and persistent per-player tracking via SavedData. The code demonstrates good Forge conventions (DeferredRegister, GlobalLootModifier, DistExecutor for safe client/server separation) and the v1.1.0 release addressed several foundational issues (copy tracking persistence, random seeding, page navigation).

The mod is at a **beta-quality level suitable for limited public testing**, but not yet production-ready for wide release. There are no critical data-loss bugs, but several high-severity logic issues could confuse pack makers, and the absence of input validation on JSON fields creates silent failure modes. The biggest architectural gaps are the double-gating drop chance behavior and the lack of test coverage.

**Release readiness: 70/100.** Suitable for an open beta. A v1.2.0 addressing the High and Medium findings below would bring it to production quality.

---

## 2. Architecture Overview

### 2.1 Module Map

```
RpgLoreMod.java (entry point, event wiring)
  |
  +-- registry/ModItems.java              DeferredRegister for LORE_BOOK item
  +-- loot/ModLootModifiers.java          DeferredRegister for loot modifier codec
  +-- config/ServerConfig.java            ForgeConfigSpec (server-side)
  +-- config/ClientConfig.java            ForgeConfigSpec (client-side)
  +-- config/BooksConfigLoader.java       JSON loading, book registry, tracking delegation
  +-- lore/LoreBookDefinition.java        Record: immutable book data
  +-- lore/DropCondition.java             Plain class: filter criteria (should be a record)
  +-- lore/DropConditionContext.java       Record: runtime context + matches() logic
  +-- lore/LoreBookItem.java              Custom WrittenBookItem subclass
  +-- lore/LoreBookScreen.java            Client-only BookViewScreen with title page
  +-- lore/LoreBookClientHelper.java      Client-only DistExecutor target
  +-- loot/LoreBookLootModifier.java      Global loot modifier: drop logic
  +-- data/LoreTrackingData.java          SavedData: per-player copy counts
  +-- command/RpgLoreCommands.java        Brigadier command tree
```

### 2.2 Data Flow: Lore Book Drops

```
Mob dies
  -> LoreBookLootModifier.doApply() fires via Forge global loot modifier
  -> Extract killer (KILLER_ENTITY -> LAST_DAMAGE_PLAYER fallback)
  -> Apply global filters (hostile mob check, player kill check)
  -> Build DropConditionContext from LootContext + LivingEntity + Player
  -> BooksConfigLoader.getMatchingBooks(ctx): iterate all books, ctx.matches(condition)
  -> Roll global drop chance (ServerConfig.GLOBAL_DROP_CHANCE + looting scaling)
  -> selectBooks(): weighted random selection without replacement
  -> Per-book: roll base_chance override, check per-player copy limit
  -> LoreBookItem.createStack(def): build ItemStack with full NBT
  -> Add to generatedLoot
```

### 2.3 Data Flow: Reading a Lore Book

```
Player right-clicks LoreBookItem
  -> LoreBookItem.use() fires
  -> Server: WrittenBookItem.resolveBookComponents() + broadcastChanges()
  -> Client: DistExecutor -> LoreBookClientHelper.openBookScreen(stack)
  -> new LoreBookScreen(stack)
  -> TitlePageBookAccess wraps WrittenBookAccess, inserts dummy page 0
  -> render() dispatches to renderTitlePage() (page 0) or renderContentPage() (page 1+)
  -> Custom LORE_BOOK_TEXTURE drawn for all pages
```

### 2.4 Design Patterns

| Pattern | Usage |
|---------|-------|
| DeferredRegister | `ModItems`, `ModLootModifiers` -- standard Forge registration |
| Factory Method | `DropCondition.defaultCondition()`, `LoreTrackingData.getOrCreate()`, `LoreBookItem.createStack()` |
| Decorator/Wrapper | `TitlePageBookAccess` wraps `BookAccess` to inject title page |
| Builder | `ForgeConfigSpec.Builder` for server/client config |
| Record (Value Object) | `LoreBookDefinition`, `DropConditionContext` -- immutable data holders |
| Singleton-like | `BooksConfigLoader` static state with volatile map swap |
| Strategy | `DropConditionContext.matches()` evaluates multiple filter types |

---

## 3. Findings by Severity

### 3.1 Critical

**None.** No data-loss bugs, no crashes, no security issues identified. The mod is fundamentally stable.

---

### 3.2 High

Issues that will confuse users, cause incorrect behavior, or create operational problems.

---

#### H1. Double-gated drop chance misleads pack makers

**File:** `src/main/java/com/rpglore/loot/LoreBookLootModifier.java:87-101`

**Problem:** The global drop chance (default 5%) is rolled first at line 89. Only if it passes are individual books considered. Then, per-book `base_chance` is rolled *again* at line 100. A pack maker setting `base_chance: 1.0` (expecting "always drops") would see the book drop only ~5% of the time due to the global gate. The README documents `base_chance` as "Override drop chance (0.0--1.0) for this book," which implies it replaces the global chance.

**Impact:** Pack makers will file bug reports. The semantics are unintuitive -- two probability gates multiply rather than one overriding the other.

**Recommendation:** Make `base_chance` truly override the global chance. Restructure the logic so that:
- Books **with** `base_chance` set use their own chance exclusively (skip the global roll)
- Books **without** `base_chance` use the global chance as today

```java
// Split candidates into those with and without base_chance overrides
List<LoreBookDefinition> withOverride = new ArrayList<>();
List<LoreBookDefinition> withoutOverride = new ArrayList<>();
for (LoreBookDefinition def : matching) {
    if (def.dropCondition().baseChance() != null) {
        withOverride.add(def);
    } else {
        withoutOverride.add(def);
    }
}

// For books without override: roll global chance
if (!withoutOverride.isEmpty() && random.nextDouble() < chance) {
    // select and add from withoutOverride...
}

// For books with override: roll each book's own base_chance
for (LoreBookDefinition def : withOverride) {
    if (random.nextDouble() < def.dropCondition().baseChance()) {
        // copy limit check + add to loot...
    }
}
```

---

#### H2. `recordPlayerReceived` called before stack is confirmed in loot

**File:** `src/main/java/com/rpglore/loot/LoreBookLootModifier.java:104-114`

**Problem:** At line 109, `recordPlayerReceived` increments the player's copy count. At line 114, the stack is added to `generatedLoot`. While `ObjectArrayList.add()` cannot realistically fail, the pattern is fragile: the `continue` on line 100 (base_chance failure) correctly skips recording, but recording on line 109 happens before we know the stack was successfully created and added. If any future code is inserted between these lines that could `continue` or `throw`, the tracking count would be wrong permanently.

**Impact:** Low probability of actual bug today, but the ordering makes the code harder to reason about and creates a maintenance trap.

**Recommendation:** Move `recordPlayerReceived` to after `generatedLoot.add()`:

```java
ItemStack stack = LoreBookItem.createStack(def);
generatedLoot.add(stack);

// Record AFTER successful addition to loot
if (player != null && def.dropCondition().maxCopiesPerPlayer() >= 0) {
    BooksConfigLoader.recordPlayerReceived(player.getUUID(), def.id());
}
```

---

#### H3. No generation field validation

**File:** `src/main/java/com/rpglore/config/BooksConfigLoader.java:225`

**Problem:** `generation` is parsed as a raw `int` with no range validation. Values outside 0-3 are silently accepted. In `LoreBookItem.appendHoverText()` at line 116, the guard `if (generation >= 0 && generation <= 3)` silently hides the label for invalid values, masking the config error. Vanilla `WrittenBookItem` only supports generations 0-3; higher values have undefined behavior in some vanilla code paths.

**Impact:** A pack maker typo (`"generation": 10`) produces no warning, no label, and potentially undefined behavior. Silent misconfiguration is the worst kind of bug.

**Recommendation:** Clamp to 0-3 with a warning in `parseFile()`:

```java
int generation = root.has("generation") ? root.get("generation").getAsInt() : 0;
if (generation < 0 || generation > 3) {
    RpgLoreMod.LOGGER.warn("Lore book '{}' has invalid generation {}, clamping to 0-3", filename, generation);
    generation = Math.max(0, Math.min(3, generation));
}
```

---

#### H4. `/rpglore give` always records against copy limits

**File:** `src/main/java/com/rpglore/command/RpgLoreCommands.java:91`

**Problem:** The admin `give` command calls `recordPlayerReceived` unconditionally. If an admin gives a player a book for testing or as a reward, it counts against their `max_copies_per_player` limit. There is no way to undo this short of editing SavedData. The CHANGELOG notes this was intentional ("for consistency"), but it creates an operational problem for server administrators.

**Impact:** Admins will inadvertently consume players' copy allowances. Particularly problematic during testing or when fixing player issues.

**Recommendation:** Add an optional `--no-track` flag (or default to not tracking for admin gives):

```java
// Option A: Add a boolean argument
.then(Commands.argument("track", BoolArgumentType.bool())
    .executes(ctx -> executeGive(ctx, BoolArgumentType.getBool(ctx, "track"))))
.executes(ctx -> executeGive(ctx, false))  // default: don't track admin gives
```

At minimum, if keeping current behavior, add a feedback message: `"Note: This counts against per-player copy limits. Use --no-track to skip."`

---

### 3.3 Medium

Code quality issues, missing safeguards, or minor logic problems.

---

#### M1. `DropCondition` should be a record

**File:** `src/main/java/com/rpglore/lore/DropCondition.java:8-76`

**Problem:** `DropCondition` is a plain class with 12 final fields and 12 manually written accessor methods. Both `DropConditionContext` and `LoreBookDefinition` are records. This creates inconsistency and unnecessary boilerplate (40+ lines of getters that a record generates automatically).

**Impact:** Code maintenance burden. No functional impact, but it increases the cognitive load when reading the codebase.

**Recommendation:** Convert to `record DropCondition(...)`. The nested enums `TimeFilter` and `WeatherFilter` can remain. The `defaultCondition()` static factory works fine on records:

```java
public record DropCondition(
    @Nullable List<ResourceLocation> mobTypes,
    @Nullable List<String> mobTags,
    @Nullable List<ResourceLocation> biomes,
    @Nullable List<String> biomeTags,
    @Nullable List<ResourceLocation> dimensions,
    @Nullable Integer minY,
    @Nullable Integer maxY,
    TimeFilter time,
    WeatherFilter weather,
    boolean requirePlayerKill,
    @Nullable Double baseChance,
    int maxCopiesPerPlayer
) {
    public enum TimeFilter { ANY, DAY_ONLY, NIGHT_ONLY }
    public enum WeatherFilter { ANY, CLEAR_ONLY, RAIN_ONLY, THUNDER_ONLY }

    public static DropCondition defaultCondition() {
        return new DropCondition(null, null, null, null, null,
                null, null, TimeFilter.ANY, WeatherFilter.ANY, true, null, -1);
    }
}
```

---

#### M2. `biomeId` null fallback silently defaults to `minecraft:plains`

**File:** `src/main/java/com/rpglore/lore/DropConditionContext.java:72`

**Problem:** If `biomeId` resolves to null (which should not happen in normal play but could happen with heavily modded dimensions or world generation), the code silently falls back to `minecraft:plains`. This would cause biome-filtered books to match incorrectly -- a book restricted to plains biomes could drop in a modded void dimension.

**Impact:** Rare edge case with modded content, but silent incorrect behavior is worse than a logged warning.

**Recommendation:** Log a warning when the fallback triggers:

```java
if (biomeId == null) {
    RpgLoreMod.LOGGER.warn("Could not determine biome at {} in {}, defaulting to minecraft:plains",
            pos, dimension);
    biomeId = new ResourceLocation("minecraft", "plains");
}
```

---

#### M3. Manual `escapeJsonString` misses edge cases

**File:** `src/main/java/com/rpglore/config/BooksConfigLoader.java:401-407`

**Problem:** The method manually escapes 5 characters (`\`, `"`, `\n`, `\r`, `\t`). It misses: form feeds (`\f`), backspace (`\b`), and Unicode control characters (`\u0000`-`\u001F`). Gson is already on the classpath and handles all edge cases correctly.

**Impact:** Books with unusual characters in plain-string pages could produce malformed JSON text components, leading to blank pages or rendering errors. Unlikely in practice but a correctness gap.

**Recommendation:** Build the JSON programmatically instead of string concatenation:

```java
// Replace line 244:
//   pageText = "{\"text\":\"" + escapeJsonString(pageText) + "\"}";
// With:
JsonObject comp = new JsonObject();
comp.addProperty("text", pageText);
pageText = GSON.toJson(comp);
```

This eliminates the manual escaping entirely and uses Gson's built-in, fully correct escaping.

---

#### M4. No page count limit

**File:** `src/main/java/com/rpglore/config/BooksConfigLoader.java:238-256`

**Problem:** A JSON file with thousands of pages is accepted without warning. Vanilla `WrittenBookItem` has a 100-page limit enforced during editing. Each page is resolved, split into lines, and cached -- a 10,000-page book could cause client lag when opened and would create a very large NBT payload on the ItemStack.

**Impact:** A malformed or malicious config could create books that lag clients or produce excessively large ItemStacks. Defense in depth for pack-maker errors.

**Recommendation:** Add a configurable hard limit (default 200 pages) with a warning when exceeded:

```java
private static final int MAX_PAGES = 200;

// After the pages parsing loop:
if (pages.size() > MAX_PAGES) {
    RpgLoreMod.LOGGER.warn("Lore book '{}' has {} pages, truncating to {}",
            filename, pages.size(), MAX_PAGES);
    pages = pages.subList(0, MAX_PAGES);
}
```

---

#### M5. No title length validation

**File:** `src/main/java/com/rpglore/config/BooksConfigLoader.java:218`

**Problem:** Titles of arbitrary length are accepted. While `LoreBookScreen.renderTitlePage()` handles scaling down wide titles at line 169, extremely long titles (100+ characters) will be scaled to unreadable sizes. Vanilla enforces a 32-character title limit for written books.

**Impact:** Cosmetic issue only (the auto-scaling prevents overflow), but a pack maker with a 200-character title will get a 2-pixel-high title on the page. A warning helps catch mistakes.

**Recommendation:** Warn (but do not reject) titles exceeding a reasonable threshold:

```java
if (title.length() > 48) {
    RpgLoreMod.LOGGER.warn("Lore book '{}' has a very long title ({} chars), may display poorly",
            filename, title.length());
}
```

---

#### M6. `BooksConfigLoader` has too many responsibilities

**File:** `src/main/java/com/rpglore/config/BooksConfigLoader.java` (entire file, 410 lines)

**Problem:** This single class handles: (a) default file generation, (b) JSON parsing, (c) the book registry/cache, (d) query methods, (e) per-player tracking delegation, and (f) parsing helpers. It is the largest file in the project and has the most reasons to change.

**Impact:** Harder to maintain, test, and extend. Adding new features (categories, data pack support, Codex integration) would further bloat it.

**Recommendation:** Extract into at least two classes:
- `BooksConfigLoader` -- file I/O, JSON parsing, default generation, hex color/string validation
- `LoreBookRegistry` -- the book map, query methods (`getMatchingBooks`, `getById`, `getAllBooks`), tracking delegation

This reduces coupling and makes each class easier to reason about independently.

---

#### M7. Weighted selection wasteful when all candidates fit

**File:** `src/main/java/com/rpglore/loot/LoreBookLootModifier.java:122-125`

**Problem:** When `candidates.size() <= max && !useWeights`, all candidates are returned directly (correct short-circuit). But when `useWeights` is true and `candidates.size() <= max`, the code still performs the full weighted selection loop, doing unnecessary work to arrive at the same set of books in a different order. Since all candidates will be selected regardless, the ordering does not matter for loot.

**Impact:** No functional difference. Minor inefficiency and asymmetric code paths that are confusing to read.

**Recommendation:** Short-circuit regardless of `useWeights`:

```java
if (candidates.size() <= max) {
    return candidates; // All will be selected anyway, order doesn't matter for loot
}
```

---

#### M8. Hardcoded vanilla layout constants

**File:** `src/main/java/com/rpglore/lore/LoreBookScreen.java:41-45`

**Problem:** `TEXT_WIDTH=114`, `TEXT_HEIGHT=128`, `IMAGE_WIDTH=192`, `PAGE_TEXT_X_OFFSET=36`, `PAGE_TEXT_Y_OFFSET=30` are hardcoded from decompiled vanilla `BookViewScreen` source. If these change in a future Minecraft version, the layout will break. These are private constants in vanilla with no public API.

**Impact:** Technical debt for version upgrades. Not an issue for 1.20.1 specifically.

**Recommendation:** Add a comment block documenting the source:

```java
// These constants are sourced from BookViewScreen.java (1.20.1 official mappings).
// They are private in vanilla and cannot be referenced directly.
// If porting to a new Minecraft version, verify these values against the
// decompiled BookViewScreen source.
```

Consider using Access Transformers to make them accessible, though the current approach avoids AT maintenance overhead.

---

#### M9. `max_copies_per_player` negative value semantics undocumented

**File:** `src/main/java/com/rpglore/data/LoreTrackingData.java:58`

**Problem:** `canPlayerReceive` returns `true` for any `maxCopies < 0`. The README documents `-1 = unlimited`, but `-2`, `-100`, etc. also silently behave as unlimited. A pack maker might accidentally set a negative value (e.g., `-5`) and not realize it means unlimited.

**Impact:** Minor confusion risk. The behavior is arguably correct (any negative = unlimited), but the documentation only mentions -1.

**Recommendation:** Validate in `BooksConfigLoader.parseDropCondition()`:

```java
int maxCopiesPerPlayer = drop.has("max_copies_per_player")
        ? drop.get("max_copies_per_player").getAsInt() : -1;
if (maxCopiesPerPlayer < -1) {
    RpgLoreMod.LOGGER.warn("Lore book '{}' has invalid max_copies_per_player {}, using -1 (unlimited)",
            filename, maxCopiesPerPlayer);
    maxCopiesPerPlayer = -1;
}
```

---

### 3.4 Low

Minor improvements, cosmetic issues, and style considerations.

---

#### L1. No creative tab for LoreBookItem

**File:** `src/main/java/com/rpglore/registry/ModItems.java:15`

**Problem:** The item has no creative tab assignment. Pack developers testing in creative mode cannot find the item in the creative inventory. The `/rpglore give` command is the intended workflow, but creative mode browsing is standard practice for mod developers.

**Recommendation:** Register in an appropriate creative tab via `CreativeModeTabEvent.BuildContents`:

```java
// In RpgLoreMod constructor, on MOD bus:
modEventBus.addListener(this::onBuildCreativeTabContents);

private void onBuildCreativeTabContents(CreativeModeTabEvent.BuildContents event) {
    if (event.getTab() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
        event.accept(ModItems.LORE_BOOK);
    }
}
```

---

#### L2. Hardcoded separator dashes in tooltip

**File:** `src/main/java/com/rpglore/lore/LoreBookItem.java:130`

**Problem:** The tooltip separator `"----------"` is a hardcoded string literal. The style is subjective and not configurable.

**Recommendation:** Low priority. Extract to a constant for clarity:

```java
private static final String TOOLTIP_SEPARATOR = "----------";
```

---

#### L3. Stream allocation per mob kill

**File:** `src/main/java/com/rpglore/lore/DropConditionContext.java:43-48, 58-61`

**Problem:** `stream().collect(Collectors.toSet())` is used twice per mob kill for entity tags and biome tags, creating intermediate objects.

**Impact:** Negligible. Would only matter with thousands of kills per second, which is not a realistic scenario.

**Recommendation:** No action needed. If profiling ever shows this as a hotspot, collect into pre-allocated sets.

---

#### L4. `trackedPage` shadow field fragility

**File:** `src/main/java/com/rpglore/lore/LoreBookScreen.java:60, 96-117`

**Problem:** The `trackedPage` field shadows the private `super.currentPage` and must be kept in sync via overrides of `pageForward()`, `pageBack()`, and `forcePage()`. If Mojang adds a new navigation method in a future version that modifies `currentPage`, the shadow will desync.

**Impact:** Fragile across version upgrades, but correct for 1.20.1. The v1.1.0 fix for page navigation desync shows this area has been a source of bugs before.

**Recommendation:** Document the fragility with a comment. Access Transformers could make `currentPage` accessible but add maintenance overhead.

---

#### L5. Missing mod metadata

**File:** `src/main/resources/META-INF/mods.toml`

**Problem:** The mod metadata is minimal. No `displayURL`, `logoFile`, or `updateJSONURL`. The mod will appear bare in the Forge mod list.

**Recommendation:** Before public release, add:

```toml
displayURL="https://github.com/otectus/RPGLore"
logoFile="rpg_lore_logo.png"
updateJSONURL="https://raw.githubusercontent.com/otectus/RPGLore/main/update.json"
```

---

### 3.5 Enhancements

Feature additions for future versions.

---

#### E1. No pickup toast/notification

Players get no special feedback when a lore book drops from a mob. A custom toast notification (similar to advancement toasts) or action bar message would significantly improve discoverability. The enchantment glint helps visually, but an explicit "You found: The Fallen Kingdom" message would be much more engaging.

---

#### E2. No advancement/collection tracking integration

There is no way for players to track their lore book collection progress through vanilla advancements or any in-game UI. This is the most impactful missing feature for player engagement. Detailed implementation plan in [Section 6: Lore Codex](#6-lore-codex----feature-implementation-plan).

---

#### E3. Cannot configure enchantment glint per-book

`LoreBookItem.isFoil()` returns `true` unconditionally (line 33). Adding a `"show_glint"` boolean to the book JSON definition would give pack makers control. Some books might be intended as mundane documents, not magical tomes.

---

#### E4. No categories/groups for books

Large collections have no organizational structure. A `"category"` field in the JSON plus grouped `/rpglore list` output and Codex sections would help pack makers manage content at scale.

---

#### E5. Commands are OP-only with no self-service options

All three commands require permission level 2. A `/rpglore collection` command showing the player's own discovered books (permission level 0) would add value for players and reduce admin burden.

---

#### E6. No data pack integration

Books can only be loaded from the `config/rpg_lore/books/` directory. Supporting data packs (`data/<namespace>/rpg_lore/books/`) would enable modpack distribution via standard Minecraft mechanisms and allow resource pack overrides. This is the standard expectation for data-driven Forge mods.

---

#### E7. No JEI/REI integration

Books do not appear in recipe viewers. A JEI plugin showing available lore books and their drop conditions (which mobs, which biomes) would help players understand what content is available without reading config files.

---

#### E8. Forge-only

The mod targets Forge 1.20.1 only. NeoForge 1.20.1 is backwards-compatible with Forge, so it likely works already without changes. Fabric would require a separate port or an architectures-style multi-loader setup.

---

## 4. Areas Needing Deeper Investigation

### 4.1 Thread Safety of Loot Modifier + Tracking

`LoreBookLootModifier.doApply()` calls into `BooksConfigLoader` (volatile map read) and `LoreTrackingData` (ConcurrentHashMap). The volatile + ConcurrentHashMap combination should be safe, but the `record then add` pattern (H2) introduces a window where tracking state and loot state are inconsistent.

If two loot events fire simultaneously for the same player (e.g., AoE damage killing multiple mobs in one tick), the copy limit check at lines 104-108 could race: both checks pass before either record is written. The `ConcurrentHashMap.merge()` in `LoreTrackingData:66` is atomic per key, so the count will be correct eventually, but the limit check is not atomic with the record.

In practice, Minecraft processes entity deaths sequentially on the server thread, so this is likely single-threaded. But it warrants a comment or explicit verification.

### 4.2 WrittenBookItem.resolveBookComponents Interaction

`WrittenBookItem.resolveBookComponents()` is called at `LoreBookItem.java:65`. This modifies the stack's NBT in place. Since `resolved` is set to 1 at `createStack():172`, this should be a no-op. However, if a lore book is modified via commands or NBT editors to have `resolved: 0`, the resolution will run and could alter page content. Edge-case behavior worth documenting.

### 4.3 Mod Compatibility with Inventory-Sorting Mods

The `stacksTo(1)` on `ModItems.java:15` means lore books do not stack. Inventory sorting mods should handle this correctly, but the custom NBT fields (`lore_id`, `lore_title_color`, etc.) mean that two copies of the "same" book are technically distinct ItemStacks even though they have identical content. This is correct behavior (WrittenBookItem semantics) but may surprise users of sorting mods who expect identical books to merge.

### 4.4 Large-Scale Stress Testing

With 1000+ book definitions, `getMatchingBooks()` iterates all books per mob kill. The condition-matching logic in `DropConditionContext.matches()` is lightweight (list `contains` checks), but the allocation of a new `ArrayList` and the iteration overhead could add up in high-throughput scenarios (mob farms). Profiling with a realistic large config would be valuable.

### 4.5 TitlePageBookAccess Contract

`TitlePageBookAccess` only overrides `getPageRaw()` and `getPageCount()`. The `BookAccess` interface also has a `getPage(int)` default method that calls `getPageRaw()` and resolves components. Since the override is on `getPageRaw()`, the default `getPage()` correctly delegates. But if Mojang changes this contract (e.g., makes `getPage()` independent of `getPageRaw()`), it would break. Worth documenting in a comment.

---

## 5. Release Readiness Assessment

### Strengths

- Clean, readable code with good documentation comments throughout
- Proper use of Forge APIs (DeferredRegister, GlobalLootModifier, SavedData, ForgeConfigSpec)
- Safe client/server separation via DistExecutor and `@OnlyIn(Dist.CLIENT)`
- Thread-safe tracking with ConcurrentHashMap
- Atomic config reload via volatile map swap
- Comprehensive README with full field documentation and examples
- Auto-generated `_README.txt` in the books config directory for pack makers
- CHANGELOG demonstrates active maintenance and responsiveness to bugs
- Good use of Java records for immutable data (`LoreBookDefinition`, `DropConditionContext`)
- Elegant `TitlePageBookAccess` wrapper pattern for title page injection

### Gaps for Public Release

- H1 (double-gated drop chance) **will** generate confusion and support requests from pack makers
- H3 (no generation validation) will produce silent misconfiguration
- Zero test coverage means regressions are discovered by users, not CI
- No CurseForge/Modrinth metadata or project infrastructure
- No mod logo, screenshots, or visual presence in the Forge mod list
- No creative tab (minor, but standard expectation)

### Recommended Release Plan

| Version | Scope | Contents |
|---------|-------|----------|
| **v1.1.1** | Patch | Fix H1, H2, H3, H4; add M2, M3, M9 validations |
| **v1.2.0** | Minor | M1 record conversion, M4 page limit, M5 title warning, M6 class extraction, M7 selection short-circuit, L1 creative tab, L5 mod metadata |
| **v1.3.0** | Feature | E1 pickup toast, E3 per-book glint toggle, E4 categories, E5 self-service commands |
| **v2.0.0** | Major | Lore Codex (Section 6), E6 data pack integration, E7 JEI plugin |

---

## 6. Lore Codex -- Feature Implementation Plan

### 6.1 Feature Overview

The **Lore Codex** is a new stylized book item that serves as a personal collection tracker for lore books. Core features:

- **Collection storage:** Records at most one entry per lore book the player has encountered
- **Auto-collection:** When a Lore Book is picked up for the first time, it is automatically registered in the Codex
- **Soul-bound:** The player receives a Codex on first login; it persists through death
- **Browsable GUI:** A custom screen lists all books (collected and uncollected), with clickable names to read collected books
- **Collection counter:** Displays `n/N` (collected / total available)
- **Duplicate prevention toggle:** When enabled, prevents picking up lore books already in the Codex
- **Copy mechanic:** A button to create a physical copy of a collected book, consuming one book from inventory
- **Fully configurable:** Every feature can be toggled on/off via server config

### 6.2 Architecture & Data Design

**Key decision: Where does collection state live?**

The collection data is stored in **server-side `SavedData`** (authoritative), with a **cached NBT snapshot on the item** for tooltip rendering. This dual-storage approach handles edge cases cleanly:

| Scenario | Behavior |
|----------|----------|
| Player loses Codex item | Data survives in SavedData; new Codex shows same collection |
| Multiple Codexes in inventory | All show the same collection (server is authoritative) |
| Book definition removed from config | `pruneStaleEntries()` removes it from collections on reload |
| New book added to config | Appears as "uncollected" in the Codex |
| Server-side tampering protection | Client never modifies tracking; all mutations go through server |

**Data structure:**

```java
ConcurrentHashMap<UUID, CodexPlayerData> playerCodexes;

class CodexPlayerData {
    Set<String> collectedBookIds;       // set of lore_id strings
    boolean preventDuplicatePickup;     // toggle state
    boolean hasEverReceivedCodex;       // prevents re-granting on every login
}
```

**Client-server communication:** Forge `SimpleChannel` with 6 packet types:

| Packet | Direction | Purpose |
|--------|-----------|---------|
| `ServerboundOpenCodexPacket` | C -> S | Player opened Codex, request sync |
| `ClientboundCodexSyncPacket` | S -> C | Full collection data + catalog for GUI rendering |
| `ServerboundCodexToggleDuplicatePacket` | C -> S | Toggle duplicate prevention |
| `ServerboundCodexCopyBookPacket` | C -> S | Request to copy a book (consumes physical copy) |
| `ServerboundCodexOpenBookPacket` | C -> S | Request to read a book from Codex |
| `ClientboundCodexOpenBookPacket` | S -> C | Book data for the requested read |

### 6.3 New Files

| # | File Path | Purpose |
|---|-----------|---------|
| 1 | `src/main/java/com/rpglore/codex/LoreCodexItem.java` | Codex item class, `use()` triggers sync + screen |
| 2 | `src/main/java/com/rpglore/codex/CodexTrackingData.java` | Server SavedData for per-player collections |
| 3 | `src/main/java/com/rpglore/codex/CodexEventHandler.java` | Soul-bind, auto-collect, initial grant, drop prevention |
| 4 | `src/main/java/com/rpglore/codex/LoreCodexScreen.java` | Client-side custom GUI screen |
| 5 | `src/main/java/com/rpglore/codex/LoreCodexClientHelper.java` | Client-only DistExecutor target |
| 6 | `src/main/java/com/rpglore/network/ModNetwork.java` | SimpleChannel registration |
| 7 | `src/main/java/com/rpglore/network/ServerboundOpenCodexPacket.java` | C->S: request to open Codex |
| 8 | `src/main/java/com/rpglore/network/ClientboundCodexSyncPacket.java` | S->C: full collection sync |
| 9 | `src/main/java/com/rpglore/network/ServerboundCodexToggleDuplicatePacket.java` | C->S: toggle duplicate prevention |
| 10 | `src/main/java/com/rpglore/network/ServerboundCodexCopyBookPacket.java` | C->S: copy book request |
| 11 | `src/main/java/com/rpglore/network/ServerboundCodexOpenBookPacket.java` | C->S: read book from Codex |
| 12 | `src/main/java/com/rpglore/network/ClientboundCodexOpenBookPacket.java` | S->C: book data for reading |
| 13 | `src/main/resources/assets/rpg_lore/models/item/lore_codex.json` | Item model |
| 14 | `src/main/resources/assets/rpg_lore/textures/item/lore_codex.png` | Item texture (16x16) |
| 15 | `src/main/resources/assets/rpg_lore/textures/gui/codex.png` | GUI background texture |
| 16 | `src/main/resources/assets/rpg_lore/textures/gui/codex_icons.png` | Icon spritesheet (toggle, copy, lock, check, question mark) |

### 6.4 Modified Files

| # | File Path | Changes |
|---|-----------|---------|
| 1 | `src/main/java/com/rpglore/registry/ModItems.java` | Register `LORE_CODEX` RegistryObject |
| 2 | `src/main/java/com/rpglore/RpgLoreMod.java` | Wire `ModNetwork.register()`, register `CodexEventHandler`, init/clear `CodexTrackingData` lifecycle |
| 3 | `src/main/java/com/rpglore/config/ServerConfig.java` | Add 7 Codex config entries under `"codex"` section |
| 4 | `src/main/java/com/rpglore/config/ClientConfig.java` | Add 2 Codex display config entries |
| 5 | `src/main/java/com/rpglore/command/RpgLoreCommands.java` | Add `/rpglore codex` subcommand tree (5 subcommands) |
| 6 | `src/main/resources/assets/rpg_lore/lang/en_us.json` | Add ~15 translation keys |
| 7 | `src/main/java/com/rpglore/config/BooksConfigLoader.java` | Add `codex_exclude` field parsing, add `getBookCount()` helper |

### 6.5 Item Registration & LoreCodexItem

**Registration** in `ModItems.java`:

```java
public static final RegistryObject<LoreCodexItem> LORE_CODEX =
    ITEMS.register("lore_codex", () -> new LoreCodexItem(new Item.Properties().stacksTo(1)));
```

**LoreCodexItem** extends `Item` (NOT `WrittenBookItem` -- the Codex is not a book, it's a custom UI container).

Key behaviors:
- **`use()`**: Client-side opens `LoreCodexScreen` via DistExecutor; server-side sends `ClientboundCodexSyncPacket`
- **`getName()`**: Returns styled "Lore Codex" with configurable color
- **`appendHoverText()`**: Shows collection counter from cached NBT (`"Collected: 3/12"`), duplicate prevention status
- **`isFoil()`**: Returns `true` (configurable)

**NBT structure on the item** (cache only, server is authoritative):

```
{
    "codex_owner": "uuid-string",
    "codex_collected": ["rpg_lore:book1", "rpg_lore:book2"],
    "codex_prevent_duplicates": false,
    "codex_collected_count": 3,
    "codex_total_count": 12
}
```

### 6.6 Data Storage -- CodexTrackingData

Follows the `LoreTrackingData` pattern exactly:

```java
public class CodexTrackingData extends SavedData {
    private static final String DATA_NAME = RpgLoreMod.MODID + "_codex";
    private final ConcurrentHashMap<UUID, CodexPlayerData> playerCodexes = new ConcurrentHashMap<>();

    // Core API
    public boolean hasBook(UUID player, String bookId);
    public boolean addBook(UUID player, String bookId);     // returns true if newly added
    public boolean removeBook(UUID player, String bookId);
    public Set<String> getCollectedBooks(UUID player);
    public int getCollectedCount(UUID player);
    public void clearPlayer(UUID player);

    // Duplicate prevention toggle
    public boolean isPreventDuplicates(UUID player);
    public void setPreventDuplicates(UUID player, boolean state);

    // Initial grant tracking
    public boolean hasEverReceivedCodex(UUID player);
    public void markCodexGranted(UUID player);

    // Maintenance
    public void pruneStaleEntries(Set<String> validBookIds);

    // Network serialization
    public CompoundTag serializeForClient(UUID player);

    // SavedData factory
    public static CodexTrackingData getOrCreate(ServerLevel overworld);
}
```

**Lifecycle wiring** in `RpgLoreMod`:
- `onServerStarting`: `CodexTrackingData.getOrCreate(overworld)`, store static reference
- `onServerStopped`: Clear static reference
- `BooksConfigLoader.reload()`: Also call `codexData.pruneStaleEntries(validBookIds)`

### 6.7 Networking -- ModNetwork

Forge `SimpleChannel` setup:

```java
public class ModNetwork {
    private static SimpleChannel INSTANCE;
    private static final String PROTOCOL_VERSION = "1";

    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RpgLoreMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
        );
        // Register all 6 packet types with encode/decode/handle methods
    }

    public static <MSG> void sendToServer(MSG msg) { ... }
    public static <MSG> void sendToPlayer(MSG msg, ServerPlayer player) { ... }
}
```

**Sync packet payload** (`ClientboundCodexSyncPacket`):

```java
record CodexBookEntry(String id, String title, String author, boolean collected, String titleColor) {}

// Packet contains:
List<CodexBookEntry> catalog;      // ALL books (collected + uncollected)
boolean preventDuplicates;         // toggle state
int collectedCount;
int totalCount;
```

This sends the full catalog so the client can render the complete list without needing separate book definition sync. The packet is sent infrequently (only when opening the Codex or on collection events).

### 6.8 Event Handling -- CodexEventHandler

All listeners registered on `MinecraftForge.EVENT_BUS`.

#### Soul-binding: Keep on Death

**Events:** `PlayerEvent.Clone` + `LivingDropsEvent`

```java
@SubscribeEvent
public static void onPlayerClone(PlayerEvent.Clone event) {
    if (!event.isWasDeath() || !ServerConfig.CODEX_SOULBOUND.get()) return;

    event.getOriginal().reviveCaps(); // Required to access original inventory
    Inventory original = event.getOriginal().getInventory();
    Inventory newInv = event.getEntity().getInventory();

    for (int i = 0; i < original.getContainerSize(); i++) {
        ItemStack stack = original.getItem(i);
        if (stack.getItem() instanceof LoreCodexItem) {
            newInv.setItem(i, stack.copy());
            original.setItem(i, ItemStack.EMPTY); // prevent dupe
            break;
        }
    }
    event.getOriginal().invalidateCaps();
}

@SubscribeEvent
public static void onPlayerDrops(LivingDropsEvent event) {
    if (!(event.getEntity() instanceof Player) || !ServerConfig.CODEX_SOULBOUND.get()) return;
    event.getDrops().removeIf(drop -> drop.getItem().getItem() instanceof LoreCodexItem);
}
```

#### Prevent Manual Drop (when soul-bound)

```java
@SubscribeEvent
public static void onItemToss(ItemTossEvent event) {
    if (!ServerConfig.CODEX_SOULBOUND.get()) return;
    if (event.getEntity().getItem().getItem() instanceof LoreCodexItem) {
        event.getPlayer().getInventory().add(event.getEntity().getItem());
        event.setCanceled(true);
    }
}
```

#### Initial Codex Grant on First Login

```java
@SubscribeEvent
public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (!ServerConfig.CODEX_GRANT_ON_FIRST_JOIN.get()) return;
    if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

    CodexTrackingData data = CodexTrackingData.getOrCreate(serverPlayer.server.overworld());
    if (data.hasEverReceivedCodex(serverPlayer.getUUID())) return;

    ItemStack codex = new ItemStack(ModItems.LORE_CODEX.get());
    codex.getOrCreateTag().putString("codex_owner", serverPlayer.getUUID().toString());
    serverPlayer.getInventory().add(codex);
    data.markCodexGranted(serverPlayer.getUUID());
}
```

#### Auto-Collection on Lore Book Pickup

**Event:** `EntityItemPickupEvent` (cancelable)

```java
@SubscribeEvent
public static void onItemPickup(EntityItemPickupEvent event) {
    if (!ServerConfig.CODEX_AUTO_COLLECT.get()) return;
    if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

    ItemStack pickedUp = event.getItem().getItem();
    if (!(pickedUp.getItem() instanceof LoreBookItem)) return;

    CompoundTag tag = pickedUp.getTag();
    if (tag == null || !tag.contains("lore_id")) return;
    String bookId = tag.getString("lore_id");

    // Check if player has a Codex
    ItemStack codexStack = findCodexInInventory(serverPlayer);
    if (codexStack.isEmpty()) return;

    CodexTrackingData data = CodexTrackingData.getOrCreate(serverPlayer.server.overworld());

    // Duplicate prevention: if enabled and book already collected, cancel pickup
    if (data.isPreventDuplicates(serverPlayer.getUUID()) && data.hasBook(serverPlayer.getUUID(), bookId)) {
        event.setCanceled(true);
        return;
    }

    // Auto-collect: register new book in Codex
    if (data.addBook(serverPlayer.getUUID(), bookId)) {
        // Newly collected -- update cached NBT, send notification
        syncCodexItemNbt(codexStack, data, serverPlayer.getUUID());
        serverPlayer.displayClientMessage(
            Component.translatable("rpg_lore.codex.collected",
                Component.literal(getBookTitle(bookId)).withStyle(ChatFormatting.GOLD)),
            true  // action bar
        );
        serverPlayer.level().playSound(null, serverPlayer.blockPosition(),
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5f, 1.2f);
    }
    // Physical book still goes to inventory normally (pickup not canceled)
}
```

### 6.9 GUI/Screen Design -- LoreCodexScreen

**Extends:** `Screen` (NOT `BookViewScreen` -- fully custom layout needed)

**Layout:**

```
+----------------------------------------------------+
|  LORE CODEX                          [Toggle] [X]  |
|  Collected: 7 / 12                                 |
|  [Search: ____________]                            |
|----------------------------------------------------|
|  [check] The Fallen Kingdom          [Copy] [Read] |
|  [check] The Ancient Battle          [Copy] [Read] |
|  [ ??? ] Unknown Tome                              |
|  [ ??? ] Unknown Tome                              |
|  [check] The Dragon's Lament         [Copy] [Read] |
|  [ ??? ] Unknown Tome                              |
|  ...                                               |
|              [<] Page 1 / 3 [>]                    |
+----------------------------------------------------+
```

**Components:**
- **Title:** "LORE CODEX" centered, styled
- **Counter:** `"Collected: n / N"` below title
- **Toggle button:** Lock/unlock icon for duplicate prevention. Tooltip shows current state
- **Book list:** Scrollable or paginated list of all books from the synced catalog
  - **Collected books:** Colored title, [Copy] button, [Read] button (or click title to read)
  - **Uncollected books:** Greyed "???" or revealed name based on `CODEX_REVEAL_UNCOLLECTED_NAMES` config
- **Search field** (optional): Filter displayed list by title substring
- **Page navigation:** `[<]` `[>]` arrows with page indicator for large catalogs (e.g., 10 books per page)

**Button behaviors:**

| Button | Action |
|--------|--------|
| Click collected book title / [Read] | Send `ServerboundCodexOpenBookPacket(bookId)`. Server validates, sends book data back. Client opens `LoreBookScreen` with return-to-Codex behavior. |
| [Copy] | Send `ServerboundCodexCopyBookPacket(bookId)`. Server: validate book is in collection, find + consume physical copy in inventory, create new copy with `generation + 1` (capped at 3), give to player. |
| [Toggle] | Send `ServerboundCodexToggleDuplicatePacket`. Server toggles flag, syncs back. Button visual updates. |
| [X] | Close screen |

**Return-to-Codex behavior when reading:**

```java
// In LoreCodexClientHelper:
public static void openBookFromCodex(ItemStack bookStack) {
    Minecraft mc = Minecraft.getInstance();
    Screen parentScreen = mc.screen; // save current Codex screen
    mc.setScreen(new LoreBookScreen(bookStack) {
        @Override
        public void onClose() {
            mc.setScreen(parentScreen); // return to Codex instead of closing to game
        }
    });
}
```

**Widget hierarchy:**

```
LoreCodexScreen extends Screen
  +-- DuplicateToggleButton extends ImageButton
  +-- CodexBookList extends AbstractSelectionList<CodexBookList.Entry>
  |   +-- Entry extends ObjectSelectionList.Entry
  |       +-- Title text (clickable for collected books)
  |       +-- CopyButton extends ImageButton (collected books only)
  +-- SearchField extends EditBox (optional)
  +-- PageNavigation (ImageButtons + label)
```

### 6.10 Configuration

#### Server Config Additions

Under a new `"codex"` section in `ServerConfig.java`:

```java
builder.comment("Lore Codex settings").push("codex");

CODEX_ENABLED = builder
    .comment("Enable the Lore Codex feature entirely")
    .define("enabled", true);

CODEX_SOULBOUND = builder
    .comment("If true, the Codex is kept on death and cannot be dropped/traded")
    .define("soulbound", true);

CODEX_AUTO_COLLECT = builder
    .comment("If true, picking up a new Lore Book automatically registers it in the Codex")
    .define("autoCollect", true);

CODEX_GRANT_ON_FIRST_JOIN = builder
    .comment("If true, players receive a Codex on first login")
    .define("grantOnFirstJoin", true);

CODEX_ALLOW_COPY = builder
    .comment("If true, players can copy books from the Codex (consumes a physical copy)")
    .define("allowCopy", true);

CODEX_ALLOW_DUPLICATE_PREVENTION = builder
    .comment("If true, the duplicate prevention toggle is available in the Codex UI")
    .define("allowDuplicatePrevention", true);

CODEX_REVEAL_UNCOLLECTED_NAMES = builder
    .comment("If true, uncollected book names are shown in the Codex. If false, they appear as '???'")
    .define("revealUncollectedNames", false);

builder.pop();
```

#### Client Config Additions

Under a new `"codex_display"` section in `ClientConfig.java`:

```java
builder.comment("Codex display settings").push("codex_display");

CODEX_SHOW_NOTIFICATION = builder
    .comment("Show an action bar message when a new book is added to the Codex")
    .define("showCollectionNotification", true);

CODEX_PLAY_SOUND = builder
    .comment("Play a sound when a new book is collected into the Codex")
    .define("playCollectionSound", true);

builder.pop();
```

#### Per-Book Configuration

Optional field in book JSON definitions:

```json
{
    "codex_exclude": true
}
```

When `true`, this book will not appear in the Codex at all (useful for debug books, test books, or books intended to be ephemeral). Parsed in `BooksConfigLoader.parseFile()`, added to `LoreBookDefinition` record.

### 6.11 Commands

New `/rpglore codex` subcommand tree:

```
/rpglore codex reset <players>              -- Clear all collected books from player(s)' Codex
/rpglore codex add <players> <book_id>      -- Add a book to player(s)' Codex collection
/rpglore codex remove <players> <book_id>   -- Remove a book from player(s)' Codex collection
/rpglore codex give <players>               -- Give a physical Codex item to player(s)
/rpglore codex status <player>              -- Show a player's collection status
```

All require permission level 2. Book ID suggestions reuse the existing `SUGGEST_BOOK_IDS` provider.

### 6.12 Resource Files

#### Language File Updates (`en_us.json`)

```json
{
    "item.rpg_lore.lore_book": "Lore Book",
    "item.rpg_lore.lore_codex": "Lore Codex",
    "rpg_lore.codex.title": "Lore Codex",
    "rpg_lore.codex.collected": "New entry: %s",
    "rpg_lore.codex.counter": "Collected: %d / %d",
    "rpg_lore.codex.uncollected": "???",
    "rpg_lore.codex.duplicate_prevention.on": "Duplicate Prevention: ON",
    "rpg_lore.codex.duplicate_prevention.off": "Duplicate Prevention: OFF",
    "rpg_lore.codex.copy": "Copy",
    "rpg_lore.codex.read": "Read",
    "rpg_lore.codex.tooltip.collected": "Collected: %d / %d",
    "rpg_lore.codex.tooltip.duplicates_blocked": "Blocking duplicate pickups",
    "rpg_lore.codex.no_physical_copy": "You need a physical copy of this book",
    "rpg_lore.codex.command.reset": "Reset Codex for %d player(s)",
    "rpg_lore.codex.command.added": "Added \"%s\" to Codex for %d player(s)",
    "rpg_lore.codex.command.removed": "Removed \"%s\" from Codex for %d player(s)",
    "rpg_lore.codex.command.given": "Gave Lore Codex to %d player(s)"
}
```

#### Item Model (`models/item/lore_codex.json`)

```json
{
    "parent": "item/generated",
    "textures": {
        "layer0": "rpg_lore:item/lore_codex"
    }
}
```

#### Textures Needed

- `textures/item/lore_codex.png` -- 16x16 item icon (stylized book/codex)
- `textures/gui/codex.png` -- 256x256 GUI background (double-page spread style)
- `textures/gui/codex_icons.png` -- Small spritesheet: check mark, question mark, lock, unlock, copy icon

### 6.13 Edge Cases & Thread Safety

| Scenario | Handling |
|----------|----------|
| **Player loses Codex item** | Data survives in `CodexTrackingData` (SavedData). A new Codex item (via command or re-grant) shows the same collection. |
| **Books added/removed from config** | `pruneStaleEntries()` removes references to deleted books on reload. New books appear as "uncollected". |
| **Multiple Codexes in inventory** | Harmless. All show the same server-side collection. Auto-collect finds the first one. |
| **Config reload while Codex is open** | The screen shows stale data until reopened or a sync packet arrives. Could add a reload hook that triggers a sync. |
| **Thread safety** | `CodexTrackingData` uses `ConcurrentHashMap` (matching `LoreTrackingData`). All mutations call `setDirty()`. The `Set<String>` for collected books should use `ConcurrentHashMap.newKeySet()`. |
| **Server authority** | Client never modifies `CodexTrackingData`. All mutations (toggle, copy, add, remove) go through server-bound packets with server-side validation. |
| **Codex owner UUID** | Informational only (for tooltips). Any player can open any Codex and see **their own** collection (data keyed by opening player's UUID, not item's UUID). |

### 6.14 Implementation Sequence

Each phase is independently testable.

#### Phase 1: Foundation (Items + Registration)
1. Create `LoreCodexItem.java` with basic `use()` that sends a chat message
2. Register in `ModItems.java`
3. Add item model JSON and placeholder texture
4. Update `en_us.json` with item name
5. Create `ModNetwork.java` with empty channel registration
6. Wire `ModNetwork.register()` into `RpgLoreMod` constructor
7. **Test:** Item appears in-game, can be held, right-click sends message

#### Phase 2: Data Layer
8. Create `CodexTrackingData.java` with full SavedData implementation
9. Wire into `RpgLoreMod.onServerStarting()` / `onServerStopped()`
10. Wire prune call into `BooksConfigLoader.reload()`
11. **Test:** Data loads/saves correctly (verify via logging or commands)

#### Phase 3: Event Handlers (Core Logic)
12. Create `CodexEventHandler.java` with all event listeners
13. Implement soul-binding (`PlayerEvent.Clone` + `LivingDropsEvent`)
14. Implement initial grant (`PlayerLoggedInEvent`)
15. Implement auto-collection (`EntityItemPickupEvent`)
16. Implement drop prevention (`ItemTossEvent`)
17. Register all event listeners in `RpgLoreMod`
18. **Test:** Soul-bind on death, auto-collection on pickup, initial grant on first join, cannot drop when soul-bound

#### Phase 4: Networking
19. Implement all 6 packet classes with encode/decode/handle
20. Register in `ModNetwork`
21. Wire `LoreCodexItem.use()` to send/receive sync packet
22. **Test:** Packet roundtrip with logging, verify data arrives on client

#### Phase 5: GUI
23. Create `LoreCodexScreen.java` with basic layout
24. Create `LoreCodexClientHelper.java`
25. Implement book list rendering (collected/uncollected indicators)
26. Implement toggle button
27. Implement copy button with server-side validation
28. Implement read button (opens `LoreBookScreen` with return-to-Codex)
29. Add scrolling/pagination for large catalogs
30. Create GUI textures
31. **Test:** Full GUI interaction -- browse, read, copy, toggle

#### Phase 6: Configuration
32. Add all `ServerConfig` entries under `"codex"` section
33. Add all `ClientConfig` entries under `"codex_display"` section
34. Wire config checks into all relevant code paths
35. Add `codex_exclude` to `LoreBookDefinition` and `BooksConfigLoader`
36. **Test:** Verify all config toggles enable/disable features correctly

#### Phase 7: Commands
37. Add all `/rpglore codex` subcommands to `RpgLoreCommands.java`
38. **Test:** All 5 subcommands work correctly

#### Phase 8: Polish
39. Sound effects (collection, copy, page turn)
40. Action bar notifications
41. Tooltip rendering with collection counter
42. Final texture/art pass
43. Update README and CHANGELOG

### 6.15 Optional Enhancements

These can be added in future iterations:

| Enhancement | Description |
|-------------|-------------|
| **Search/filter** | `EditBox` widget at top of Codex screen; filter list by title substring |
| **Categories** | Optional `"category"` field in book JSON; Codex groups books by category with collapsible headers |
| **"New" badge** | Track `lastOpenedTimestamp` per book; books collected since last Codex opening get a "NEW" indicator |
| **Rarity indicators** | Visual markers (color/icon) based on book weight or a new `"rarity"` field |
| **Advancements** | Custom `CriteriaTrigger` implementations: "Bibliophile" (first book), "Librarian" (50%), "Archivist" (100%) |
| **Particle effects** | Spawn enchantment particles when a new book is added to the Codex |
| **Keybind** | Register a keybind to open the Codex directly without right-clicking the item |

### 6.16 Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Data authority | Server-side SavedData | Survives item loss, prevents duplication exploits, enables admin commands |
| Item NBT role | Cache/tooltip only | Fast tooltip rendering without network roundtrip; server remains authoritative |
| Screen base class | `Screen` (not `BookViewScreen`) | Fully custom layout needed; `BookViewScreen` is for page-by-page text reading |
| Book reading from Codex | Server sends book data via packet | Client does not have `BooksConfigLoader`; server validates collection before sending |
| Copy mechanic | Consume physical copy, produce new at `generation+1` | Matches requirement; prevents infinite free duplication; provides meaningful resource cost |
| Soul-bind implementation | `PlayerEvent.Clone` + `LivingDropsEvent` | Standard Forge pattern; proven reliable (used by Botania, Curios, etc.) |
| Auto-collection event | `EntityItemPickupEvent` | Pre-pickup, cancelable; allows preventing duplicates before item enters inventory |
| Duplicate prevention scope | Cancels entire pickup | Prevents item from cluttering inventory; the book stays on the ground for others |
| Network protocol | Forge `SimpleChannel` | Standard for Forge 1.20.1; well-documented, type-safe |
| Catalog sync approach | Full catalog in sync packet | Simple, infrequent, avoids separate catalog sync mechanism |

---

*End of review. All findings are grounded in the actual codebase as of commit `b5eb969` (v1.1.0).*
