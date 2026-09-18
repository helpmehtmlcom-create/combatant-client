# Learnings

Corrections, insights, and knowledge gaps captured during development.

**Categories**: correction | knowledge_gap | best_practice
**Areas**: frontend | backend | infra | tests | docs | config
**Statuses**: pending | in_progress | resolved | wont_fix | promoted | promoted_to_skill

## Status Definitions

| Status | Meaning |
|--------|---------|
| `pending` | Not yet addressed |
| `in_progress` | Actively being worked on |
| `resolved` | Issue fixed or knowledge integrated |
| `wont_fix` | Decided not to address (reason in Resolution) |
| `promoted` | Elevated to CLAUDE.md, AGENTS.md, or copilot-instructions.md |
| `promoted_to_skill` | Extracted as a reusable skill |

## Skill Extraction Fields

When a learning is promoted to a skill, add these fields:

```markdown
**Status**: promoted_to_skill
**Skill-Path**: skills/skill-name
```

---
## [LRN-20260916-001] best_practice
**Logged**: 2026-09-16T11:45:00Z
**Priority**: high
**Status**: promoted
**Promoted**: CLAUDE.md
**Area**: backend
### Summary
Minecraft 26.2 Mojang mappings convention for player inventory and input access.
### Details
In Minecraft 26.2:
1. Player selected hotbar slot is retrieved via `player.getInventory().getSelectedSlot()` rather than direct field access.
2. Movement inputs are stored inside `player.input.keyPresses` (an instance of `Input` record).
3. Effect holders use `MobEffects.HASTE` and `MobEffects.MINING_FATIGUE` directly from `BuiltInRegistries.MOB_EFFECT`.
### Suggested Action
Always verify accessors against deobfuscated Minecraft 26.2 JAR via `javap` before refactoring utility classes.
### Metadata
- Source: compilation_fixes
- Related Files: src/main/java/combatant/client/util/block/mining/MiningDamageCalculator.java, src/main/java/combatant/client/util/player/inventory/manager/InventoryManager.java
- Tags: minecraft26_2, mappings, input, inventory

---

## [LRN-20260916-002] best_practice
**Logged**: 2026-09-16T12:00:00Z
**Priority**: medium
**Status**: promoted
**Promoted**: CLAUDE.md
**Area**: frontend
### Summary
Complete module descriptions in @ModuleInfo eliminate ClickGUI and command confusion.
### Details
Previously, 90+ modules lacked descriptive text, showing `//todo Description` or empty placeholders in tooltips.
Populating `description = "..."` for every module ensures clear player understanding of module mechanics, protections, and anti-cheat behaviors in the ClickGUI search, settings headers, and the `.modules` command.
### Suggested Action
Enforce that every new module declared with `@ModuleInfo` must provide a non-empty, player-facing `description` attribute explaining its function and usage.
### Metadata
- Source: user_feedback
- Related Files: src/main/java/combatant/client/features/module/ModuleInfo.java, src/main/java/combatant/client/features/module/modules/
- Tags: ux, clickgui, documentation, modules

---

## [LRN-20260916-003] best_practice
**Logged**: 2026-09-16T12:15:00Z
**Priority**: high
**Status**: resolved
**Area**: backend
### Summary
EventBus polymorphic dispatch with CopyOnWrite exact-handler arrays and fallback hierarchy cache.
### Details
Event dispatch in a utility client must achieve zero allocation on hot tick/render loops while supporting inheritance (e.g. `PacketEvent.Receive` is a subclass of `PacketEvent`). Precomputing flattened subscriber hierarchies and checking `hasListeners` guards prevents unnecessary event object allocations when no module listens.
### Suggested Action
Maintain cached flat subscriber arrays updated on registration/unregistration rather than traversing class hierarchy at dispatch time.
### Metadata
- Source: performance_audit
- Related Files: src/main/java/combatant/client/events/EventBus.java, src/main/java/combatant/client/mixins/ConnectionMixin.java
- Tags: eventbus, performance, zero_allocation

---

