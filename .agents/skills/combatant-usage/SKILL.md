---
name: combatant-usage
description: >-
  Comprehensive guide to running, operating, configuring, and using Combatant Client. Use when
  launching the client in dev/runtime, using in-game @commands, navigating the ClickGUI, managing
  .cbcfg config profiles, configuring draggable HUD elements, setting keybinds, and managing relations.
---

# Combatant Client Usage & Operation Guide

This skill covers every action of using, configuring, running, and managing **Combatant Client** in both development and live gameplay.

---

## 1. Running & Launching the Client

Combatant requires **Java 25 JDK**, **Minecraft 26.2**, and **Fabric Loader 0.19.3**.

### Common Gradle Launch Commands

Execute using the repository's Gradle wrapper (`gradlew.bat` on Windows):

```powershell
# Standard local client launch
.\gradlew.bat runClient

# Launch with optional runtime mods (Sodium, Iris, ImmediatelyFast)
.\gradlew.bat runClient -PwithOptionalModRuntime=true

# Launch with ViaFabricPlus (for connecting to multi-version servers)
.\gradlew.bat runClient -PwithViaFabricPlusRuntime=true

# Launch with both optional mods and ViaFabricPlus
.\gradlew.bat runClient -PwithOptionalModRuntime=true -PwithViaFabricPlusRuntime=true

# Launch including the development source set (src/dev)
.\gradlew.bat runClient -PcombatantDev=true
```

### Building Distributable Jars

```powershell
# Build release distribution jar (written to build/libs/combatant-26.2-<version>.jar)
.\gradlew.bat clean build

# Build development jar (includes src/dev features and dev metadata)
.\gradlew.bat buildDev
```

*Note:* Sodium is the primary render target (`sodium-fabric-0.9.1+mc26.2`). Iris shaderpack features are currently supported only on the OpenGL backend.

---

## 2. In-Game ClickGUI Controls

Open the ClickGUI by pressing **Right Shift (`RSHIFT`)** (default keybind, remappable).

### Navigation & Layout
- **Category Tabs:** At the top/side of the ClickGUI:
  - `Modules`: Browse modules across `Combat`, `Movement`, `Player`, `Visuals`, and `Misc`.
  - `Settings`: Global client configuration (visuals, security, runtime, inventory).
  - `Configs`: Profile manager for `.cbcfg` files.
  - `HUD`: Layout editor for draggable HUD elements.
  - `Themes`: Visual theming (accent colors, glass blur, gradient modes).
  - `Relations`: Manage `Friend`, `Enemy`, and `Staff` lists.

### Interacting with Modules
- **Left-Click:** Toggle the module enabled or disabled.
- **Right-Click:** Open the module's detailed settings window (sliders, toggles, color pickers, modes).
- **Search Bar:** Press `/` or click the search box to search modules, settings, or descriptions in real-time.
- **Keybind Editing:** Click the bind button in a module card or setting window, then press the desired key (press `Escape` or `Backspace` to clear).

---

## 3. Client Commands Reference (`@...`)

Client commands begin with `@` and are processed locally (never sent to the multiplayer server). Argument autocomplete and syntax help appear dynamically in chat.

| Command | Aliases | Usage | Description | Requirement |
| --- | --- | --- | --- | --- |
| `@help` | `@commands`, `@cmds` | `@help [page\|command]` | Shows all commands with clickable pagination and help. | None |
| `@modules` | `@modulelist`, `@mods` | `@modules [all\|enabled\|<cat>] [page]` | Lists modules filtered by category or active state. | None |
| `@toggle` | `@t` | `@toggle <module> [on\|off\|toggle]` | Enables, disables, or toggles a module by ID. | None |
| `@bind` | `@binds`, `@keybind` | `@bind <module> <key\|combo\|none>`<br>`@bind list` | Changes or views module keybindings. | None |
| `@hide` | `@modulevisibility` | `@hide <module> [on\|off\|toggle]` | Hides/shows a module from the in-game HUD `ModuleList`. | None |
| `@config` | `@cfg`, `@settings` | `@config [save\|load\|path]` | Saves, reloads, or prints the active config directory. | None |
| `@friend` | `@friends`, `@f` | `@friend [list\|add\|remove\|toggle\|clear] [player]` | Manages the friend list (prevents targeting by combat modules). | None |
| `@enemy` | `@enemies`, `@e` | `@enemy [list\|add\|remove\|toggle\|clear] [player]` | Manages the enemy list (prioritizes targets in combat). | None |
| `@staff` | `@staffs`, `@admin` | `@staff [list\|add\|remove\|toggle\|clear] [player]` | Manages staff list for server admin alerts. | None |
| `@coordinates` | `@coords`, `@pos` | `@coordinates` | Displays exact player position and block coordinates in chat. | None |
| `@ping` | `@latency` | `@ping` | Shows current ping/round-trip latency to the server. | Multiplayer |
| `@tps` | `@servertps` | `@tps` | Shows estimated server ticks per second (20.0 max). | Multiplayer |
| `@serverinfo` | `@server`, `@sinfo` | `@serverinfo` | Displays current server address, protocol, and world time. | None |
| `@username` | `@name`, `@whoami` | `@username` | Prints the active Minecraft username and UUID. | None |
| `@xaero` | `@xmark`, `@waypoint` | `@xaero here [name]`<br>`@xaero add <x> <y> <z> [name]`<br>`@xaero list`<br>`@xaero remove <name>` | Creates, lists, or removes waypoints in Xaero's Minimap/WorldMap. | Xaero's Minimap |
| `@addons` | — | `@addons [list\|scan\|enable\|disable] [id]` | Manages runtime external addons. | None |
| `@runtime` | `@panic`, `@resume` | `@runtime [status\|panic\|resume]` | Controls panic mode (emergency disable of all combat/movement hacks). | None |
| `@iris` | `@shaderpack` | `@iris` | Shows Iris compatibility state, active shaderpack, and diagnostics. | Iris |

