/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.server;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure bookkeeping for DonutSMP safe mode: which per-module anticheat presets to switch to
 * {@value #TARGET} on join, and which to put back on leave. A module the player changed
 * while safe mode was active is left alone on restore.
 */
public final class DonutSafeMode {

    public static final String TARGET = "grim";

    private final Map<String, String> previous = new LinkedHashMap<>();

    /** Records and returns {module -> new preset} for every module not already on the target. */
    public Map<String, String> apply(Map<String, String> current) {
        Map<String, String> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : current.entrySet()) {
            if (TARGET.equals(e.getValue()) || previous.containsKey(e.getKey())) continue;
            previous.put(e.getKey(), e.getValue());
            changes.put(e.getKey(), TARGET);
        }
        return changes;
    }

    /** Returns {module -> old preset} for modules still on the target; clears the memory. */
    public Map<String, String> restore(Map<String, String> current) {
        Map<String, String> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : previous.entrySet()) {
            if (TARGET.equals(current.get(e.getKey()))) changes.put(e.getKey(), e.getValue());
        }
        previous.clear();
        return changes;
    }

    public boolean isActive() {
        return !previous.isEmpty();
    }
}
