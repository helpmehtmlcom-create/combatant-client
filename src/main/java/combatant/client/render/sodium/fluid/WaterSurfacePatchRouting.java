/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sodium.fluid;

/** Compatibility facade for water replacement routing. */
public final class WaterSurfacePatchRouting {
    private WaterSurfacePatchRouting() { }
    public static boolean replacementActive() { return SurfacePatchRouting.waterReplacementActive(); }
    public static boolean setReplacementActive(boolean active) { return SurfacePatchRouting.setWaterReplacementActive(active); }
}
