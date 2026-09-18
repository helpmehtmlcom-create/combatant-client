# Errors

Command failures, API errors, and unexpected behavior captured during development.

**Areas**: frontend | backend | infra | tests | docs | config
**Statuses**: pending | in_progress | resolved | wont_fix | promoted | promoted_to_skill

## Status Definitions

| Status | Meaning |
|--------|---------|
| `pending` | Not yet addressed |
| `in_progress` | Actively being worked on |
| `resolved` | Issue fixed (add Resolution block) |
| `wont_fix` | Decided not to address (reason in Resolution) |
| `promoted` | Elevated to CLAUDE.md, AGENTS.md, or copilot-instructions.md |
| `promoted_to_skill` | Extracted as a reusable skill |

Entry format: see the self-improvement skill's "Error Entry" section. IDs use `ERR-YYYYMMDD-XXX`.

---
## [ERR-20260916-001] GlTextureBlitter mipLevel on GpuTexture in MC 26.2
**Logged**: 2026-09-16T10:30:00Z
**Priority**: critical
**Status**: promoted
**Promoted**: CLAUDE.md
**Area**: backend
### Summary
`cannot find symbol: method getWidth()` on `GpuTexture` during compileJava.
### Error
```
GlTextureBlitter.java:49: error: cannot find symbol int srcW = Math.max(1, source.getWidth(srcMip));
  symbol: method getWidth()
  location: variable source of type GpuTexture
```
### Context
- Minecraft version: 26.2
- Loom mappings: Mojang official
- Mojang refactored `GpuTexture.getWidth()` to require a mip level argument: `getWidth(int mipLevel)`.
### Suggested Fix
Pass `srcMip` / `dstMip` or `0` to `source.getWidth(srcMip)` and `destination.getWidth(dstMip)`.
### Metadata
- Reproducible: yes
- Related Files: src/main/java/combatant/client/render/engine/rhi/blit/GlTextureBlitter.java
### Resolution
- **Resolved**: 2026-09-16T11:00:00Z
- **Notes**: Updated `GlTextureBlitter.java` to invoke `source.getWidth(srcMip)` and `source.getHeight(srcMip)`.

---

## [ERR-20260916-002] ClientCommonPacketListenerImplMixin infinite re-entrancy
**Logged**: 2026-09-16T10:45:00Z
**Priority**: critical
**Status**: promoted
**Promoted**: CLAUDE.md
**Area**: backend
### Summary
Recursive packet re-entry leading to StackOverflowError when Flight module replaces ServerboundMovePlayerPacket.
### Error
```
connection.send(replacement) inside mixin was re-intercepted by the same @Inject method.
```
### Context
- Intercepting `send(Packet<?>)` in `ClientCommonPacketListenerImplMixin`.
- When `flight.onSendMovePacket(move)` returned a replacement packet, calling `connection.send(replacement)` re-invoked the mixin handler recursively.
### Suggested Fix
Add a `@Unique private boolean combatant$replacingMovePacket` recursion guard with a `try-finally` block.
### Metadata
- Reproducible: yes
- Related Files: src/main/java/combatant/client/mixins/ClientCommonPacketListenerImplMixin.java
### Resolution
- **Resolved**: 2026-09-16T11:15:00Z
- **Notes**: Added thread-local/instance boolean guard preventing recursive interception.

---

## [ERR-20260916-003] ClientInput keyPresses accessor in MC 26.2
**Logged**: 2026-09-16T11:00:00Z
**Priority**: high
**Status**: promoted
**Promoted**: CLAUDE.md
**Area**: backend
### Summary
Compilation error calling `player.input.forward()` directly on `ClientInput`.
### Error
```
InventoryManager.java:150: error: cannot find symbol
if (pauseOnMove && (player.input.forward() || player.input.backward() ...))
  symbol: method forward()
  location: variable input of type ClientInput
```
### Context
In Minecraft 26.2, `ClientInput` holds `public Input keyPresses;` where `Input` is a record with methods `forward()`, `backward()`, `left()`, `right()`.
### Suggested Fix
Access `player.input.keyPresses.forward()`, etc.
### Metadata
- Reproducible: yes
- Related Files: src/main/java/combatant/client/util/player/inventory/manager/InventoryManager.java
### Resolution
- **Resolved**: 2026-09-16T11:30:00Z
- **Notes**: Added null-checks and updated access to `player.input.keyPresses.forward()`.

---

## [ERR-20260916-004] CommandOutput missing info method
**Logged**: 2026-09-16T13:15:00Z
**Priority**: medium
**Status**: resolved
**Area**: backend
### Summary
Compilation error when calling `CommandOutput.info(...)` instead of `send(...)`.
### Error
```
error: cannot find symbol
CommandOutput.info(...)
  symbol: method info(String)
  location: class CommandOutput
```
### Context
`CommandOutput` had `success(msg)`, `warning(msg)`, `error(msg)`, but only `send(msg)` for informational tone.
### Suggested Fix
Added `public static void info(String message) { send(message, Tone.INFO); }` to `CommandOutput`.
### Metadata
- Reproducible: yes
- Related Files: src/main/java/combatant/client/features/command/CommandOutput.java
### Resolution
- **Resolved**: 2026-09-16T13:20:00Z
- **Notes**: Added `info` overload calling `send(message, Tone.INFO)`.
