---
name: combatant-ui-scripting
description: >-
  Script and customize Combatant Client HUD widgets, draggable elements, and ClickGUI panels
  using Javet V8 (JavaScript/TypeScript) and ui.d.ts. Use when creating or styling HUD elements,
  modifying UI layouts, writing custom JavaScript widgets, or designing ClickGUI themes.
---

# Combatant Client UI & HUD Scripting Guide

This skill guides developers through creating and styling HUD widgets and ClickGUI components using Combatant's high-performance **Javet V8 JavaScript/TypeScript runtime**.

---

## 1. Runtime Architecture & Script Integration

Combatant executes UI scripts inside an embedded V8 engine via Javet (`com.caoccao.javet:javet-v8-*`).

### Data Flow
```text
Game State (Java)
      │
      ▼  Pushes reactive state map/props
Script Engine (JavetUiScriptEngine.java)
      │
      ▼  Evaluates layout & styles in V8
JavaScript Script (assets/combatant/ui/...)
      │
      ▼  Returns UiNode tree specification
UI Batch Compiler (UiPassCompiler.java)
      │
      ▼  Draws via liquid glass & MSDF shaders
Screen Output
```

### Linking Java Classes to Scripts
Connect a Java HUD element to a JavaScript file using `@UiScriptAsset`:
```java
package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptAsset;

@UiScriptAsset("combatant:modules/hud/draggable/custom_stat")
public final class CustomStatElement extends DraggableHudElement {
    public CustomStatElement() {
        super("custom_stat", "Custom Stat", 100, 24);
    }
}
```
The asset path `"combatant:modules/hud/draggable/custom_stat"` automatically resolves to:
`src/main/resources/assets/combatant/ui/modules/hud/draggable/custom_stat.js`.

---

## 2. Layout Tree & Node Specification (`ui.d.ts`)

Every script produces a node tree conformant with `src/main/resources/assets/combatant/ui/api/ui.d.ts`.

### Node Kinds (`UiNodeType`)
- Containers: `root`, `panel`, `row`, `column`, `stack`, `scroll`, `spacer`, `divider`
- Visuals: `text`, `image`, `svg`, `shape`, `canvas`
- Minecraft Controls: `item` (draws vanilla ItemStack with count and durability overlay)
- Interactive: `button`, `input_text`, `checkbox`, `slider`

### Layout Properties (`UiLayoutProps`)
Placed directly on node descriptor objects:
```javascript
{
  type: "panel",
  width: 140,              // Explicit width (px)
  height: 32,              // Explicit height (px)
  minWidth: 80,
  maxWidth: 200,
  grow: 1,                 // Flexbox grow factor in row/column
  absolute: false,         // Take out of flow and use absolute coordinates
  x: 0,                    // Absolute x offset
  y: 0,                    // Absolute y offset
  align: "center",         // Cross-axis: "start" | "center" | "end" | "stretch"
  justify: "between",      // Main-axis: "start" | "center" | "end" | "between"
  overflow: "hidden"       // "visible" | "hidden" | "scroll-y"
}
```

### Built-In Global Helper Functions (`ui`)
The runtime exposes the `ui` object globally:
- `ui.num(val, fallback)`: Safely parses a number with a default fallback.
- `ui.str(val, fallback)`: Safely converts to a string.
- `ui.fmt(template, ...args)`: Formats strings with interpolation.
- `ui.cls(...classes)`: Joins CSS-like style class tokens.
- `ui.prop(obj, key, fallback)`: Safe nested property access.
- `ui.arr(val)`: Ensures an object is wrapped in an array.

---

## 3. Color & Math Utilities

Scripted widgets should use the standard ARGB color utilities (seen in `armor.js`):

```javascript
// Parse hex into color channels
function parseArgb(value, fallback = "#FFFFFFFF") {
  const src = ui.str(value, fallback);
  const normalized = src.length === 7 ? `#FF${src.slice(1)}` : src;
  const raw = Number.parseInt(normalized.slice(1), 16) >>> 0;
  return {
    a: (raw >>> 24) & 255,
    r: (raw >>> 16) & 255,
    g: (raw >>> 8) & 255,
    b: raw & 255,
  };
}

