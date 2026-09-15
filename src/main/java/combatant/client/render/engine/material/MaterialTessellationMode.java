/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

/**
 * Explicit producer-side tessellation policy. Sodium terrain remains triangle/quad submitted;
 * modes other than {@link #NONE} are requests for an extracted patch mesh, never a topology
 * mutation of the shared Sodium terrain batch.
 */
public enum MaterialTessellationMode {
    NONE(0),
    HEIGHT_DISPLACEMENT(1),
    WATER_SURFACE(2);

    private final int gpuCode;

    MaterialTessellationMode(int gpuCode) {
        this.gpuCode = gpuCode;
    }

    public int gpuCode() {
        return gpuCode;
    }

    public boolean requiresPatchMesh() {
        return this != NONE;
    }
}
