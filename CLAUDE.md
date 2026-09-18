# Combatant Client (Minecraft 26.2) — Project Knowledge & Conventions

## Architecture & Conventions

### Minecraft 26.2 Mapping Gotchas
- **Player Hotbar Slot**: Access via `player.getInventory().getSelectedSlot()` instead of direct field access.
- **Player Movement Input**: `player.input` is a `ClientInput` object where keys are accessed via `player.input.keyPresses.forward()`, `player.input.keyPresses.backward()`, etc. (`keyPresses` is an `Input` record).

### Network & Event Thread Safety
- **Netty Packet Thread**: `PacketEvent.Receive` fires on Netty worker threads. Handlers modifying state or collections must either:
  - Use concurrent collections (e.g. `ConcurrentHashMap.newKeySet()`).
  - Schedule work on the client game loop via `mc.execute(() -> { ... })`.
- **Packet Send Re-entrancy**: When intercepting packet sends in mixins (e.g. `ClientCommonPacketListenerImplMixin`) and sending replacement packets, always wrap in a `@Unique private boolean ...` recursion guard with a `try-finally` block to prevent infinite `StackOverflowError`.

### Rotation & Aiming Math
- **NaN / Infinity Guards**: Never pass raw `Math.atan2` or `Math.hypot` results to player rotation packets without checking `Double.isNaN` or near-zero distance (`< 1.0E-7`). Always use `RotationUtil.calculateRotations` and clamp angles to avoid server kicks for invalid movement packets.

### RHI & Rendering Engine
- **GpuTexture Dimensions**: `GpuTexture` requires an explicit mip level: `texture.getWidth(mipLevel)` (use `0` for base level).
- **Fullscreen Draw Passes**:
  - Attachment dimensions must be clamped as positive integers: `Math.max(1, attachment.getWidth(0))`.
  - Fullscreen quads operate in clip space `[-1, +1]`: bind identity projection and model-view matrices (`MeshUniforms.update(...)`) so presentation passes are not scaled or offset by active world transforms.

### Modules & UI
- **Module Declarations**: Every `@ModuleInfo` annotation must provide a descriptive `description` parameter explaining its behavior and anti-cheat compatibility.
- **Complex Settings**: Attach `.description(...)` to multi-mode or complex numeric settings so `SettingsPanelComponent` can render user guidance in `ClickGuiHintOverlay`.
