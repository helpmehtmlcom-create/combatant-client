# Feature Requests

Missing capabilities requested by users, captured during development.

**Areas**: frontend | backend | infra | tests | docs | config
**Statuses**: pending | in_progress | resolved | wont_fix
**Complexity**: simple | medium | complex

## Status Definitions

| Status | Meaning |
|--------|---------|
| `pending` | Not yet addressed |
| `in_progress` | Actively being built |
| `resolved` | Capability implemented (add Resolution block) |
| `wont_fix` | Decided not to build (reason in Resolution) |

Entry format: see the self-improvement skill's "Feature Request Entry" section. IDs use `FEAT-YYYYMMDD-XXX`.

---
## [FEAT-20260916-001] module_search_tags
**Logged**: 2026-09-16T12:30:00Z
**Priority**: medium
**Status**: pending
**Area**: frontend
### Requested Capability
Interactive tag-based filtering and fuzzy search across all module descriptions in ClickGUI and command autocomplete.
### User Context
With 100+ modules available, players want to search for keywords like "crystal", "elytra", "anti-cheat", or "visual" and see all relevant modules highlighted instantly.
### Complexity Estimate
simple
### Suggested Implementation
Index `description` and `aliases` in `ModuleManager` into a lightweight in-memory trie or lowercase token inverted index used by `ClickGuiRenderer` search field and `@modules` command.
### Metadata
- Frequency: recurring
- Related Features: ClickGUI, ModulesCommand

---

## [FEAT-20260916-002] setting_quick_presets
**Logged**: 2026-09-16T13:50:00Z
**Priority**: medium
**Status**: pending
**Area**: frontend
### Requested Capability
Per-module quick presets dropdown (e.g. "Strict / NCP", "Hypixel", "Anarchy / 2b2t", "Grim AC") in ClickGUI setting header.
### User Context
Configuring 20+ intricate settings for AutoCrystal or KillAura is tedious for new users; one-click server profile presets significantly improve understandability and out-of-the-box experience.
### Complexity Estimate
medium
### Suggested Implementation
Define `SettingPreset` record containing map of config values and expose a preset selection pill in `SettingsPanelComponent` header.
### Metadata
- Frequency: recurring
- Related Features: SettingsPanelComponent, ConfigProfileService

