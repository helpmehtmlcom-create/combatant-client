/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.math;

public enum HudScale {
    ;
    public static final float BASE_WIDTH = 1920f;
    public static final float BASE_HEIGHT = 1080f;
    public static final float FIXED_HUD_FONT_SIZE = 20f;

    public static float scale(int screenW, int screenH) {
        if (screenW <= 0 || screenH <= 0) return 1f;
        float sx = screenW / BASE_WIDTH;
        float sy = screenH / BASE_HEIGHT;
        float s = Math.min(sx, sy);
        if (s <= 1e-6f || !Float.isFinite(s)) return 1f;
        return s;
    }

    /**
     * Logical (virtual) width in UI units for given framebuffer size.
     */
    public static float virtualWidth(int screenW, int screenH) {
        if (screenW <= 0) return 0f;
        float scale = scale(screenW, screenH);
        if (scale <= 1e-6f || !Float.isFinite(scale)) return screenW;
        return screenW / scale;
    }

    /**
     * Logical (virtual) height in UI units for given framebuffer size.
     */
    public static float virtualHeight(int screenW, int screenH) {
        if (screenH <= 0) return 0f;
        float scale = scale(screenW, screenH);
        if (scale <= 1e-6f || !Float.isFinite(scale)) return screenH;
        return screenH / scale;
    }

    /**
     * Convert framebuffer-space value to logical UI units.
     */
    public static float toVirtual(float value, float scale) {
        if (!Float.isFinite(value)) return 0f;
        if (scale <= 1e-6f || !Float.isFinite(scale)) return value;
        return value / scale;
    }

    /**
     * Convert logical UI units to framebuffer-space value.
     */
    public static float toFramebuffer(float value, float scale) {
        if (!Float.isFinite(value)) return 0f;
        if (scale <= 1e-6f || !Float.isFinite(scale)) return value;
        return value * scale;
    }

    public static float s(float value, float scale) {
        if (!Float.isFinite(value) || !Float.isFinite(scale)) return 0f;
        return value * scale;
    }

    public static float hudFontSize() {
        return FIXED_HUD_FONT_SIZE;
    }
}
