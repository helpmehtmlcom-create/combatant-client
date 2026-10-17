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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleSearchTest {
    private static ModuleSearch.Result m(String q, String name, List<String> aliases, List<String> settings, String desc) {
        return ModuleSearch.match(q, name, aliases, settings, desc);
    }

    @Test
    void nameTiersOrderExactPrefixWordSubstring() {
        int exact = m("aura", "Aura", List.of(), List.of(), "").rank();
        int prefix = m("kill", "KillAura", List.of(), List.of(), "").rank();
        int word = m("aura", "Kill Aura", List.of(), List.of(), "").rank();
        int sub = m("ura", "KillAura", List.of(), List.of(), "").rank();
        assertTrue(exact < prefix || exact == 0);
        assertEquals(0, exact);
        assertEquals(1, prefix);
        assertEquals(2, word);
        assertEquals(3, sub);
    }

    @Test
    void aliasRanksBelowName() {
        var r = m("cheststealer", "Stealer", List.of("ChestStealer"), List.of(), "");
        assertEquals(ModuleSearch.Field.ALIAS, r.field());
        assertTrue(r.rank() >= 100);
    }

    @Test
    void settingNameMatchesOnlyForLongEnoughQueries() {
        var hit = m("reach", "KillAura", List.of(), List.of("Attack reach", "Delay"), "");
        assertEquals(ModuleSearch.Field.SETTING, hit.field());
        assertEquals("Attack reach", hit.matchedText());
        assertFalse(m("r", "KillAura", List.of(), List.of("Attack reach"), "").matched() && m("r", "KillAura", List.of(), List.of("Attack reach"), "").field() == ModuleSearch.Field.SETTING);
    }

    @Test
    void descriptionMatchesAllTokens() {
        assertTrue(m("hits players", "Aura", List.of(), List.of(), "Automatically hits nearby players").matched());
        assertFalse(m("hits zombies", "Aura", List.of(), List.of(), "Automatically hits nearby players").matched());
    }

    @Test
    void fuzzySubsequenceMatchesNameButNotSparse() {
        assertEquals(ModuleSearch.Field.NAME, m("kla", "KillAura", List.of(), List.of(), "").field());
        assertTrue(m("kla", "KillAura", List.of(), List.of(), "").rank() >= 200);
        assertFalse(m("zzz", "KillAura", List.of(), List.of(), "").matched());
    }

    @Test
    void betterTierAlwaysBeatsWorse() {
        int name = m("fly", "Flight Fly", List.of(), List.of(), "").rank();
        int desc = m("fly", "Speed", List.of(), List.of(), "lets you fly").rank();
        assertTrue(name < desc);
    }

    @Test
    void emptyQueryMatchesEverything() {
        assertEquals(0, m("  ", "Anything", null, null, null).rank());
    }

    @Test
    void ignoresSeparators() {
        assertEquals(0, m("auto totem", "AutoTotem", List.of(), List.of(), "").rank());
        assertEquals(0, m("auto_totem", "Auto Totem", List.of(), List.of(), "").rank());
    }
}
