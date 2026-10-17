/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.nav;

import java.util.ArrayList;
import java.util.List;

/** Bounded most-recently-used id list; newest first, no duplicates. */
public final class RecentList {
    private final int capacity;
    private final List<String> items = new ArrayList<>();

    public RecentList(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public void touch(String id) {
        if (id == null || id.isBlank()) return;
        items.remove(id);
        items.add(0, id);
        while (items.size() > capacity) items.remove(items.size() - 1);
    }

    public void remove(String id) {
        items.remove(id);
    }

    /** Index of the id (0 = most recent) or -1. */
    public int indexOf(String id) {
        return items.indexOf(id);
    }

    public List<String> snapshot() {
        return List.copyOf(items);
    }

    public void replaceAll(List<String> ids) {
        items.clear();
        if (ids == null) return;
        for (int i = ids.size() - 1; i >= 0; i--) touch(ids.get(i));
        // touch() reverses insertion order; the loop above preserves the given order (first = newest).
    }
}
