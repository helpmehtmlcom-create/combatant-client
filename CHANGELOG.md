# Changelog

All notable changes to **Combatant Client** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased / Latest Krypton Updates] - 2026-10-09

### Anticheat Compatibility & Presets (GrimAC 2.3.74 & Vanilla Baseline)
- **Centralized `AntiCheatPreset` System**:
  - Added `anticheat_mode` enum setting (`CUSTOM`, `GRIM`, `VANILLA`) across combat, movement, and player modules.
  - Defined canonical server interaction constants: `GRIM_ENTITY_RANGE` (3.0), `GRIM_BLOCK_RANGE` (4.5), alongside vanilla limits (`6.0` entity, `5.5` block).
- **Combat Preset Bypasses**:
  - **AutoAnchor & AutoBed**: Clamped block placement and detonation reach to 4.5 blocks; restricted interaction rate to at most one interaction per client tick to prevent interaction spam flags.
  - **AutoCrystal**: Enforced strict Grim distance bounds: 4.5 block placement range, 3.0 block wall range, 3.0 block break range, and 3.0 block break wall range.
  - **Criticals**: Under Grim preset, automatically converts packet tricks (`PACKET`, `NO_GROUND`, `VULCAN_297`) to legitimate `JUMP` criticals at vanilla motion (`0.42`), preventing `GroundSpoof`, `Simulation`, and `Timer` flags while preserving damage output. Added unit tests for Grim mode selection.
  - **KillAura & Reach**: Enforced strict `GRIM_ENTITY_RANGE` (3.0 blocks) attack clamping under Grim.
  - **Block Protection / Combat Traps**: `AutoTrap`, `AutoWeb`, `HoleFill`, `SelfTrap`, and `Surround` clamp block placement ranges to 4.5 blocks and wall ranges to 3.0 blocks, with crystal-clearing checks restricted to Grim bounds.
  - **Totem Pop Counter**: Deduplicated totem pop triggers to count once per totem pop instead of once per processing thread.
- **Movement Preset Bypasses**:
  - **Speed**: Added new legitimate `BHOP` (Bunny Hop) mode that drives acceleration through input packets (`jump` and `sprint` bits) rather than directly injecting velocities, passing Grim simulation checks cleanly. Covered by `BhopInputTest`.
  - **Sprint**: Added Grim-compliant sprint blockage checks to prevent omnidirectional sprinting flags when blinded, sneaking, exhausted/hungry, or colliding horizontally.
  - **NoSlow**: Wired into vanilla item-use slowdown; automatically swaps in `GRIM_TICK` under the Grim preset.
  - **Timer**: Caps timer speed to 1.0x under Grim, ceases ping/pong packet holding, and disables `Shift` tick accumulation to pass transaction order checks.
  - **AirJump & ReverseStep**: Safely disabled under Grim to prevent vertical velocity and falling simulation flags.
  - **Step**: Enforces legitimate step height limits (clamped within 0.6–5.0) and mode switching.
  - **NoFall**: Automatically maps ground-spoofing modes to `PACKET_JUMP` under Grim, maintaining `MLG` mode as a real-placement alternative.
  - **Strafe & TargetStrafe**: Automatically switches from Matrix velocity modification to Grim mode.
  - **SafeWalk**: Fixed edge-walking creep where holding a direction key slowly shifted the player off the block edge.
  - **Blink**: Movement-only mode now holds movement packets reliably.

### Scaffold & Placement Engine Overhaul
- **Raycast Geometry**: Overhauled placement targeting so clicks satisfy GrimAC raycast geometry (aims at external block face positions from outside the block collision box).
- **Rotation Smoothing & Yaw Clamping**:
  - Constrained sent yaw angles to prevent 180° flick flags (`GrimAC AimModulo360`).
  - Aligned silent movement correction yaw quantization so diagonal movement remains natural without desyncing look angles.
  - Fast-snapped rotation in one step when smoothing latency would arrive after the placement window.
- **Tick-Based Placement Cadence**: Swapped placement timing from wall-clock nanoseconds to integer client ticks (defaulting to 20 blocks per second = 1 block per tick).
- **Trajectory & Jump Prediction**:
  - Added single-tick jump impulse prediction (`0.42` impulse + sprint boost) for accurate placement while jumping or telly-bridging.
  - Follows the player's real path on diagonal bridging rather than snapping to an ideal line when movement stabilization is disabled.
  - Fall-off prediction fixed to correctly return the supporting block face.
- **Input Handling**: Reads player movement direction directly from raw keyboard input rather than corrected movement inputs to prevent double-correction drift.

### Player & Automation Modules
- **Pre-Tick Placement Dispatch**:
  - `BlockPlacer`: Relocated block placement dispatch to before the movement packet (`start-of-tick` hook).
  - `InventorySwap`: Hotbar lease restoration now sends at the start of the tick, eliminating `Post` interaction flags.
  - `SpeedMine`: Relocated to pre-tick hook, paced block breaks, and ensured required hand swing packets are sent before break completion.
  - `AirPlace`, `AutoMount`, and `AutoBreed`: Moved to start-of-tick hooks.
- **AutoBreed**: Now feeds all nearby breedable animals in one pass instead of stopping at the first.
- **AutoMine**: New module for automated block mining.
- **ChestStealer**: Fixed container inventory synchronization.
- **NoDelay**: Enforced minimum delays on block breaking, placing, right-clicking, and jumping under Grim.
- **AntiAFK, AutoWalk, AntiVoid, HoleSnap, AutoClicker**: Added utility modules for automation, safety, and combat assistance.
- **Surround & SelfTrap**: Refactored around a shared placement planner with options for `KTLeave` and `ProjectilePuncher`.

### Visuals & Finders
- **DonutSMP Finders**:
  - Ported `NetheriteFinder`, `ActivityFinder`, `ChunkFinder`, `SpawnerFinder`, `SusChunkFinder`, `OGChunkFinder`, `LightFinder`, and `ExplosionMarker`.
  - Added server seed mismatch detection in `NetheriteFinder` with neighbor chunk synchronization.
- **Camera Adjustments**: Added mouse-wheel adjustment to `Zoom` and `Freecam`.
- **ESP & Cosmetics**:
  - Improved `DropESP`, `BlockESP`, `Predictions`, and `ViewModel`.
  - Fixed `FakePlayer` absorption health setting having no effect.

### Rendering Engine & Performance
- **Sodium Compatibility**: Added support for Sodium 0.9.2 while retaining extended terrain stride rendering.
- **Iris Compatibility**: Prevented crash in `GlCommandEncoderMixin` when Iris is absent.
- **2D/3D Render Engine**:
  - Split JIT compilation size of `UiShapeRenderer` to improve startup compilation performance.
  - Added memory caches for script color parsing (`UiScriptColor`) and mesh arena mappings (`Blaze3dMeshArena`).
  - Eliminated per-draw allocations in visual baseline profiles.
  - Optimized health bar script reruns and dynamic island updates.

### Diagnostics & Developer Tooling
- **Command `@i18naudit`**: Developer command to dump all configuration setting key chains and identify missing English/Russian localization strings.
- **Command `@rotdbg`**: Developer command for real-time packet and rotation logging to `latest.log` for anticheat tuning.
- **Build Cleanliness**: Excluded `.omc` agent state, test server files, and stray root metadata files from release jar packaging via `.gitignore`.
