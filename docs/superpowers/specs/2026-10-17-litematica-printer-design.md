# SchematicBuilder: Litematica as a schematic source

Date: 2026-10-17 · Branch: `lb/printer-263` (off `lb/sync-26.3`) · Target: Minecraft 26.3

## Goal

`SchematicBuilder` (aliases Printer / LitematicaPrinter) can build the schematic the player has placed in
Litematica, using Litematica's placement, sub-region transforms and layer range, while keeping every existing
builder feature: anticheat presets, rotations, walking/pillaring, redstone ordering, restock, helpers.
Litematica stays optional. Without it the client behaves exactly as today.

## Non-goals

- No new module; no second copy of the build logic.
- No mixins into Litematica or MaLiLib. We only call its public API, so we have no hooks inside it to undo in
  panic mode.
- The built-in `.litematic` reader and `SchematicProjection` are not removed.
- We don't reimplement Litematica's easy-place.

## Design

### 1. Source setting

`SchematicBuilder` gets `source`: `COMBATANT` (current behaviour, default) / `LITEMATICA`. The option only
appears when `FabricLoader.isModLoaded("litematica")`. If a config selects `LITEMATICA` but the mod is absent,
the module warns once and falls back to `COMBATANT`.

### 2. Bridge (only class that touches Litematica)

Package `combatant.client.compat.litematica`:

- `LitematicaCompat`: the only entry point. It is always safe to load and checks `isModLoaded` before touching
  `LitematicaBridge`, which is the only class that imports `fi.dy.masa.*`. This follows the existing
  Xaero/ViaFabricPlus optional-mod pattern.
- `LitematicaBridge`:
  - `selectedPlacement()`: `DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement()`.
    If none is selected, it uses the only enabled placement. If there are several, the player must select
    one in Litematica.
  - `snapshot(placement)`: builds a Combatant `Schematic` plus `SchematicPlacement` (world origin = minimum
    corner of the placement's enclosing box). It reads every enabled sub-region from the placement's
    `LitematicaSchematic` container and applies the placement and sub-region rotation/mirror with Litematica's
    own transform utilities. Block states are rotated/mirrored the same way Litematica renders them. Disabled
    sub-regions are skipped.
  - `revision(placement)`: a cheap key (origin, rotation, mirror, enabled flags, sub-region offsets, schematic
    file, last-modified). A change triggers a new snapshot.
  - `layerRange()`: `DataManager.getRenderLayerRange()` converted to a `LayerMask` (axis plus min/max inclusive
    in world coordinates).
  - `easyPlaceEnabled()`: reads Litematica's `easyPlaceMode` config.

### 3. Feeding the builder

`SchematicState` gets `loadExternal(Schematic, SchematicPlacement, LayerMask)`. It marks the state as
externally owned, so the schematic menu cannot load or move a file over it.

Every 10 ticks, `SchematicBuilder` in `LITEMATICA` mode asks the bridge for the revision. On change it
re-snapshots, then calls `loadExternal` and `tickDiff` (existing diff and attention data are reset).

Layer handling:

- With a Y-axis mask, the builder's working layers are clamped to `[min, max]`, and auto-advance stays inside
  them. This gives "build only heights N..M".
- With an X or Z mask, cells outside it count as not wanted, so the diff leaves them alone. Layers are still
  built bottom-up.

Everything downstream (`SchematicDiff`, `BuildPlanner`, `BuildSafety`, the walker, materials, shortfall
notifications) runs unchanged on the snapshot.

### 4. Rendering and UI

- In `LITEMATICA` mode `SchematicProjection` draws nothing for the external schematic, because Litematica
  already renders it. Attention markers (wrong or hazardous cells) still draw.
- The schematic menu shows "Following Litematica: <placement name>" plus the layer range, and disables
  load and move.
- If `easyPlaceMode` is on, the module warns and pauses: two placers clicking the same cells would flag.

### 5. Anticheat

There is no new placement path. Litematica cells go through the same `BuildPlanner` and rotation gating, and
the anticheat preset (with the preset clamping from `lb/preset-clamp-263`) applies unchanged.

### 6. Panic / unhook

- On soft panic the existing `ModuleManager` handling disables `SchematicBuilder`. Extra steps:
  - `LitematicaCompat` drops its snapshot and stops polling.
  - `SchematicState.loadExternal` content is cleared, so nothing of ours stays drawn.
  - The menu entry is gone, because Combatant UI is hidden.
- Litematica itself is untouched: it is a normal, visible mod, and since we have no mixins into it there is
  nothing to unhook.
- `restoreAfterSoftPanic` re-enables the module, and the next poll re-snapshots.

### 7. Build setup

- `build.gradle`: `modCompileOnly` Litematica + MaLiLib for 26.3 (versions in `gradle.properties`).
- They are added to the runtime only under `-PwithOptionalModRuntime=true` (the existing flag), so the release
  jar does not bundle or require them.
- `fabric.mod.json`: `litematica` goes under `suggests`.

## Errors

- No placement, or several placements and none selected: status `NO_SCHEMATIC` plus a one-time notification
  saying what to do.
- Snapshot failure (an API change in a new Litematica version): caught and logged once. The module falls back
  to `COMBATANT` with a warning naming the Litematica version. The client never crashes.
- Placement larger than our dense-box limit: the existing reader limit applies, with the same warning.

## Testing

- Unit:
  - `LayerMask` conversion for all Litematica layer modes and axes.
  - Snapshot transforms: rotation × mirror for a 2-region fixture compared against the expected world
    positions and states. The transform math is pure, so it is tested without Litematica on the classpath.
  - Revision change triggers a re-snapshot.
- Smoke (`runClient -PwithOptionalModRuntime=true`):
  - Place a rotated and mirrored schematic in Litematica.
  - Set the layer range to Y 2..4.
  - Run SchematicBuilder in `LITEMATICA` mode and confirm it builds only those layers, matching Litematica's
    overlay.
  - Toggle panic and confirm the builder stops and leaves nothing drawn. Exit panic and confirm it resumes.
  - Start once without Litematica installed and confirm no crash and that the source option is hidden.
- Grim: `lp_` harness scenarios. These need Litematica on the harness client. They run the existing
  SchematicBuilder grim-preset scenarios with `source=LITEMATICA` and must be clean for 3 rounds.
