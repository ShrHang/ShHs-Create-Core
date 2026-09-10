# AGENTS.md

## Project Overview

- This is a Java 21 NeoForge Minecraft mod for Minecraft 1.21.1.
- Mod id: `shhs_create_core`.
- Main package: `io.github.shrhang.shhs_create_core`.
- Main mod entrypoint: `src/main/java/io/github/shrhang/shhs_create_core/ShHsCreateCore.java`.
- The project uses Gradle with `net.neoforged.moddev`, Create, Registrate, Ponder, JEI, Curios, Iron's Spells, KubeJS, and several L2/XKMC libraries.
- Local jars under `libs/` are part of the development setup. Do not delete, rename, or replace them unless the task is specifically about dependency maintenance.

## Encoding and Windows Rules

> IMPORTANT: This repository contains Chinese text. Never use PowerShell's
> implicit/default text encoding to read or write project files. Always specify
> UTF-8 explicitly, and verify suspicious output before editing.

- In PowerShell, use commands such as `Get-Content -Encoding UTF8` and `Set-Content -Encoding UTF8` if reading or writing text outside of dedicated patch tools.
- Prefer `rg` / `rg --files` for searching. They avoid many encoding surprises and are faster than recursive PowerShell searches.
- Avoid broad destructive commands. Resolve exact target paths before deleting or moving files.

## Common Commands

Use the Windows Gradle wrapper from the repository root:

- Compile: `.\gradlew.bat compileJava`
- Run data generation: `.\gradlew.bat runData`
- Build: `.\gradlew.bat build`
- Run client: `.\gradlew.bat runClient`
- Run server: `.\gradlew.bat runServer`

Notes:

- Java toolchain is configured for Java 21 in `build.gradle`.
- Gradle configuration cache is enabled in `gradle.properties`; avoid changes that break cacheability unless necessary.
- If a command fails because a game/dev runtime dependency is unavailable, report the failing task and the relevant error lines instead of changing unrelated dependency versions.

## Source Layout

- `src/main/java/`: hand-written Java source.
- `src/main/resources/`: hand-written resources, assets, mixin config, language files, and data files.
- `src/main/templates/`: resource templates expanded by the `generateModMetadata` Gradle task.
- `src/generated/resources/`: generated data/assets included in the main resources source set.
- `.tmp/<modid>_src/`: temporary extraction/staging location for source files.

When extracting or staging source files temporarily, use `.tmp/<modid>_src/` instead of creating any other folder.

## Generated Resources

- Prefer changing data-generation code under `src/main/java/.../content/data/` and then running `.\gradlew.bat runData`.
- Do not manually edit files under `src/generated/resources/` unless the user explicitly asks for a direct resource patch or generation is not available.
- If generated output changes, review the resulting JSON diffs for unintended churn before finishing.

## Coding Conventions

- Follow the existing package boundaries:
  - `api/registrate`: reusable registration builders/helpers.
  - `compat/`: optional mod integrations.
  - `content/registries`: registry declarations.
  - `content/data`: data-generation declarations.
  - `content/*`: gameplay/content implementation.
  - `mixin/`: mixins grouped by target mod/package.
- Prefer existing helpers such as `ShHsCreateCore.rl(...)`, `ShHsRegistrate`, and registry classes over introducing parallel registration patterns.
- Keep common setup work on the correct NeoForge event bus and enqueue thread-sensitive work when the surrounding code does so.
- Preserve optional-mod boundaries. Code under `compat/` and conditional paths should not force-load classes from optional dependencies unless guarded by the existing `Mods` helper or equivalent safe loading pattern.
- Keep comments short and useful. Avoid restating obvious Java syntax.

## Mixins

- Mixin classes live under `src/main/java/.../mixin/` and must remain consistent with `src/main/resources/shhs_create_core.mixins.json`.
- When adding, renaming, or moving a mixin, update the mixin config in the same change.
- Be conservative with target signatures and injection points. Prefer stable method boundaries and document fragile injections briefly when they depend on local variables or obfuscated names.
- For compatibility mixins targeting another mod, keep the package path aligned with the target mod to make ownership clear.

## Assets, Lang, and Data

- Keep resource locations lowercase and namespaced.
- Update `zh_cn.json` for user-facing Chinese text. If English generated language output is affected, update the data-generation source rather than editing generated lang JSON by hand.
- For block/item additions, check the matching registry, model/blockstate, loot table, recipe, creative tab, and translation surfaces as applicable.
- Avoid changing binary assets (`.png`, `.nbt`, `.jar`) unless the task explicitly requires it.

## Dependency Guidance

- Version constants live in `gradle.properties`; dependency wiring lives in `build.gradle`.
- Prefer repository-published dependencies over adding new local jars to `libs/`.
- Do not refresh, upgrade, or normalize dependency versions as part of unrelated feature or bug-fix work.

## Verification Expectations

- For Java-only changes, run `.\gradlew.bat compileJava` when practical.
- For registry/resource/data-generation changes, run `.\gradlew.bat runData` and then `.\gradlew.bat compileJava` when practical.
- For broad integration changes, run `.\gradlew.bat build` when practical.
- If verification is skipped, explain why and state the most relevant command the next agent or developer should run.

## Git Hygiene

- The worktree may contain user changes. Do not revert or overwrite unrelated changes.
- Before editing, check whether the target files already have modifications.
- Keep changes scoped to the user request. Avoid formatting-only churn in unrelated files.
- Do not commit unless the user explicitly asks for a commit.
