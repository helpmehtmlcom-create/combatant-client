/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.spatial;

import net.minecraft.world.phys.AABB;

/** Immutable world-space sphere used by projected-size/LOD policies. */
public record SceneBoundingSphere(double x, double y, double z, double radius) {
    public SceneBoundingSphere {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("Invalid scene bounding sphere");
        }
    }

    public static SceneBoundingSphere from(AABB bounds) {
        if (bounds == null) return new SceneBoundingSphere(0.0, 0.0, 0.0, 0.0);
        double x = (bounds.minX + bounds.maxX) * 0.5;
        double y = (bounds.minY + bounds.maxY) * 0.5;
        double z = (bounds.minZ + bounds.maxZ) * 0.5;
        double hx = Math.max(0.0, bounds.maxX - bounds.minX) * 0.5;
        double hy = Math.max(0.0, bounds.maxY - bounds.minY) * 0.5;
        double hz = Math.max(0.0, bounds.maxZ - bounds.minZ) * 0.5;
        return new SceneBoundingSphere(x, y, z, Math.sqrt(hx * hx + hy * hy + hz * hz));
    }
}
