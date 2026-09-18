/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.ui.runtime.style.UiColor;

/** Shared color resolution for runtime painters. Keeps lifecycle alpha out of painter instance state. */
final class UiRenderColors {
    private UiRenderColors() {
    }

    static int raw(Object value, int fallback) {
        return UiReactiveVisual.rawColor(value, fallback);
    }

    static int resolve(Object value, int fallback, float alpha) {
        return applyAlpha(raw(value, fallback), alpha);
    }

    static int applyAlpha(int argb, float alpha) {
        return alpha >= 0.999f ? argb : UiColor.multiplyAlpha(argb, alpha);
    }

    static int lighten(int argb, float amount) {
        return adjust(argb, Math.abs(amount));
    }

    static int darken(int argb, float amount) {
        return adjust(argb, -Math.abs(amount));
    }

    private static int adjust(int argb, float delta) {
        int a = (argb >>> 24) & 0xFF;
        int r = channel((argb >>> 16) & 0xFF, delta);
        int g = channel((argb >>> 8) & 0xFF, delta);
        int b = channel(argb & 0xFF, delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int value, float delta) {
        int out = delta >= 0.0f
                ? value + Math.round((255 - value) * delta)
                : value - Math.round(value * -delta);
        return Math.max(0, Math.min(255, out));
    }
}
