---
name: combatant-module-dev
description: >-
  Develop, extend, and debug Combatant Client modules, settings, events, commands, and mixins.
  Use when creating a new module, declaring settings (BooleanValue, NumberValue, EnumValue, ColorValue),
  subscribing to EventBus events, adding @commands, writing Fabric mixins, or compiling with Gradle.
---

# Combatant Client Module & Feature Development Guide

This skill provides step-by-step instructions for engineering, extending, and testing features in **Combatant Client**.

---

## 1. Creating a New Client Module

All client modules extend `combatant.client.features.module.Module` and reside under `src/main/java/combatant/client/features/module/modules/<category>/`.

### Module Skeleton

```java
package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.EnumValue;
import combatant.client.events.Events;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;

@ModuleInfo(
    id = "auto_totem",
    displayName = "Auto Totem",
    category = ModuleCategory.COMBAT,
    description = "Automatically equips a Totem of Undying to your offhand.",
    enabledByDefault = false,
    aliases = {"totem", "offhandtotem"}
)
public final class AutoTotem extends Module {

    private final BooleanValue checkHealth = new BooleanValue("check_health", true);
    private final NumberValue healthThreshold = new NumberValue("health_threshold", 10.0, 1.0, 20.0, 0.5);
    private final EnumValue<Mode> mode = new EnumValue<>("mode", Mode.SMART, Mode.class);

    public AutoTotem() {
        // Register settings to the module's setting list
        addSetting(checkHealth);
        addSetting(healthThreshold);
        addSetting(mode);

        // Optional: dynamic visibility constraint
        healthThreshold.visibleIf(checkHealth::get);
    }

    @Override
    protected void onEnable() {
        // Register to event bus
        Events.bus().register(this);
    }

    @Override
    protected void onDisable() {
        // Unregister from event bus
        Events.bus().unregister(this);
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (event.isPost() || Minecraft.getInstance().player == null) {
            return;
        }

        // Logic here
    }

    public enum Mode {
        FAST, SMART, STRICT
    }
}
```

---

## 2. Setting Definitions (`SettingDef`)

Settings are strongly-typed, reactive, and serialized automatically to `.cbcfg` profiles:

### A. Boolean Setting (`BooleanValue`)
```java
private final BooleanValue silent = new BooleanValue("silent", true);
```

### B. Numerical Setting (`NumberValue`)
Supports integer, float, or double ranges with min, max, and step increments:
```java
// Name, default, min, max, step
private final NumberValue delay = new NumberValue("delay_ms", 50, 0, 1000, 10);
private final NumberValue range = new NumberValue("range", 4.5, 1.0, 6.0, 0.1);
```

### C. Enum / Dropdown Setting (`EnumValue`)
```java
public enum TargetMode { SINGLE, MULTI, SWITCH }
private final EnumValue<TargetMode> targetMode = new EnumValue<>("target_mode", TargetMode.SINGLE, TargetMode.class);
```

### D. Color Settings
```java
// Color with alpha
private final ColorSetting boxColor = new ColorSetting("box_color", 0x80FF0000);

// Color without alpha
private final ConfigColorSettingNoAlpha accentColor = new ConfigColorSettingNoAlpha("accent", 0xFF55FF);
```

### E. Dynamic Setting Visibility & Dependencies
Hide or show settings based on the state of other settings:
```java
// Only show slider if the boolean toggle is enabled
customDelay.visibleIf(useCustomDelay::get);

// Only show sub-settings when a specific enum mode is selected
speedMultiplier.visibleIf(() -> mode.get() == Mode.CUSTOM);
```

---

## 3. The Event System (`EventBus`)

Combatant uses a zero-allocation, high-throughput `EventBus` (`combatant.client.events.EventBus`).

### Event Subscription & Priority
```java
@EventHandler(priority = 100) // Higher priority runs earlier
public void onMove(PlayerMoveEvent event) {
    if (event.isCancelled()) return;
    // Intercept or modify movement
}
```

### Key Built-In Events (`events/impl/`)
- `GameTickEvent`: Fired every client tick. Check `event.isPre()` vs `event.isPost()`.
- `PlayerMoveEvent`: Fired before player physics simulation. Can alter motion vectors or cancel.
- `PlayerJumpEvent`, `PlayerStepEvent`: Locomotion hooks.
- `PacketEvent.Send` & `PacketEvent.Receive`: Intercept vanilla packets before dispatch or receipt.
- `RotationUpdateEvent`: Silent aiming hook. Modify yaw/pitch without affecting player camera.
- `AttackEntityEvent`: Triggered when player attacks an entity.
- `RenderPrewarmCollectEvent`: Collects textures or meshes before frame rendering starts.
- `LightmapModifyEvent`: Alter world brightness or fullbright values.

---

## 4. Creating a Client Command

Commands extend `combatant.client.features.command.ClientCommand` and reside in `features/command/impl/`.

```java
package combatant.client.features.command.impl;

import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandInfo;
import java.util.List;

@CommandInfo(
    id = "customcmd",
    aliases = {"cc", "cmd"},
    usage = "@customcmd <get|set> [value]",
    description = "Inspects or updates custom client parameters."
)
public final class CustomCmdCommand implements ClientCommand {

    @Override
    public boolean execute(CommandContext ctx) {
        if (ctx.argCount() == 0) {
            ctx.output().info("Usage: " + metadata().usage());
            return true;
        }

        String action = ctx.arg(0).toLowerCase();
        switch (action) {
            case "get" -> ctx.output().info("Current state: Active");
            case "set" -> {
                if (ctx.argCount() < 2) {
                    ctx.output().error("Missing parameter for 'set'");
                    return true;
                }
                String value = ctx.arg(1);
                ctx.output().success("Updated parameter to: " + value);
            }
            default -> ctx.output().warn("Unknown sub-command: " + action);
        }
        return true;
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        if (argIndex == 0) {
            return List.of("get", "set");
        }
        return List.of();
    }
}
```

---

## 5. Mixin Conventions & Rules

Mixin classes live in `src/main/java/combatant/client/mixins/` and are configured via `combatant.mixins.json`.

### Rules & Best Practices
1. **Never use `@Overwrite`:** Overwrites break compatibility with other mods (Sodium, Iris, Fabric API). Always use `@Inject`, `@ModifyVariable`, or `@WrapOperation`.
2. **Duck Typing via Interfaces:** To attach state or custom methods to a vanilla Minecraft class, declare an interface in `combatant.client.mixininterface.IFoo`, implement it on the target class via `@Mixin`, and cast instances to `IFoo`.
3. **Use Accessors and Invokers:** For private fields or methods, use `@Accessor` or `@Invoker` interfaces in `mixins/accessors/` rather than reflection.
4. **Mark Reflective Points:** Use `@UsedImplicitly` from `combatant.client.events.UsedImplicitly` on methods invoked via reflection or mixin interfaces to prevent IntelliJ warnings.
5. **Thread Safety:** Mixins hooking network or rendering pipelines run on separate threads. Never mutate player/world state directly from a netty thread without scheduling to client main thread (`Minecraft.getInstance().execute(...)`).

### Example Injection Pattern
```java
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extract()V"),
        cancellable = true
    )
    private void onRenderExtract(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        // Combatant 2D overlay pass hook
    }
}
```

---

## 6. Build & Test Commands

```powershell
# Compile Java sources and run annotation processor
.\gradlew.bat compileJava

# Run automated unit tests
.\gradlew.bat test

# Run build without tests (fast check)
.\gradlew.bat assemble

# Build development distribution with debug symbols
.\gradlew.bat buildDev
```
