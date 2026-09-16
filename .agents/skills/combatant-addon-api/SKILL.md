---
name: combatant-addon-api
description: >-
  Create and maintain external Fabric addons for Combatant Client using the versioned Addon API v0.
  Use when building standalone addon mods, extending built-in modules with custom settings,
  adding custom ClickGUI sections/tabs, or registering custom render passes.
---

# Combatant Client Addon API Guide (API v0)

This skill guides developers through creating standalone Fabric addon mods that integrate seamlessly with **Combatant Client** using the versioned `combatant.client.api.v0.*` contracts.

---

## 1. Addon Project Architecture

Combatant provides a public template repository for bootstrapping addons:
- **Template:** `https://github.com/pivosos2007/combatant-addon-template`
- **Target Specifications:**
  - Minecraft 26.2
  - Fabric Loader 0.19.3
  - Java 25 JDK
  - Combatant Addon API Version: `0`

### `fabric.mod.json` Configuration
Register your addon class under the `combatant:addon` entrypoint:

```json
{
  "schemaVersion": 1,
  "id": "my_combatant_addon",
  "version": "1.0.0",
  "name": "My Combatant Addon",
  "environment": "client",
  "entrypoints": {
    "combatant:addon": [
      "com.example.addon.MyAddon"
    ]
  },
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "26.2",
    "combatant": "*"
  }
}
```

---

## 2. Implementing the Addon Entrypoint (`CombatantAddon`)

Your entrypoint implements `combatant.client.api.v0.addon.CombatantAddon`:

```java
package com.example.addon;

import combatant.client.api.v0.addon.CombatantAddon;
import combatant.client.api.v0.addon.CombatantAddonContext;
import combatant.client.api.v0.addon.CombatantAddonRuntimeContext;
import combatant.client.api.v0.addon.CombatantModuleLoadContext;

public final class MyAddon implements CombatantAddon {

    @Override
    public void onConfigureModules(CombatantModuleLoadContext context) {
        // Optional hook: called before Combatant auto-loads built-in modules.
        // Use only if you need to exclude or replace a built-in module.
    }

    @Override
    public void onInitialize(CombatantAddonContext context) {
        // Primary registration hook.
        // Register custom modules, commands, HUD elements, or ClickGUI tabs.
        context.registerModule(new CustomAddonModule());
        context.registerCommand(new CustomAddonCommand());
    }

    @Override
    public void onClientReady(CombatantAddonRuntimeContext context) {
        // Called after all configs are loaded. Safe hook to mutate existing module settings.
    }

    @Override
    public void onShutdown(CombatantAddonRuntimeContext context) {
        // Cleanup resources, file handles, or network sockets.
    }
}
```

---

## 3. Extending Built-In Modules (`ModuleExtensionContext`)

Addons can inject custom settings, flags, or callbacks into existing built-in client modules without modifying or overriding them:

```java
@Override
public void onInitialize(CombatantAddonContext context) {
    context.extendModule("speed", (ModuleExtensionContext ext) -> {
        // Add a custom bypass checkbox to Combatant's built-in Speed module
        BooleanValue bypassToggle = new BooleanValue("special_bypass", false);
        ext.addSetting(bypassToggle);

        // Add custom tick listener tied to the Speed module's lifecycle
        ext.onTick(() -> {
            if (bypassToggle.get()) {
                // Execute specialized logic while Speed is active
            }
        });
    });
}
```

---

## 4. Registering Custom ClickGUI Sections (`CombatantClickGuiSection`)

Addons can add entirely new tabs/sections to the main ClickGUI (alongside Modules, Settings, Configs, Themes):

```java
package com.example.addon.gui;

import combatant.client.api.v0.clickgui.CombatantClickGuiSection;
import combatant.client.api.v0.clickgui.CombatantClickGuiRenderContext;

public final class MyCustomSection implements CombatantClickGuiSection {

    @Override
    public String id() {
        return "my_addon_tools";
    }

    @Override
    public String displayName() {
        return "Addon Tools";
    }

    @Override
    public void render(CombatantClickGuiRenderContext ctx) {
        // ctx provides mouse coordinates, delta time, and Renderer2D access
        ctx.renderer().drawText("Custom Addon Dashboard", ctx.bounds().x() + 10, ctx.bounds().y() + 10, 0xFFFFFFFF);
    }
}
```

Register it in `onInitialize`:
```java
context.registerClickGuiSection(new MyCustomSection());
```

---

## 5. Hooking Custom Render Passes (`CombatantRenderStage`)

Inject custom rendering code into specific stages of Combatant's rendering engine:

```java
import combatant.client.api.v0.render.CombatantRenderStage;
import combatant.client.api.v0.render.CombatantRenderCallback;

@Override
public void onInitialize(CombatantAddonContext context) {
    context.registerRenderCallback(
        CombatantRenderStage.AFTER_ENTITIES,
        (CombatantRenderContext renderCtx) -> {
            // Draw custom 3D indicators or bounding boxes
            renderCtx.renderer3D().drawBox(
                targetPos,
                1.0, 1.0, 1.0,
                0x80FF00FF, // Color RGBA
                true        // Depth test
            );
        }
    );
}
```

### Available Render Stages (`CombatantRenderStage`)
- `BEFORE_WORLD`: Pre-render setup before chunk geometry draws.
- `AFTER_ENTITIES`: After entity renderers, before hand/particles. Ideal for 3D ESP and markers.
- `AFTER_WORLD`: World rendering complete, before GUI passes.
- `HUD`: 2D HUD rendering stage.
- `POST_PROCESS`: Direct access to the post-processing pipeline for full-screen shaders.
