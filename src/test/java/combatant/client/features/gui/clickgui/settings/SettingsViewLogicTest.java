/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class SettingsViewLogicTest {
    @Test
    void leadTokenSplitsSnakeAndCamel() {
        assertEquals("aim", SettingsViewLogic.leadToken("aim_point_delay"));
        assertEquals("aim", SettingsViewLogic.leadToken("aimPointDelay"));
        assertEquals("criticals", SettingsViewLogic.leadToken("Criticals"));
    }

    @Test
    void groupsRunsOfThreeOrMore() {
        List<String> ids = List.of("keep_sprint", "aim_a", "aim_b", "aim_c", "criticals", "criticals_mode");
        List<SettingsViewLogic.Section> s = SettingsViewLogic.sections(ids, 3);
        assertEquals(1, s.size());
        assertEquals(new SettingsViewLogic.Section("aim", 1, 4), s.get(0));
    }

    @Test
    void singleWordIdsAreNotGroupedTogether() {
        // "range", "rotate" differ; identical single-word ids cannot occur but distinct ones must not merge.
        assertTrue(SettingsViewLogic.sections(List.of("range", "rotate", "reach"), 2).isEmpty());
    }

    @Test
    void filterMatching() {
        assertTrue(SettingsViewLogic.matches("", "x"));
        assertTrue(SettingsViewLogic.matches(null, "x"));
        assertTrue(SettingsViewLogic.matches(" AIM ", "Aim point", null));
        assertFalse(SettingsViewLogic.matches("zzz", "Aim point", "desc"));
        assertTrue(SettingsViewLogic.matches("desc", "Aim point", "a description"));
    }

    @Test
    void titleCase() {
        assertEquals("Aim", SettingsViewLogic.titleFor("aim"));
        assertEquals("", SettingsViewLogic.titleFor(""));
    }
}
