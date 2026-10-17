# Documentation Maintenance

This document is the maintenance index for Combatant's Markdown documentation.
Keep it current whenever a change affects installation, building, release
artifacts, user-facing behavior, commands, configuration, compatibility,
licensing, or contributor expectations.

## Documentation Index

- `README.md` - public project overview, screenshots, requirements,
  installation, supported integrations, addon entry points, and license links.
- `BUILDING_GUIDELINES.md` - local build commands, Java and Fabric targets,
  generated native resources, MSDF assets, Maven publishing, and native bridge
  notes.
- `CLIENT_COMMANDS.md` - command syntax and optional integration commands.
- `CONFIGS_AND_HUD.md` - profile files, configuration loading, HUD setup, and
  HUD editing behavior.
- `CONTRIBUTING.md` - contribution scope, evidence expectations, code quality,
  dependency rules, licensing, and review requirements.
- `SHADERPACK_PATCHES.md` - bundled Iris shaderpack patch manifests and the
  validation workflow.
- `SOUND_SYSTEM.md` - sound replacement, sound registry behavior, and resource
  pack interaction notes.
- `CREDITS.md`, `THIRD_PARTY_NOTICES.md`, and `THIRD_PARTY_LICENSES/` - required
  attribution and license records.

## Source Of Truth

Use these files as the source of truth when updating docs:

- Project version, artifact name, mod id, and dependency versions:
  `gradle.properties`.
- Gradle tasks, source sets, bundled libraries, and publishing configuration:
  `build.gradle`.
- GitHub release and package publishing behavior: `.github/workflows/`.
- Runtime resources and generated assets: `src/main/resources/` and the
  generation tasks documented in `BUILDING_GUIDELINES.md`.
- User-facing command names and aliases: command classes under
  `src/main/java/combatant/client/features/command/`.
- Module names, settings, and localization keys: module classes under
  `src/main/java/combatant/client/features/module/` plus
  `src/main/resources/assets/combatant/lang/`.

Do not copy version numbers from old release notes or screenshots when a Gradle
property exists. Update every public mention of a changed version in the same
patch.

## Writing Rules

- Do not use emoji in documentation.
- Keep headings short and descriptive.
- Prefer direct instructions over marketing copy.
- Use exact commands and paths in code blocks.
- Keep warnings factual and tied to a real constraint.
- Link to an existing detailed guide instead of duplicating large sections.
- Keep screenshots optional context; never let a screenshot be the only source
  of required setup or behavior.
- Use ASCII punctuation unless the surrounding file already uses a specific
  character set for names or attributions.

## Maintenance Checklist

Before merging documentation changes:

1. Check `gradle.properties` for current versions.
2. Check `build.gradle` for task names and packaging behavior.
3. Update `README.md` if installation, requirements, integrations, release jar
   names, or major guide links changed.
4. Update `BUILDING_GUIDELINES.md` if build commands, source sets, native
   handling, generated assets, or publishing changed.
5. Update `CLIENT_COMMANDS.md` when command syntax, aliases, permissions,
   optional mod behavior, or examples change.
6. Update `CONFIGS_AND_HUD.md` when profile loading, config file behavior, HUD
   editing, or UI flow changes.
7. Update `CREDITS.md`, `THIRD_PARTY_NOTICES.md`, and `THIRD_PARTY_LICENSES/`
   when code, assets, fonts, libraries, or design references are added,
   removed, or relicensed.
8. Run a Markdown scan for conflict markers, stale known version strings, and
   emoji before committing.

Useful checks:

```powershell
git grep -n "<<<<<<<\|=======\|>>>>>>>" -- "*.md"
git grep -n "combatant-0.1.\|0.9.0+mc26.2\|0.9.0-fabric" -- "*.md"
```

## Release Update Flow

When preparing a release:

1. Update `mod_version` in `gradle.properties`.
2. Update the installation jar name in `README.md`.
3. Confirm dependency links in `README.md` and `BUILDING_GUIDELINES.md`.
4. Run the documented build command.
5. Confirm the release workflow expects the same artifact name:
   `build/libs/combatant-<version>.jar`.
6. Update any release-specific notes without changing attribution records unless
   dependency or asset contents changed.

## External Context

The project instruction set requires the linked Google Doc to be reviewed during
work. That document is not a technical source for Combatant's repository
documentation. Use repository files and official dependency metadata for
documentation facts.
