---
name: combatant-navigator
description: >-
  Navigate Combatant Client's architecture, subsystems, and codebase. Use when finding where
  features, modules, settings, renderers, mixins, commands, events, or configs are located,
  understanding code organization, or tracing how data flows through this Minecraft utility client.
---

# Combatant Client Codebase Navigator

This skill is the architectural map and navigation guide for **Combatant Client** (Minecraft 26.2 on Fabric Loader 0.19.3).

---

## 1. Top-Level Repository Structure

```text
combatant-client/26.2/
├── BUILDING_GUIDELINES.md      # Build requirements, Gradle targets, Java 25 notes, MSDF generation
├── CLIENT_COMMANDS.md          # Complete reference for all 18+ in-game @commands
├── CONFIGS_AND_HUD.md          # Guide to .cbcfg profiles (modules/hud/themes) & HUD layout
├── CONTRIBUTING.md             # Contribution rules, licensing, code standards, review bar
├── DOCUMENTATION.md            # Maintenance index and documentation guidelines
├── LICENSE                     # GNU General Public License v3.0 (GPL-3.0-only)
├── README.md                   # Public overview, screenshots, supported mods, installation
├── SHADERPACK_PATCHES.md       # Iris shaderpack patches, OpenGL limitations, manifest validation
├── SOUND_SYSTEM.md             # Custom sound replacement and registry interaction
├── build.gradle                # Loom setup, dependencies (Fabric, Sodium, Iris, Xaero), source sets
├── gradle.properties           # Single source of truth for mod & dependency versions
├── settings.gradle             # Project definitions and subprojects (:registry-processor)
├── registry-processor/         # Annotation processor generating metadata catalogs
├── tools/                      # MSDF generator binaries and PowerShell automation
│   └── msdf/                   # msdfgen, msdf-atlas-gen, GenerateFontMsdf.ps1, GenerateSvgMsdf.ps1
└── src/
    ├── main/                   # Core client source code and assets
    ├── dev/                    # Development-only modules, debug panels, dev runtime
    ├── duplexCourier/          # Cross-boundary invocation and runtime annotations
    ├── vulkanDebug/            # Vulkan validation layer hooks and GPU debug markers
    └── svgMsdfTool/            # CLI tool for compiling SVGs to MSDF textures
```

---

## 2. Core Package Architecture (`src/main/java/combatant/client/`)

| Package | Primary Responsibility | Key Classes / Interfaces |
| --- | --- | --- |
| `combatant.client` | Entrypoint and lifecycle coordination | `Combatant` (`ClientModInitializer`), `ClientRuntime` |
| `addon/` | Internal addon loader, runtime hooks, module extensions | `AddonManager`, `ModuleExtensionManager`, `ClickGuiSectionManager` |
| `api/v0/` | Public API exposed to external Fabric addons | `CombatantAddon`, `CombatantAddonContext`, `CombatantModuleExtension` |
| `config/` | Setting definitions, `.cbcfg` profile serialization | `MainConfig`, `ConfigSerializer`, `SettingDef`, `BooleanValue`, `NumberValue` |
| `events/` | High-throughput event bus & event contracts | `EventBus`, `Events`, `Event`, `EventHandler`, `UsedImplicitly` |
| `events/impl/` | Built-in tick, motion, packet, render events | `GameTickEvent`, `PlayerMoveEvent`, `PacketEvent`, `RenderPrewarmCollectEvent` |
| `features/module/` | Module registry, lifecycle, and categories | `ModuleManager`, `Module`, `ModuleInfo`, `ModuleCategory`, `ModuleAutoLoader` |
| `features/module/modules/` | All 100+ built-in modules organized by category | Subpackages: `combat/`, `movement/`, `player/`, `visuals/`, `misc/` |
| `features/command/` | Client command dispatch (`@...`) | `CommandManager`, `ClientCommand`, `CommandInfo`, `CommandContext` |
| `features/gui/clickgui/` | Modern ClickGUI, search, setting editors | `ClickGuiScreen`, `ClickGuiEditorScreen`, `ClickGuiPickerScreen`, `ClickGuiRenderer` |
| `features/gui/hud/` | HUD element registry, draggable & static elements | `DraggableHudElement`, `StaticHudElementRegistry`, `HudRenderSpace` |
| `features/theme/` | Color palettes, themes, and theme persistence | `Themes`, `ThemeStore`, `Theme`, `EditableClickGuiTheme` |
| `features/account/` | Alt manager, session token management | `CombatantAltManagerScreen`, `AccountConfig` |
| `features/relations/` | Friend, Enemy, and Staff management | `RelationsComponent`, `OnlineRelationPlayerPickerComponent` |
| `features/map/` | Minimap/Worldmap integration, waypoints | `HeuristicRuntime`, `MapLocationRuntime` |
| `compat/` | Third-party mod bridges (Xaero, Iris, Sodium) | `XaeroWaypointHudOverlay`, `SodiumRenderBridge`, `ShaderPatchEngine` |
| `mixins/` | Fabric Mixin bytecode injectors | `GameRendererMixin`, `LevelRendererMixin`, `CameraMixin`, `GpuSurfaceMixin` |
| `mixininterface/` | Duck typing interfaces cast onto vanilla classes | `IGuiGraphics`, `IRenderPipeline`, `ILocalPlayer`, `IVulkanBackendInfo` |
| `render/engine/` | Custom RHI, deferred pipeline, UI batching | `rhi/`, `deferred/`, `renderer/ui/`, `text/`, `postprocess/`, `material/` |
| `runtime/` | Failure boundaries, panic recovery, shutdown | `FailureIsolation`, `RuntimeGate`, `RuntimeShutdownParticipant` |
| `util/` | Math, player physics, packets, aiming, combat | `RotationManager`, `BlinkManager`, `AntiBotTracker`, `TargetManager` |

