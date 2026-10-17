/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickGuiScalingTest {
    private static final float DW = 631f;
    private static final float DH = 285f;

    @Test
    void wideWindowCapsAtMax() {
        assertEquals(ClickGuiScaling.MAX_SCALE, ClickGuiScaling.fit(5000f, 3000f, DW, DH));
    }

    @Test
    void standard1080pFitsWithinArea() {
        float s = ClickGuiScaling.fit(1864f, 1010f, DW, DH);
        assertTrue(s > 2.5f && s <= 3.0f);
        assertTrue(DW * s <= 1864f);
    }

    @Test
    void narrowWindowShrinksInsteadOfOverflowing() {
        float areaW = 1080f - 56f;
        float s = ClickGuiScaling.fit(areaW, 1010f, DW, DH);
        assertTrue(s < 2.0f, "old 2.55 floor would overflow: " + s);
        assertTrue(DW * s <= areaW);
    }

    @Test
    void userScaleShrinksAndIsClamped() {
        float base = ClickGuiScaling.fit(1864f, 1010f, DW, DH);
        assertEquals(base * 0.8f, ClickGuiScaling.resolve(1864f, 1010f, DW, DH, 0.8f), 1e-4f);
        assertEquals(base, ClickGuiScaling.resolve(1864f, 1010f, DW, DH, 5f), 1e-4f);
        assertEquals(base * ClickGuiScaling.MIN_USER, ClickGuiScaling.resolve(1864f, 1010f, DW, DH, 0.01f), 1e-4f);
        assertEquals(base, ClickGuiScaling.resolve(1864f, 1010f, DW, DH, Float.NaN), 1e-4f);
    }

    @Test
    void degenerateAreaStaysFinite() {
        float s = ClickGuiScaling.resolve(1f, 1f, DW, DH, 1f);
        assertTrue(Float.isFinite(s) && s >= ClickGuiScaling.MIN_SCALE);
    }
}
