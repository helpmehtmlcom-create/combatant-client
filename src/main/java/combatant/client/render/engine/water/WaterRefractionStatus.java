/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.water;

/** Explicit validity/fallback states for the forward-water refraction contract. */
public enum WaterRefractionStatus {
    VALID_EXACT_BOUNDARY(1),
    VALID_SCREEN_SPACE(2),
    INVALID_BOUNDARY_DIRECTION(3),
    OFFSCREEN(4),
    TOTAL_INTERNAL_REFLECTION(5),
    UNKNOWN(6);

    private final int gpuCode;

    WaterRefractionStatus(int gpuCode) {
        this.gpuCode = gpuCode;
    }

    public int gpuCode() {
        return gpuCode;
    }
}
