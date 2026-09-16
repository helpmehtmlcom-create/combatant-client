/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.water;

/** Explicit validity states mirrored by the forward-water thickness resolver. */
public enum WaterThicknessStatus {
    EXACT_LOCAL_BOUNDARY(1),
    VALID_SCREEN_SPACE(2),
    CONNECTED_FLUID_CONTINUATION(3),
    UNKNOWN_OFFSCREEN(4),
    CAMERA_INSIDE_FLUID(5),
    MISSING_BACK_SURFACE(6);

    private final int gpuCode;

    WaterThicknessStatus(int gpuCode) {
        this.gpuCode = gpuCode;
    }

    public int gpuCode() {
        return gpuCode;
    }
}
