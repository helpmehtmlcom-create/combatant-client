# Combatant Client Built-In Event Catalog

This reference documents all core events dispatched by Combatant's `EventBus` (`combatant.client.events.impl.*`).

## 1. Tick & Game Loop Events

| Event Class | Phase / Trigger | Key Methods | Typical Usage |
| --- | --- | --- | --- |
| `GameTickEvent` | Dispatched once per client tick | `isPre()`, `isPost()` | Main update loop for combat, movement, automation |
| `RotationUpdateEvent` | Fired before motion packet is built | `getYaw()`, `getPitch()`, `setYaw()`, `setPitch()`, `cancel()` | Silent aim, server-side rotation locks |
| `EventSync` | Fired during player motion sync | `cancel()` | Pre-motion state sync |
| `EventPostSync` | Fired immediately after motion sync | — | Restoring pitch/yaw after silent aim packet |

## 2. Movement & Physics Events

| Event Class | Phase / Trigger | Key Methods | Typical Usage |
| --- | --- | --- | --- |
| `PlayerMoveEvent` | Fired during player move calculation | `getX()`, `getY()`, `getZ()`, `setX()`, `setY()`, `setZ()`, `cancel()` | Custom velocity, Speed, Strafe, Flight |
| `PlayerJumpEvent` | Fired when player initiates jump | `cancel()` | High jump, jump cancelation |
| `PlayerStepEvent` | Fired before stepping up a block | `getHeight()`, `setHeight()`, `cancel()` | Step module height modification |
| `PlayerStepSuccessEvent` | Fired after successful step | `getHeight()` | Step velocity adjustment |
| `MovementInputEvent` | Fired when reading WASD / Space / Shift | `getForward()`, `getStrafe()`, `isJumping()`, `isSneaking()` | InventoryMove, NoSlow, SafeWalk |
| `PlayerVelocityStrafe` | Fired during strafe acceleration | `setSpeed()`, `cancel()` | TargetStrafe, Strafe |
| `EventPushOutOfBlocks` | Fired when suffocating in block | `cancel()` | Freecam, Phase, Burrow |
| `EventCollision` | Fired per block bounding box collision | `getBlockPos()`, `getVoxelShape()`, `setVoxelShape()`, `cancel()` | Jesus, AirPlace, Phase |

## 3. Network & Packet Events

| Event Class | Phase / Trigger | Key Methods | Typical Usage |
| --- | --- | --- | --- |
| `PacketEvent.Send` | Outgoing client-to-server packet | `getPacket()`, `cancel()` | Blink, packet cancellation, fake lag, silent rotation |
| `PacketEvent.Receive` | Incoming server-to-client packet | `getPacket()`, `cancel()` | Velocity (knockback packet cancel), AntiHunger |
| `BlinkPacketEvent` | Packets queued during Blink | `getPacket()`, `cancel()` | Packet buffering and flush management |

## 4. Combat & Entity Events

| Event Class | Phase / Trigger | Key Methods | Typical Usage |
| --- | --- | --- | --- |
| `AttackEntityEvent` | Fired before client attacks target | `getEntity()`, `cancel()` | Criticals, Reach, Weapon swap |
| `EventTargetChanged` | Fired when target entity changes | `getNewTarget()`, `getOldTarget()` | TargetHud, TargetStrafe |
| `CrosshairTargetUpdateEvent` | Fired when raycasting target changes | `getTarget()` | Triggerbot, Hitbox |
| `CombatProtocolBossbarEvent` | Fired when server sends bossbar | `getTitle()`, `cancel()` | Anti-AntiCheat heuristics |

## 5. Render & UI Events

| Event Class | Phase / Trigger | Key Methods | Typical Usage |
| --- | --- | --- | --- |
| `RenderPrewarmCollectEvent` | Fired before frame render begins | `collect(...)` | Pre-warming MSDF fonts, textures, shaders |
| `LightmapModifyEvent` | Fired when lightmap texture is sampled | `getLight()`, `setLight()` | FullBright, NightVision |
| `PvpOverlayEvent` | Fired during in-game PVP HUD render | `getGraphics()` | Custom overlay drawing |
| `KeyInputEvent` | Fired when keyboard key is pressed | `getKey()`, `getAction()`, `getModifiers()` | Keybind listeners |
