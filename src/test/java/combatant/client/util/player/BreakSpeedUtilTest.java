/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Break-speed arithmetic against the values {@code Player.getDestroySpeed} / {@code getDestroyProgress} produce. */
class BreakSpeedUtilTest {

    private static final float EPS = 1.0e-6f;

    @Test
    void obsidianWithDiamondPickaxeTakes188Ticks() {
        // Diamond pickaxe 8.0 on obsidian (hardness 50, right tool): 8 / 50 / 30.
        float perTick = BreakSpeedUtil.progressFromSpeed(8.0f, 50.0f, true);
        assertEquals(8.0f / 1500.0f, perTick, EPS);
        assertEquals(188, (int) Math.ceil(1.0f / perTick));
    }

    @Test
    void wrongToolIsHundredDivisor() {
        assertEquals(0.02f, BreakSpeedUtil.progressFromSpeed(1.0f, 0.5f, false), EPS);
    }

    @Test
    void zeroHardnessIsInstantAndNegativeIsUnbreakable() {
        assertEquals(1.0f, BreakSpeedUtil.progressFromSpeed(1.0f, 0.0f, true), EPS);
        assertEquals(0.0f, BreakSpeedUtil.progressFromSpeed(8.0f, -1.0f, true), EPS);
    }

    @Test
    void hasteAddsTwentyPercentPerLevel() {
        assertEquals(1.2f, BreakSpeedUtil.hasteMultiplier(0), EPS);
        assertEquals(1.4f, BreakSpeedUtil.hasteMultiplier(1), EPS);
        assertEquals(2.0f, BreakSpeedUtil.hasteMultiplier(4), EPS);
    }

    @Test
    void fatigueIsPowerOfPointThreeWithoutCap() {
        assertEquals(0.3f, BreakSpeedUtil.fatigueMultiplier(0), EPS);
        assertEquals(0.09f, BreakSpeedUtil.fatigueMultiplier(1), EPS);
        assertEquals(0.027f, BreakSpeedUtil.fatigueMultiplier(2), EPS);
        assertEquals(0.0081f, BreakSpeedUtil.fatigueMultiplier(3), EPS);
        assertEquals(0.00243f, BreakSpeedUtil.fatigueMultiplier(4), EPS);
    }

    @Test
    void airborneIsFiveTimesSlower() {
        assertEquals(8.0f, BreakSpeedUtil.airborneSpeed(8.0f, true), EPS);
        assertEquals(1.6f, BreakSpeedUtil.airborneSpeed(8.0f, false), EPS);
    }
}
