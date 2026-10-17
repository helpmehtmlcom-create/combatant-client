/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.nav;

/** Pure sizing math for the ClickGUI module panels. */
public final class ClickGuiScaling {
    /** Design-unit to logical-pixel cap (reached on roomy windows). */
    public static final float MAX_SCALE = 3.0f;
    /** Absolute floor so extremely small windows still produce a finite, drawable layout. */
    public static final float MIN_SCALE = 0.5f;
    public static final float MIN_USER = 0.6f;
    public static final float MAX_USER = 1.0f;

    private ClickGuiScaling() {
    }

    /**
     * Largest scale at which the panel row fits the area, never above {@link #MAX_SCALE}.
     * There is deliberately no lower clamp above {@link #MIN_SCALE}: narrow windows shrink the
     * panels instead of overflowing.
     */
    public static float fit(float areaW, float areaH, float designW, float designH) {
        float sideMargin = Math.max(28.0f, areaW * 0.03f);
        float verticalReserve = Math.max(56.0f, areaH * 0.10f);
        float fitX = (areaW - sideMargin * 2.0f) / Math.max(1.0f, designW);
        float fitY = (areaH - verticalReserve) / Math.max(1.0f, designH);
        float fit = Math.min(fitX, fitY);
        if (!Float.isFinite(fit)) return MIN_SCALE;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, fit));
    }

    /** Fit scale multiplied by the user's size preference (1.0 = fill the window). */
    public static float resolve(float areaW, float areaH, float designW, float designH, float userScale) {
        float user = Float.isFinite(userScale) ? Math.max(MIN_USER, Math.min(MAX_USER, userScale)) : 1.0f;
        return Math.max(MIN_SCALE, fit(areaW, areaH, designW, designH) * user);
    }
}