---

## 4. Config Profiles & `.cbcfg` Management

Combatant splits configuration profiles into three independent types stored under `.minecraft/config/combatant/profiles/`:

```text
.minecraft/config/combatant/profiles/
├── modules/    # Module states (enabled/disabled) and module settings
├── hud/        # Draggable HUD layout, screen positions, and HUD element configs
└── themes/     # ClickGUI themes, colors, and visual styles
```

### Working with Profiles in ClickGUI
1. Open ClickGUI (`RSHIFT`), select **Settings**, then open **Configs**.
2. Select the top tab: `Modules`, `Hud`, or `Themes`.
3. **Save New Profile:** Enter a profile name in the text field and click `Save`.
4. **Apply Profile:** Select any profile card and click the **Apply/Download** icon to load it instantly.
5. **Diff Preview:** Click the **Diff** button on a card to see exactly what values differ between that profile and your current setup before loading.
6. **Overwrite Existing Profile:** Open the diff panel for the selected profile and confirm the overwrite.
7. **Rename Profile:** Click the profile name text, type a new name, and press `Enter` (or `Escape` to cancel).
8. **Delete Profile:** Click the red **Trash** icon.

### Sharing and Importing Profiles
- **Drag-and-Drop:** You can drag any `.cbcfg` file from Windows Explorer directly into the game window when the `Settings -> Configs` screen is open. Combatant reads the header and routes it to the correct `modules/`, `hud/`, or `themes/` folder.
- **Manual Import:** Place any `.cbcfg` file into `config/combatant/profiles/` (or its appropriate subfolder). Combatant auto-sorts files placed in the parent directory on next scan.

---

## 5. HUD Layout Editor & Customization

Open the HUD Editor via **ClickGUI -> Settings -> HUD**.

### HUD Manipulation
- **Move Elements:** Click and drag any HUD element to reposition it on screen.
- **Precision Snapping:** Dragging elements near the edges of the screen or near other elements activates magnetic snap guides.
- **Anchor System:** Elements can be anchored to `Top-Left`, `Top-Right`, `Bottom-Left`, `Bottom-Right`, or `Center`. Anchored elements retain their relative screen positions when changing GUI scale or window resolution.
- **Element Settings:** Right-click an element or configure it in the HUD settings list to adjust scale, opacity, color accents, and display modes.

### Prominent HUD Elements
- `Coordinates`: Shows current X, Y, Z coordinates, nether equivalents, and biome.
- `Armor`: Scripted durability bar, damage counters, and item icons.
- `ModuleList`: Displays active modules with customizable rainbow/gradient/category coloring.
- `TargetHud`: Displays current combat target, health bar, armor durability, and status effects.
- `Itemizer`: Custom hotbar and held item counts (totems, crystals, golden apples).
- `Ping`, `Fps`, `Tps`: Real-time network and engine performance metrics.
- `DynamicIsland` & `CustomHotbar`: Modern styled replacements for vanilla UI bars.

---

## 6. Runtime Safety & Panic Mode

- **Emergency Panic:** Run `@runtime panic` or bind a key to the `Panic` module (`features/module/modules/misc/Panic.java`). This immediately disables all active combat and movement modules, suppresses visual overlays, and prevents bans during screen-shares or checks.
- **Resume:** Run `@runtime resume` to restore your previous operational module configuration.
- **Failure Boundaries:** If a specific module or rendering pass encounters an unhandled exception, Combatant's `FailureIsolation` catches it, disables that specific module to prevent game crash, and displays a localized failure toast with diagnostic details. Check with `@help` or the errors window.
