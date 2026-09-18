/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeSpec;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UiScriptObjectConverter {
    private static final List<String> RESERVED_NODE_KEYS = List.of(
            "type",
            "key",
            "class",
            "className",
            "props",
            "events",
            "meta",
            "children"
    );

    public UiNodeSpec convert(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("UI script render must return an object.");
        }
        return node(map);
    }

    private UiNodeSpec node(Map<?, ?> map) {
        validateFieldShape(map, "type", String.class);
        validateFieldShape(map, "key", String.class);
        validateFieldShape(map, "class", String.class);
        validateFieldShape(map, "className", String.class);
        validateMapField(map, "props");
        validateMapField(map, "events");
        validateMapField(map, "meta");
        validateChildrenField(map);

        Map<String, Object> props = mapObject(map.get("props"));
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) continue;
            String key = String.valueOf(entry.getKey());
            if (!RESERVED_NODE_KEYS.contains(key)) {
                props.put(key, entry.getValue());
            }
        }

        return new UiNodeSpec(
                string(map.get("key"), ""),
                nodeType(string(map.get("type"), "panel")),
                new UiProps(props),
                UiStyle.DEFAULT,
                string(map.get("class"), string(map.get("className"), "")),
                events(map.get("events")),
                mapObject(map.get("meta")),
                children(map.get("children"))
        );
    }

    private List<UiNodeSpec> children(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<UiNodeSpec> children = new ArrayList<>();
        int index = 0;
        for (Object child : iterable) {
            if (child instanceof Map<?, ?> childMap) {
                children.add(node(childMap));
            } else if (child != null && UiRuntimeValidation.enabled()) {
                throw UiRuntimeValidation.invalid(
                        "UI child at index " + index + " must be an object/map, got " + child.getClass().getSimpleName() + "."
                );
            }
            index++;
        }
        return children;
    }

    private static Map<String, String> events(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, String> events = new LinkedHashMap<>(raw.size());
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() == null) continue;
            if (entry.getValue() instanceof String action) {
                events.put(String.valueOf(entry.getKey()), action);
            } else if (entry.getValue() != null && UiRuntimeValidation.enabled()) {
                throw UiRuntimeValidation.invalid(
                        "UI event '" + entry.getKey() + "' must name a String action, got "
                                + entry.getValue().getClass().getSimpleName() + "."
                );
            }
        }
        return events;
    }

    private static Map<String, Object> mapObject(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) return new LinkedHashMap<>();
        Map<String, Object> map = new LinkedHashMap<>(raw.size());
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return map;
    }

    private static UiNodeType nodeType(String raw) {
        String normalized = raw == null ? "panel" : raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return UiNodeType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            if (UiRuntimeValidation.enabled()) {
                throw UiRuntimeValidation.invalid("Unknown UI node type '" + raw + "'.");
            }
            return UiNodeType.PANEL;
        }
    }

    private static void validateFieldShape(Map<?, ?> map, String field, Class<?> expectedType) {
        if (!UiRuntimeValidation.enabled() || !map.containsKey(field)) return;
        Object value = map.get(field);
        if (value == null || expectedType.isInstance(value)) return;
        throw UiRuntimeValidation.invalid(
                "UI node field '" + field + "' must be " + expectedType.getSimpleName() + ", got " + value.getClass().getSimpleName() + "."
        );
    }

    private static void validateMapField(Map<?, ?> map, String field) {
        if (!UiRuntimeValidation.enabled() || !map.containsKey(field)) return;
        Object value = map.get(field);
        if (value == null || value instanceof Map<?, ?>) return;
        throw UiRuntimeValidation.invalid(
                "UI node field '" + field + "' must be an object/map, got " + value.getClass().getSimpleName() + "."
        );
    }

    private static void validateChildrenField(Map<?, ?> map) {
        if (!UiRuntimeValidation.enabled() || !map.containsKey("children")) return;
        Object value = map.get("children");
        if (value == null || value instanceof Iterable<?>) return;
        throw UiRuntimeValidation.invalid(
                "UI node field 'children' must be iterable, got " + value.getClass().getSimpleName() + "."
        );
    }

    private static String string(Object value, String fallback) {
        return value instanceof String s ? s : fallback;
    }
}