---

## 3. Module Subsystem Directory Map (`features/module/modules/`)

Modules are segregated into 5 primary functional categories:

### A. Combat (`features/module/modules/combat/`)
- **Crystal Combat:** `AutoCrystal.java` (with `autocrystal/` planners, trackers, damage rules, evaluation)
- **Anchor & Bed Combat:** `AutoAnchor.java` (and `autoanchor/`), `AutoBed.java` (and `autobed/`), `AntiBed.java`
- **Aura & Offense:** `KillAura.java`, `AutoAttack.java`, `Criticals.java`, `Reach.java`, `RubberHand.java`, `MaceKill.java`, `SpearAssist.java`, `BowBomb.java`, `BowSpam.java`
- **Defense & Trapping:** `Surround.java`, `SelfTrap.java`, `AutoTrap.java`, `Burrow.java`, `AntiCev.java`, `CevBreaker.java`, `AutoCity.java`, `AutoWeb.java`, `HoleFill.java`, `HoleMiner.java`, `Blocker.java`, `FeetPlace.java`
- **PVP Utilities:** `AntiTotem.java`, `AntiWeakness.java`, `AntiRegear.java`, `Backtrack.java`, `Hitbox.java`, `PvpCooldowns.java`, `ProjectilePuncher.java`, `TPSSync.java`, `KTLeave.java`, `ElytraTarget.java`, `AttributeSwap.java`

### B. Movement (`features/module/modules/movement/`)
- **Locomotion:** `Speed.java`, `Strafe.java`, `TargetStrafe.java`, `Sprint.java`, `Step.java`, `ReverseStep.java`, `SafeWalk.java`, `Parkour.java`
- **Air & Elytra:** `Flight.java`, `ElytraFly.java`, `ElytraHelper.java`, `AnchorFly.java`, `BoatFly.java`, `PacketFly.java`, `SuperFirework.java`, `WindJump.java`, `AirJump.java`
- **Velocity & Modification:** `Velocity.java`, `NoSlow.java`, `Timer.java`, `NoFall.java`, `FastFall.java`, `HoleSnap.java`, `Phase.java`, `AntiVoid.java`, `AutoDodge.java`, `Freeze.java`
- **Environment & Liquid:** `Jesus.java`, `FastSwim.java`, `EntityControl.java`, `InventoryMove.java`, `NoPush.java`, `NoStun.java`, `AutoWalk.java`, `AutoHighway.java`, `AutoTunnel.java`

