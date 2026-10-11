/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

import java.util.ArrayList;
import java.util.List;

/** Logical rectangles of earlier glass draw boundaries in the same ordered replay. */
public final class UiGlassStackRegions {
    public static final int CAPACITY = 4;
    private final List<UiRect> previous = new ArrayList<>();

    /** Only older regions are candidates; the current lens is not included until after it draws. */
    public List<UiRect> overlapping(UiRect upper) {
        List<UiRect> result = new ArrayList<>(CAPACITY);
        if (upper == null || upper.empty()) return result;
        for (int i = previous.size() - 1; i >= 0 && result.size() < CAPACITY; i--) {
            UiRect lower = previous.get(i);
            if (overlaps(lower, upper)) result.add(lower);
        }
        return result;
    }

    public void add(UiRect bounds) {
        if (bounds == null || bounds.empty()) return;
        previous.add(bounds);
        // Older parts of a stack are already contained in the composited snapshot. Keep a
        // bounded history to avoid storing unbounded HUD geometry for a single flush.
        if (previous.size() > 64) previous.remove(0);
    }

    public static boolean overlaps(UiRect a, UiRect b) {
        return a != null && b != null && !a.empty() && !b.empty()
                && a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
                && a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
    }
}
