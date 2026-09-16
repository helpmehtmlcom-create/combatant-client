/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world;

/** Canonical packing contract for the renderer-owned aerial-perspective lookup tables. */
public final class AerialPerspectiveLayout {
    public static final int DISTANCE_SLICES = 32;
    public static final int ZENITH_SLICES = 32;
    public static final int AZIMUTH_SLICES = 16;
    public static final int WIDTH = ZENITH_SLICES * AZIMUTH_SLICES;
    public static final int HEIGHT = DISTANCE_SLICES;

    private AerialPerspectiveLayout() {
    }
}
