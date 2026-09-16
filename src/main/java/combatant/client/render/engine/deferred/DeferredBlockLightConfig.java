/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/** Numerical/quality policy for the renderer-owned colored block-light clipmap. */
record DeferredBlockLightConfig(
        boolean enabled,
        int sizeX,
        int sizeY,
        int sizeZ,
        int originAlignment,
        int refreshIntervalFrames,
        int propagationSteps,
        float surfaceSampleOffset
) {
    private static final DeferredBlockLightConfig DEFAULT = new DeferredBlockLightConfig(
            true, 48, 32, 48, 4, 4, 15, 0.25f
    );

    DeferredBlockLightConfig {
        sizeX = clamp(sizeX, 8, 128);
        sizeY = clamp(sizeY, 8, 128);
        sizeZ = clamp(sizeZ, 8, 128);
        originAlignment = clamp(originAlignment, 1, 16);
        refreshIntervalFrames = clamp(refreshIntervalFrames, 1, 120);
        propagationSteps = clamp(propagationSteps, 1, 32);
        surfaceSampleOffset = Float.isFinite(surfaceSampleOffset)
                ? Math.max(0.0f, Math.min(1.0f, surfaceSampleOffset)) : 0.25f;
    }

    static DeferredBlockLightConfig current() {
        return DEFAULT;
    }

    int atlasColumns() {
        return Math.max(1, (int) Math.ceil(Math.sqrt(sizeZ)));
    }

    int atlasRows() {
        return (sizeZ + atlasColumns() - 1) / atlasColumns();
    }

    int atlasWidth() {
        return Math.multiplyExact(sizeX, atlasColumns());
    }

    int atlasHeight() {
        return Math.multiplyExact(sizeY, atlasRows());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
