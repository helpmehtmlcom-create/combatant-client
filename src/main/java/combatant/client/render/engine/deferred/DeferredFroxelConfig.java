/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

/** Numerical policy for the view-aligned participating-media froxel grid. */
record DeferredFroxelConfig(
        boolean enabled,
        int tileSizePixels,
        int depthSlices,
        float depthExponent,
        float minimumMaxDistanceBlocks,
        int maxLocalFogVolumes
) {
    private static final DeferredFroxelConfig DEFAULT = new DeferredFroxelConfig(
            true, 16, 48, 2.0f, 64.0f, 128
    );

    DeferredFroxelConfig {
        tileSizePixels = clamp(tileSizePixels, 4, 64);
        depthSlices = clamp(depthSlices, 8, 128);
        depthExponent = clamp(depthExponent, 1.0f, 4.0f);
        minimumMaxDistanceBlocks = clamp(minimumMaxDistanceBlocks, 16.0f, 4096.0f);
        maxLocalFogVolumes = clamp(maxLocalFogVolumes, 1, 1024);
    }

    static DeferredFroxelConfig current() {
        return DEFAULT;
    }

    Grid grid(int fullWidth, int fullHeight, float farPlane) {
        int width = Math.max(1, (Math.max(1, fullWidth) + tileSizePixels - 1) / tileSizePixels);
        int height = Math.max(1, (Math.max(1, fullHeight) + tileSizePixels - 1) / tileSizePixels);
        float maxDistance = Float.isFinite(farPlane) && farPlane > 0.0f
                ? Math.max(minimumMaxDistanceBlocks, farPlane)
                : minimumMaxDistanceBlocks;
        return new Grid(width, height, depthSlices, maxDistance, depthExponent);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Grid(int width, int height, int depth, float maxDistanceBlocks, float depthExponent) {
        Grid {
            width = Math.max(1, width);
            height = Math.max(1, height);
            depth = Math.max(1, depth);
            if (!Float.isFinite(maxDistanceBlocks) || maxDistanceBlocks <= 0.0f) maxDistanceBlocks = 64.0f;
            if (!Float.isFinite(depthExponent) || depthExponent < 1.0f) depthExponent = 1.0f;
        }

        int froxelCount() {
            return Math.multiplyExact(Math.multiplyExact(width, height), depth);
        }
    }
}
