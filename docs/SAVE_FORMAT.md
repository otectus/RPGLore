# Codex Save Format

## Overview
The Codex tracking data saves to the world's `data/rpg_lore_codex.dat` file as Minecraft NBT. It tracks collected books, read state, and per-player settings for each player on the server.

## Root Tags
- `version` (int) — save format version (currently 2)
- `players` (compound) — player data indexed by UUID string

## Player Tags (`players[<uuid>]`)
All per-player Codex state lives under a UUID key:

| Key | Type | Meaning |
|-----|------|---------|
| `collected` | List of strings | Book IDs in the player's collection |
| `copies` | Compound | Spare books banked per book ID (`id` → `int` count) |
| `read` | List of strings | Collected books the player has opened |
| `favorites` | List of strings | Collected books marked as favorites |
| `discovered` | Compound | Discovery timestamp per collected book (`id` → `long` game time; 0 = unknown) |
| `prevent_duplicates` | Bool | When true, prevents picking up already-collected books |
| `has_codex` | Bool | Whether the player has received a Codex (first-join grant tracking) |
| `pending_codex` | Compound | Soul-bound Codex stashed during death, restored on respawn; contains `stack` (ItemStack NBT) and optional `curio_slot` (string) and `curio_index` (int) |

## Invariants
- `read`, `favorites`, and `discovered` only contain IDs from `collected`
- Removing a book clears it from all five structures: collected, copies, read, favorites, and discovered

## Migration
Migration from version 1 is automatic per-player: if the `read` tag is absent, it is initialized as a copy of `collected`, so all existing books are treated as read (returning players are not flooded with "new" entries). Missing `favorites` and `discovered` tags become empty. The root `version` tag is informational only (written as 2, not consulted on load).