// Convert channels back to hex
function argb(color) {
  const a = Math.max(0, Math.min(255, Math.round(color.a)));
  const r = Math.max(0, Math.min(255, Math.round(color.r)));
  const g = Math.max(0, Math.min(255, Math.round(color.g)));
  const b = Math.max(0, Math.min(255, Math.round(color.b)));
  const raw = (((a << 24) | (r << 16) | (g << 8) | b) >>> 0);
  return `#${raw.toString(16).padStart(8, "0").toUpperCase()}`;
}

// Linear color interpolation
function mix(first, second, amount) {
  const t = Math.max(0, Math.min(1, ui.num(amount, 0)));
  const a = parseArgb(first);
  const b = parseArgb(second);
  return argb({
    a: a.a * (1 - t) + b.a * t,
    r: a.r * (1 - t) + b.r * t,
    g: a.g * (1 - t) + b.g * t,
    b: a.b * (1 - t) + b.b * t,
  });
}

// Adjust opacity
function alpha(colorHex, factor) {
  const c = parseArgb(colorHex);
  c.a = 255 * Math.max(0, Math.min(1, ui.num(factor, 1)));
  return argb(c);
}
```

---

## 4. Step-by-Step: Writing a Complete Scripted HUD Widget

Here is a complete, working example of a custom draggable HUD widget showing player speed and latency.

### Step 1: Java Bridge Class
`src/main/java/combatant/client/features/gui/hud/draggable/impl/SpeedStatsHud.java`:
```java
package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptAsset;
import net.minecraft.client.Minecraft;
import java.util.Map;

@UiScriptAsset("combatant:modules/hud/draggable/speed_stats")
public final class SpeedStatsHud extends DraggableHudElement {

    public SpeedStatsHud() {
        super("speed_stats", "Speed Stats", 120, 28);
    }

    @Override
    public Map<String, Object> createScriptProps() {
        Minecraft mc = Minecraft.getInstance();
        double speedBps = 0.0;
        if (mc.player != null) {
            double dx = mc.player.getX() - mc.player.xo;
            double dz = mc.player.getZ() - mc.player.zo;
            speedBps = Math.hypot(dx, dz) * 20.0;
        }

        return Map.of(
            "speed", String.format("%.2f bps", speedBps),
            "accentColor", "#FF55FFFF"
        );
    }
}
```

### Step 2: JavaScript Layout Script
`src/main/resources/assets/combatant/ui/modules/hud/draggable/speed_stats.js`:
```javascript
/* Custom HUD Script: Speed Stats */

function render(props) {
  const speed = ui.prop(props, "speed", "0.00 bps");
  const accent = ui.prop(props, "accentColor", "#FF55FFFF");
  const bgGlass = alpha("#101018", 0.75);

  return {
    type: "panel",
    width: 120,
    height: 28,
    style: {
      background: bgGlass,
      borderRadius: 6,
      borderColor: alpha(accent, 0.4),
      borderWidth: 1,
      padding: [4, 8]
    },
    children: [
      {
        type: "row",
        align: "center",
        justify: "between",
        grow: 1,
        children: [
          {
            type: "text",
            text: "SPEED",
            font: "matrix_sans_bold",
            size: 9,
            color: alpha(accent, 0.9)
          },
          {
            type: "text",
            text: speed,
            font: "iosevka",
            size: 11,
            color: "#FFFFFFFF"
          }
        ]
      }
    ]
  };
}
```

---

## 5. Existing Script Directory Reference

Inspect existing scripts for reference patterns:
- `modules/hud/draggable/armor.js`: Armor durability, damage gradient color mixing, item count overlays.
- `modules/hud/draggable/inventory_panel.js`: Full player inventory grid layout.
- `modules/hud/draggable/triangulator.js`: Radar vector calculations and spatial markers.
- `modules/hud/static/dynamic_island.js`: Dynamic animated status pill atop the screen.
- `modules/clickgui/settings_panel.js`: Complex scrolling setting windows with interactive sliders and buttons.
