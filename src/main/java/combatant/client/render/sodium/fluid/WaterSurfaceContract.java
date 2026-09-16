/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sodium.fluid;

import net.minecraft.resources.Identifier;

/**
 * Explicit producer-side semantics carried by extracted water surfaces.
 *
 * <p>These values come from fluid meshing/state. They are never reconstructed from screen color,
 * texture slots or depth.</p>
 */
public final class WaterSurfaceContract {
    public static final int FLAG_TOP_SURFACE = 1 << 0;
    public static final int FLAG_SOURCE = 1 << 1;
    public static final int FLAG_STILL = 1 << 2;
    public static final int FLAG_FLOWING = 1 << 3;
    public static final int FLAG_REVERSED = 1 << 4;

    private WaterSurfaceContract() {
    }

    /** Stable deterministic 32-bit identifier for the producer-known fluid registry key. */
    public static int stableFluidTypeId(Identifier id) {
        String text = id == null ? "combatant:unknown_fluid" : id.toString();
        int hash = 0x811C9DC5;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i) & 0xFF;
            hash *= 0x01000193;
            hash ^= (text.charAt(i) >>> 8) & 0xFF;
            hash *= 0x01000193;
        }
        return hash;
    }

    public static float flowStrength(float x, float z) {
        return (float) Math.sqrt(x * x + z * z);
    }

    public static int flags(FluidSurfaceData surface) {
        if (surface == null) return 0;
        float strength = flowStrength(surface.flowX(), surface.flowZ());
        boolean still = strength <= 1.0e-5f;
        int flags = 0;
        if (surface.isTopSurface()) flags |= FLAG_TOP_SURFACE;
        if (surface.source()) flags |= FLAG_SOURCE;
        if (still) flags |= FLAG_STILL;
        else flags |= FLAG_FLOWING;
        if (surface.reversed()) flags |= FLAG_REVERSED;
        return flags;
    }

    /** Geometric orientation of the captured producer quad, in world axes. */
    public static float[] surfaceNormal(FluidSurfaceData surface) {
        if (surface == null) return new float[]{0.0f, 1.0f, 0.0f};
        float[] x = surface.x();
        float[] y = surface.y();
        float[] z = surface.z();
        // Sodium's top quad is ordered around the surface. Average both triangle normals so
        // sloped/uneven corners get one stable per-surface orientation contract.
        float ax = x[1] - x[0], ay = y[1] - y[0], az = z[1] - z[0];
        float bx = x[2] - x[0], by = y[2] - y[0], bz = z[2] - z[0];
        float cx = x[2] - x[0], cy = y[2] - y[0], cz = z[2] - z[0];
        float dx = x[3] - x[0], dy = y[3] - y[0], dz = z[3] - z[0];
        float nx = ay * bz - az * by + cy * dz - cz * dy;
        float ny = az * bx - ax * bz + cz * dx - cx * dz;
        float nz = ax * by - ay * bx + cx * dy - cy * dx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(len > 1.0e-6f) || !Float.isFinite(len)) return new float[]{0.0f, 1.0f, 0.0f};
        nx /= len;
        ny /= len;
        nz /= len;
        if (ny < 0.0f) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        return new float[]{nx, ny, nz};
    }
}
