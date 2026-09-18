/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import java.util.Locale;

/** Optional flex-flow override for container nodes. Null on {@link UiStyle} means use the node type default. */
public enum UiFlexDirection {
    ROW,
    COLUMN;

    public static UiFlexDirection parse(String value, UiFlexDirection fallback) {
        if (value == null) return fallback;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "row", "horizontal", "x" -> ROW;
            case "column", "col", "vertical", "y" -> COLUMN;
            default -> fallback;
        };
    }
}