## [LRN-20260916-004] best_practice
**Logged**: 2026-09-16T13:30:00Z
**Priority**: high
**Status**: resolved
**Area**: frontend
### Summary
Setting tooltip and description architecture in ClickGUI hint overlay.
### Details
`SettingDef` and abstract `Setting` support programmatic `.description()` and `.tooltip()` declarations as well as localized `<setting_translation_key>.desc` keys. `SettingsPanelComponent` captures the hovered setting during row iteration and displays `Setting Title — Tooltip` directly in the bottom-left `ClickGuiHintOverlay`, providing immediate user guidance without obstructing the panel.
### Suggested Action
Attach `.description(...)` to all multi-mode or complex numeric settings in gameplay modules.
### Metadata
- Source: settings_ux_audit
- Related Files: src/main/java/combatant/client/config/SettingDef.java, src/main/java/combatant/client/features/gui/clickgui/settings/Setting.java, src/main/java/combatant/client/features/gui/clickgui/layout/screen/settings/implement/module/SettingsPanelComponent.java
- Tags: settings, tooltips, clickgui, ux

---

## [LRN-20260916-005] best_practice
**Logged**: 2026-09-16T13:40:00Z
**Priority**: critical
**Status**: promoted
**Promoted**: CLAUDE.md
**Area**: backend
### Summary
Thread-safe state handling for Netty packet event listeners.
### Details
`PacketEvent.Receive` is fired synchronously on Netty worker threads. Handlers modifying state (e.g. `Blocker.java` storing block destruction targets in `placePositions`) must use concurrent data structures like `ConcurrentHashMap.newKeySet()` or dispatch to the main game loop via `mc.execute()` to prevent `ConcurrentModificationException` during main-thread tick iterations.
### Suggested Action
Audit all `PacketEvent` listeners to ensure any collections modified from incoming packets are thread-safe.
### Metadata
- Source: crash_hazard_audit
- Related Files: src/main/java/combatant/client/features/module/modules/combat/Blocker.java
- Tags: concurrency, netty, packet_event, thread_safety

---

## [LRN-20260916-006] best_practice
**Logged**: 2026-09-16T13:45:00Z
**Priority**: high
**Status**: promoted
**Promoted**: CLAUDE.md
**Area**: backend
### Summary
NaN and zero-magnitude vector defense in rotation math.
### Details
Calculating yaw and pitch angles via `Math.atan2` and `Math.hypot` without guarding against `Double.isNaN` or near-zero magnitudes (`< 1.0E-7`) can propagate `NaN` or `Infinity` into Minecraft movement and rotation packets (`ServerboundMovePlayerPacket.Rot`), causing server kicks or desyncs. Always clamp and return `Rotation.ZERO` when distance is negligible.
### Suggested Action
Use `RotationUtil.calculateRotations` and `Rotation.fromRotationVec` with builtin NaN and near-zero clamping.
### Metadata
- Source: crash_hazard_audit
- Related Files: src/main/java/combatant/client/util/aiming/RotationUtil.java, src/main/java/combatant/client/util/aiming/data/Rotation.java
- Tags: math, rotations, nan_safety, aiming


---

## [LRN-20260918-001] best_practice
**Logged**: 2026-09-18T17:30:00Z
**Priority**: high
**Status**: promoted
**Promoted**: CLAUDE.md
**Area**: backend

### Summary
Integer clamping and identity transforms for RHI fullscreen draw passes in SodiumGlBackend.

### Details
In `SodiumGlBackend.drawFullscreen(FullscreenDrawCommand command)`:
1. `command.colorAttachment` dimensions must be retrieved with explicit mip level (`getWidth(0)`, `getHeight(0)`) and clamped with `Math.max(1, ...)`.
2. Viewport payload in `MeshUniforms.update(...)` and `RenderPass.RenderArea(0, 0, width, height)` requires integer dimensions (`int width`, `int height`). Passing raw floats or zero causes viewport distortion or invalid render pass bounds.
3. Persistent fullscreen quads span `[-1, +1]` in normalized device / clip coordinates; vertex shader uniforms must bind identity projection and model-view matrices rather than world-space matrices to avoid shrinking or offsetting fullscreen post/debug presentation passes.

### Suggested Action
Always verify fullscreen RHI passes use identity matrices and integer-clamped attachment dimensions for render areas and uniforms.

### Metadata
- Source: commit_audit
- Related Files: src/main/java/combatant/client/render/engine/rhi/backend/SodiumGlBackend.java
- Tags: rendering, rhi, opengl, sodium, shaders
- See Also: ERR-20260916-001
- Pattern-Key: harden.rhi_fullscreen_viewport
- Recurrence-Count: 1
- First-Seen: 2026-09-18
- Last-Seen: 2026-09-18
