/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.nav;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentListTest {
    @Test
    void newestFirstNoDuplicatesAndBounded() {
        RecentList r = new RecentList(3);
        r.touch("a");
        r.touch("b");
        r.touch("c");
        r.touch("a");
        assertEquals(List.of("a", "c", "b"), r.snapshot());
        r.touch("d");
        assertEquals(List.of("d", "a", "c"), r.snapshot());
    }

    @Test
    void replaceAllKeepsOrderAndCap() {
        RecentList r = new RecentList(2);
        r.replaceAll(List.of("x", "y", "z"));
        assertEquals(List.of("x", "y"), r.snapshot());
    }

    @Test
    void ignoresBlank() {
        RecentList r = new RecentList(2);
        r.touch(" ");
        r.touch(null);
        assertEquals(0, r.snapshot().size());
    }
}
