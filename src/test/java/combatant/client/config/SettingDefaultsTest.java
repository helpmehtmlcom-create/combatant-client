/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class SettingDefaultsTest {
    @Test
    void numbersCompareByValue() {
        assertTrue(SettingDefaults.jsonEquals(1, 1.0));
        assertFalse(SettingDefaults.jsonEquals(1, 2));
    }

    @Test
    void deepStructuresCompare() {
        assertTrue(SettingDefaults.jsonEquals(Map.of("a", List.of(1, 2)), Map.of("a", List.of(1.0, 2.0))));
        assertFalse(SettingDefaults.jsonEquals(Map.of("a", List.of(1)), Map.of("a", List.of(1, 2))));
        assertFalse(SettingDefaults.jsonEquals(Map.of("a", 1), Map.of("b", 1)));
    }

    @Test
    void changedIgnoresMissingDefault() {
        assertFalse(SettingDefaults.isChanged(null, 5));
        assertTrue(SettingDefaults.isChanged(true, false));
        assertFalse(SettingDefaults.isChanged("x", "x"));
    }

    @Test
    void formatsNumbers() {
        assertEquals("3", SettingDefaults.formatNumber(3.0, false, 2));
        assertEquals("3.14", SettingDefaults.formatNumber(3.14159, false, 2));
        assertEquals("0.5", SettingDefaults.formatNumber(0.5, false, 3));
        assertEquals("4", SettingDefaults.formatNumber(3.6, true, 0));
        assertEquals("0", SettingDefaults.formatNumber(Double.NaN, false, 2));
    }

    @Test
    void parsesAndClamps() {
        assertEquals(2.5, SettingDefaults.parseNumber("2,5", false, 0, 10));
        assertEquals(10, SettingDefaults.parseNumber("99", true, 0, 10));
        assertTrue(Double.isNaN(SettingDefaults.parseNumber("1.5", true, 0, 10)));
        assertTrue(Double.isNaN(SettingDefaults.parseNumber("-", false, -5, 5)));
        assertTrue(Double.isNaN(SettingDefaults.parseNumber("abc", false, 0, 1)));
        assertTrue(Double.isNaN(SettingDefaults.parseNumber(null, false, 0, 1)));
    }

    @Test
    void stepsClamp() {
        assertEquals(1.5, SettingDefaults.step(1.0, 0.5, 1, 0, 3));
        assertEquals(0.0, SettingDefaults.step(0.5, 0.5, -3, 0, 3));
        assertEquals(3.0, SettingDefaults.step(2.5, 0.5, 5, 0, 3));
    }
}
