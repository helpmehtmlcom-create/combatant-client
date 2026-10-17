/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.anticheat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PresetRulesTest {

    private enum Mode { A, B, C }

    private AntiCheatPreset preset = AntiCheatPreset.CUSTOM;

    private NumberValue<Float> range() {
        NumberValue<Float> v = new NumberValue<>("range", 5.5f, 1.0f, 6.0f);
        PresetRules.bind(v, () -> preset, PresetRules.Num.max(4.5), PresetRules.Num.fixed(5.0));
        return v;
    }

    @Test
    void grimClampsSliderAndValueButRawRestoresOnCustom() {
        NumberValue<Float> v = range();
        assertEquals(5.5f, v.get());
        preset = AntiCheatPreset.GRIM;
        assertEquals(4.5f, v.get());
        assertEquals(4.5f, v.getMax());
        assertEquals(1.0f, v.getMin());
        assertFalse(v.isForced());
        assertEquals(5.5f, v.getRaw());
        assertEquals(5.5, ((Number) v.toJson()).doubleValue());
        v.set(9.0f);
        assertEquals(4.5f, v.getRaw());
        preset = AntiCheatPreset.CUSTOM;
        v.set(5.5f);
        preset = AntiCheatPreset.GRIM;
        preset = AntiCheatPreset.CUSTOM;
        assertEquals(5.5f, v.get());
    }

    @Test
    void vanillaForcesValueAndIgnoresWrites() {
        NumberValue<Float> v = range();
        preset = AntiCheatPreset.VANILLA;
        assertTrue(v.isForced());
        assertEquals(5.0f, v.get());
        v.set(2.0f);
        assertEquals(5.5f, v.getRaw());
        preset = AntiCheatPreset.CUSTOM;
        assertEquals(5.5f, v.get());
    }

    @Test
    void configRoundTripKeepsRaw() {
        NumberValue<Float> v = range();
        preset = AntiCheatPreset.GRIM;
        v.fromJson(6.0);
        assertEquals(4.5f, v.get());
        assertEquals(6.0, ((Number) v.toJson()).doubleValue());
    }

    @Test
    void enumFiltersOptionsAndFallsBack() {
        EnumValue<Mode> v = new EnumValue<>("mode", Mode.C, Mode.class);
        PresetRules.bind(v, () -> preset, PresetRules.Opt.except(Mode.A, Mode.C), PresetRules.Opt.fixed(Mode.B));
        assertEquals(Mode.C, v.get());
        preset = AntiCheatPreset.GRIM;
        assertEquals(Mode.A, v.get());
        assertEquals(List.of("a", "b"), v.getOptions().stream().map(String::toLowerCase).toList());
        assertEquals("c", v.toJson());
        v.setById("c");
        assertEquals(Mode.C, v.getRaw());
        preset = AntiCheatPreset.VANILLA;
        assertTrue(v.isForced());
        assertEquals(Mode.B, v.get());
        preset = AntiCheatPreset.CUSTOM;
        assertEquals(Mode.C, v.get());
        assertEquals(3, v.getOptions().size());
    }

    @Test
    void booleanForcedKeepsRaw() {
        BooleanValue v = new BooleanValue("b", true);
        PresetRules.bind(v, () -> preset, PresetRules.Flag.OFF, PresetRules.Flag.FREE);
        preset = AntiCheatPreset.GRIM;
        assertFalse(v.get());
        assertTrue(v.isForced());
        v.set(false);
        assertEquals(true, v.toJson());
        preset = AntiCheatPreset.CUSTOM;
        assertTrue(v.get());
    }
}
