/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.blend;

/**
 * Backend-neutral backdrop blend operator.
 *
 * <p>These are material/compositing modes, not fixed-function GPU blend factors. The fragment
 * material evaluates the operator against an immutable backdrop snapshot; the graphics pipeline's
 * ordinary source-over stage is then used only for geometric coverage/antialiasing.</p>
 */
public enum UiBackdropBlendMode {
    NORMAL(0),
    MULTIPLY(1),
    SCREEN(2),
    OVERLAY(3),
    DIFFERENCE(4),
    EXCLUSION(5),
    NEGATIVE(6),
    MONOCHROME(7),
    MONO_NEGATIVE(8),
    DUOTONE(9),
    SOLARIZE(10);

    private final int shaderId;

    UiBackdropBlendMode(int shaderId) {
        this.shaderId = shaderId;
    }

    public int shaderId() {
        return shaderId;
    }
}