### C. Player (`features/module/modules/player/`)
- **Automation:** `AutoEat.java`, `AutoTool.java`, `AutoArmor.java`, `AutoMend.java`, `AutoReplenish.java`, `AutoKit.java`, `InventorySorter.java`, `ChestStealer.java`, `EChestFarmer.java`
- **World Interaction:** `Scaffold.java`, `AirPlace.java`, `FastPlace.java`, `SpeedMine.java`, `ClickPearl.java`, `Offhand.java`, `MultiTask.java`, `LiquidInteract.java`, `NoDelay.java`, `NoInteract.java`
- **Network & Exploits:** `Blink.java`, `FakeLag.java`, `PacketExp.java`, `PortalChat.java`, `PortalGodMode.java`, `AntiHunger.java`, `ChorusExploit.java`, `XCarry.java`, `ShitDropper.java`

### D. Visuals (`features/module/modules/visuals/`)
- **ESP & Overlays:** `ESP.java`, `BlockESP.java`, `DropESP.java`, `PortalESP.java`, `SoundESP.java`, `TargetESP.java`, `HoleESP.java`, `StorageESP.java`, `BedwarsESP.java`, `Tracers.java`, `NameTags.java`, `Chams.java`
- **World & Camera:** `FullBright.java`, `Freecam.java`, `Zoom.java`, `FovControl.java`, `CameraClip.java`, `AspectRatio.java`, `ViewModel.java`, `BlockHighlight.java`, `NewChunks.java`, `LogoutSpots.java`
- **Rendering & Shaders:** `MotionBlur.java`, `PostFX.java`, `DamageTint.java`, `HitEffect.java`, `KillEffect.java`, `JumpCircles.java`, `Trails.java`, `TotemFX.java`, `WorldParticles.java`, `WorldTweaks.java`, `ReimaginedVisual.java`, `SeeInvisibles.java`, `Crosshair.java`, `ChorusPredict.java`, `Predictions.java`, `NoRender.java`, `TazikHat.java`

### E. Misc (`features/module/modules/misc/`)
- **Client & Security:** `ClickGui.java`, `Panic.java`, `AutoLog.java`, `AutoReconnect.java`, `AutoRespawn.java`, `AutoEZ.java`, `PopCounter.java`, `VisualRange.java`, `FakePlayer.java`, `DefineTarget.java`, `StashFinder.java`, `BetterMinecraft.java`, `MessageFilter.java`, `NoSound.java`, `HitSounds.java`

---

## 4. Rendering & Shader Engine Map (`render/engine/`)

The render engine is a custom low-level graphics pipeline abstracting Vulkan and OpenGL:

```text
render/engine/
├── rhi/                        # Render Hardware Interface
│   ├── backend/gl/             # OpenGL backend (GlAdvancedShaderBackend, GlStencilShapeClipBackend)
│   ├── backend/vulkan/         # Vulkan backend (VulkanAdvancedShaderBackend, VulkanStorageBuffer)
│   ├── shader/                 # Compute dispatch, std430 buffer writers, resource barriers
│   └── resource/               # FramebufferPool, TexturePool, transient targets
├── deferred/                   # Complete deferred rendering pipeline
│   ├── DeferredWorldPipeline   # Pipeline orchestrator
│   ├── DeferredPassGraph       # Topological render pass DAG
│   └── *Source.java            # Individual pass generators: AO, reflections, clouds, atmosphere,
│                               # dynamic lights, colored block lights, shadow resolve
├── renderer/
│   ├── Renderer2D.java         # Immediate & batched 2D draw primitives
│   ├── Renderer3D.java         # World-space lines, boxes, outlines, bounding boxes
│   ├── FullScreenRenderer.java # Fullscreen quads and composite blits
│   └── ui/                     # UI compilation & liquid glass batching
│       ├── UiPassCompiler.java # Compiles UI nodes into optimal draw batches
│       ├── UiBatchPlan.java    # Planned draw passes and clip bounds
│       ├── OrderedUiBatcher    # Sorts and executes z-ordered UI primitives
│       └── runtime/script/     # Javet V8 JavaScript UI runtime bridge
├── postprocess/                # Post-processing graph and passes
│   ├── PostProcessGraph.java   # Composite node graph
│   └── *ComputeBackend.java    # DepthOfField, SeparableMaskBlur, HandChams
└── text/                       # Multi-channel Signed Distance Field (MSDF) text rendering
    ├── TextRenderSystem.java   # Font cache and text batching
    ├── MsdfFont.java           # MSDF texture atlas sampler and kerning
    └── BuiltinFontCatalog.java # Built-in fonts (MatrixSans, Onest, Noto, Iosevka)
```

