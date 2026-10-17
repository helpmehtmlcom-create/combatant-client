# Visual features gap: reference client vs Combatant (26.3)

Source of ideas only (read-only reference, MC 1.21.11). Nothing is copied; every item below is a reimplementation target in our architecture (Renderer3D / world phases / Module settings API).

| Reference feature | Our closest module | Missing options / looks | Decision |
|---|---|---|---|
| BlockEsp (per-block palette colors, alpha, tracers) | `visuals/BlockESP` (Sodium-fed scan, LOS/unrestricted, tracers, glow fill, shapes) | Per-block color (we have only visible/chunk color) | EXPAND: `color_by_block` (hash hue per block id) |
| BedrockEsp (enclosed non-bedrock pockets in bedrock layers, min size, Box/Outline/Both, chat notice) | none (`HoleESP` is a different feature) | whole feature | ADD new module `finder/BedrockESP` |
| StorageEsp (chest/ender/shulker/spawner/furnace/barrel/... per-type colors, double-chest merge, tracers) | none for storage (finders only cover spawners/hives) | whole feature | ADD new module `visuals/StorageESP` |
| PlayerEsp (alpha, line width, tracers) | `visuals/ESP` + `Tracers` (much richer) | none | SKIP, covered |
| ItemOutline (neon outline on first-person held item) | `Chams` hand effects (Smoke/Metallic/Glass/Rift + glow/edge, ghosting) | none worth adding | SKIP, covered by Chams hands |
| HandAura (hand glow/rim/sheen via framebuffer) | `Chams` hands (HandChamsComputeBackend, glow, shadow, Iris mask) | none | SKIP, covered |
| BlockOverlay (style Both/Outline/Fill, gradient/solid/rainbow, flow, glow, smooth glide, fade in/out, face only, see-through, mining progress) | `visuals/BlockHighlight` (lines/box, color modes, depth test, breaking highlight) | Smooth box glide, fade in/out, face-only | EXPAND: `smooth`, `smooth_speed`, `face_only` |
| TargetHud | `hud/draggable/impl/TargetHud` (87K) | none | SKIP |
| HudModule (watermark, time, coords, modules list) | HUD elements (Coordinates, ModuleList, GameTime, ...) | none | SKIP |
| Character3d (glTF baked 3D character replacement) | none | asset pipeline (glTF/SDF baker) | SKIP: asset-heavy, out of scope |
| CustomSkys (themes, milky way, shooting stars, aurora cold biomes) | `ReimaginedVisual` skybox shader (aurora, small stars) + `WorldTweaks` sky color | themes, shooting stars, milky way | DEFER: needs sky shader/UBO changes owned by renderer work |
| CustomTotem / CustomTools (3D item designs, pop FX) | `TotemFX` (pop burst) | procedural item models | SKIP: asset-heavy |
| Halloween (darkness, fog, event intro) | `WorldTweaks` (fog start/end/color, sky color, time) | seasonal intro/ghost overlay | SKIP: event gimmick, fog covered |
| Freelook (distance, sensitivity, through walls, invert Y) | `visuals/FreeLook` (sensitivity, perspective) | distance, through walls, invert Y | EXPAND: `distance`, `through_walls`, `invert_y` (CameraMixin / MouseMixin hooks) |
| StaticHitboxes / HitboxModule | `combat/Hitbox` | pose-forcing is gameplay | SKIP |
| RegionMap (server region grid) | none | server-specific | SKIP |
| NameProtect | `misc/NameProtect` | none | SKIP, covered |
| SkinProtect (local skin/cape replacement by username, self or everyone) | none | whole feature | ADD `misc/SkinProtect` (hook `PlayerInfo.getSkin`, Mojang lookup via `SkinManager.createLookup`) |
| FakeScoreboard / OgScoreboard | `hud/draggable/impl/Scoreboard` | fake stat spoofing | SKIP: spoofing, non-visual intent |
| Chunk finders (Sus/Prime/Lucky/Netherite/...) | `visuals/finder/*` | none | SKIP, covered |
| MinecraftUiRenderer look | our ClickGUI/UI pass compiler | n/a | SKIP |
| Auction/pay/spawner-sell/StaffList/Vanish | n/a | server-specific gameplay | SKIP |

## Implementation status

Not implemented in this pass (run ended on its request budget after the inventory phase): all ADD/EXPAND rows above. Worktree `C:/Users/voidg/kv/visual263` (branch `lb/visual-263`) is created and merged with `sync-26.3`. Integration notes gathered for the follow-up:

- Modules are auto-discovered by `@ModuleInfo`; lang keys are `module.<id>.description`, `setting.<id>.<settingId>`, `setting.<id>.<settingId>.option.<X>` in `en_us.json` and `ru_ru.json`.
- World drawing: override `getWorldPhase()` (END_MAIN) and `onRenderWorldEngine(Renderer3D, Renderer3D, float)`; use `renderer.filledBox/outlineBox/line`, `Renderer3D.Culling.isInFrustum`, tracers through `Tracers.render3DTracersRaw`.
- Panic: module hooks run only while modules are enabled and `RuntimeGate.canRunModules()`; mixin hooks (skin, camera) must early-out on `!RuntimeGate.canRunClientLogic()`.
- Skin hook: `net.minecraft.client.multiplayer.PlayerInfo#getSkin()`; lookup via `SkinManager.createLookup(GameProfile, false)` (UUID by name via the existing `features/account/SkinManager.fetchUuid`, currently private).
- FreeLook hooks live in `mixins/CameraMixin` (`alignWithEntity`, fixed distance `4.0F`, `getMaxZoom`) and `mixins/MouseMixin`.
