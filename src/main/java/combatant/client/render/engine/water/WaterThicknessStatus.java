/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.water;

/** Explicit validity states mirrored by the forward-water shader's thickness resolver. */
public enum WaterThicknessStatus {
    VALID_SCREEN_SPACE,
    UNKNOWN_OFFSCREEN,
    CAMERA_INSIDE_FLUID,
    MISSING_BACK_SURFACE
}