Shaders live in `src/main/resources/assets/combatant/shaders/`:
- `.comp`: Compute shaders for deferred stages (`atmosphere_*.comp`, `cloud_*.comp`, `reflection_*.comp`, etc.)
- `.frag` / `.vsh`: Liquid glass batches (`ui_liquid_glass_batch.frag`), module category surfaces, shader ESP

---

## 5. UI & HUD Scripting Subsystem (`features/gui/`)

Combatant pairs native Java components with high-performance Javet V8 scripts:

```text
src/main/java/combatant/client/features/gui/
├── clickgui/                   # Modern full-screen ClickGUI
│   ├── ClickGuiScreen.java     # Root screen (Modules, Settings, Configs, HUD, Themes, Relations)
│   ├── sections/               # Top-level tabs (ModulesSection, SettingsSection)
│   ├── settings/               # Setting editors (Sliders, Pickers, Toggles, ColorPickers)
│   └── picker/                 # Picker dialogs (Item, block, keybind, sound selection)
└── hud/                        # HUD Element management
    ├── draggable/              # Draggable HUD elements (Coordinates, Fps, Armor, Itemizer, etc.)
    │   └── impl/               # Script-backed and Java-backed element implementations
    ├── nondraggable/           # Static HUD replacements (CustomHealthBar, DynamicIsland, etc.)
    └── script/                 # Java bridges to Javet V8 scripts
```

Script assets live in `src/main/resources/assets/combatant/ui/`:
- `api/ui.d.ts`: Full TypeScript declaration of the node tree (`root`, `panel`, `row`, `column`, `text`, `image`, etc.)
- `modules/hud/draggable/*.js`: Scripted HUD widgets (`armor.js`, `inventory_panel.js`, `triangulator.js`, `itemizer.js`)
- `modules/hud/static/*.js`: Static overlays (`dynamic_island.js`, `custom_health_bar.js`, `tooltip_panel.js`)
- `modules/clickgui/*.js`: Scripted ClickGUI panels and theme previews

---

## 6. Decision Tree: Where Do I Look?

| To Change or Investigate... | Look In... |
| --- | --- |
| A specific hack/feature (e.g. Speed, AutoCrystal) | `src/main/java/combatant/client/features/module/modules/<category>/<Name>.java` |
| An in-game client command (e.g. `@config`, `@bind`) | `src/main/java/combatant/client/features/command/impl/<Name>Command.java` |
| Setting storage, serialization, `.cbcfg` profile logic | `src/main/java/combatant/client/config/` (`ConfigSerializer.java`, `MainConfig.java`) |
| ClickGUI layout, tabs, windows, or animations | `src/main/java/combatant/client/features/gui/clickgui/` |
| Draggable HUD elements, HUD positioning, snap grid | `src/main/java/combatant/client/features/gui/hud/draggable/` |
| Scripted HUD widget appearance or layout | `src/main/resources/assets/combatant/ui/modules/hud/draggable/` and `ui.d.ts` |
| Vanilla game rendering hooks or camera manipulation | `src/main/java/combatant/client/mixins/` (`GameRendererMixin`, `CameraMixin`) |
| Low-level shaders, deferred lighting, compute passes | `src/main/java/combatant/client/render/engine/` and `src/main/resources/assets/combatant/shaders/` |
| Font rendering or custom MSDF glyphs | `src/main/java/combatant/client/render/engine/text/` and `tools/msdf/` |
| Third-party mod compat (Sodium, Iris, Xaero) | `src/main/java/combatant/client/compat/` and `render/sodium/` |
| Addon loading or Addon API v0 | `src/main/java/combatant/client/addon/` and `api/v0/` |
| Player physics, rotations, raycasting, movement simulation | `src/main/java/combatant/client/util/` (`RotationManager`, `PlayerSimulationCache`) |
