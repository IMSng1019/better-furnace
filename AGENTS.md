# Repository Guidelines

## Project Structure & Module Organization
- Core server/common mod code lives in `src/main/java/better/furnace/`:
  - `mixin/` for behavior injection (`*Mixin.java`)
  - `minecart/` for train/furnace minecart domain logic
  - `menu/` for screen handlers
  - `chunk/` for chunk-loading management
- Client-only code is in `src/client/java/better/furnace/` (screens, client init).
- Resources are under `src/main/resources/` and `src/client/resources/`:
  - Mixins JSON, `fabric.mod.json`, language files, GUI textures.
- Design/implementation notes live in `docs/`; active task tracking is in `TODO.md`.

## Build, Test, and Development Commands
- `.\gradlew.bat build`  
  Full compile, remap, and jar packaging (primary CI/local verification command).
- `.\gradlew.bat clean build`  
  Rebuild from scratch when caches may be stale.
- `.\gradlew.bat runClient`  
  Launch a dev client for manual mod testing.
- `.\gradlew.bat tasks --all`  
  Inspect available Gradle tasks.

Note: current Loom/plugin setup may require JDK 21+ to run Gradle; source target remains Java 17.

## Coding Style & Naming Conventions
- Java: keep existing style (tabs, same-line braces, concise comments only where logic is non-obvious).
- Naming:
  - Classes: `PascalCase`
  - Methods/fields: `lowerCamelCase`
  - Mixin classes end with `Mixin`
  - Injected members use `betterFurnace$...` prefix to avoid collisions.
- Keep server/client boundaries explicit; do not reference client classes from `src/main`.

## Testing Guidelines
- No dedicated automated test suite yet; use `build` as baseline gate.
- Validate behavior manually in-game, especially:
  - minecart linking/collision behavior
  - portal group teleport
  - fuel insertion (hand + hopper)
  - GUI slot/flame rendering and sync
- If adding unit tests, place them in `src/test/java` and run with `.\gradlew.bat test`.

## Commit & Pull Request Guidelines
- Existing history uses short lowercase messages (`base`, `first commit`).
- Prefer clearer commit format going forward, e.g.:
  - `feat: add linked minecart collision filter`
  - `fix: adjust furnace GUI slot offset`
- PRs should include:
  - scope summary
  - changed paths/modules
  - build result (`.\gradlew.bat build`)
  - manual test steps and outcomes
  - GUI screenshots when UI is affected.
