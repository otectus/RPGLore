# RPG Lore — Forge 1.20.1 Mod

## Quick Reference
- **Mod ID**: `rpg_lore`
- **Package**: `com.rpglore`
- **Version**: 2.0.5
- **MC**: 1.20.1 | **Forge**: 47.3.0 | **Java**: 17
- **Mappings**: Official

## Build
- `./gradlew build` — full build
- `./gradlew compileJava` — compile-only
- `./gradlew runData` — run data generators

## Project Structure
- `command/` — in-game commands
- `codex/` — codex/book UI system
- `config/` — mod configuration
- `data/` — data generation providers
- `loot/` — loot table integration (mob drops)
- `lore/` — core lore book logic
- `network/` — packet handling
- `registry/` — DeferredRegister registrations
- `compat/` — mod compatibility layer
- `src/generated/resources/` — datagen output

## Key Dependencies
- **Forge** 47.3.0 (required)
- **Curios** 5.4.7+ (optional soft dep, compileOnly + runtimeOnly)

## Conventions
- Registration: DeferredRegister on MOD bus
- Data-driven lore books that drop from mobs
- License: MIT
