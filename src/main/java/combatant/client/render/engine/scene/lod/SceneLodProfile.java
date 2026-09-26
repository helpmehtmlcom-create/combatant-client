/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.lod;

import java.util.Arrays;

/**
 * Screen-space LOD boundaries for one consumer/asset family.
 *
 * <p>For N levels, N-1 strictly descending projected-diameter boundaries are supplied. Level 0 is
 * the highest-detail level. Example boundaries {@code [160, 64, 16]} describe four levels:</p>
 * <pre>
 * >=160 px -> LOD0
 * >= 64 px -> LOD1
 * >= 16 px -> LOD2
 * otherwise -> LOD3
 * </pre>
 */
public final class SceneLodProfile {
    private final float[] boundaries;
    private final float hysteresisFraction;

    public SceneLodProfile(float[] boundaries, float hysteresisFraction) {
        if (boundaries == null) throw new IllegalArgumentException("LOD boundaries are required");
        this.boundaries = Arrays.copyOf(boundaries, boundaries.length);
        float previous = Float.POSITIVE_INFINITY;
        for (float boundary : this.boundaries) {
            if (!Float.isFinite(boundary) || boundary <= 0.0f) {
                throw new IllegalArgumentException("LOD boundaries must be finite and positive");
            }
            if (!(boundary < previous)) {
                throw new IllegalArgumentException("LOD boundaries must be strictly descending");
            }
            previous = boundary;
        }
        if (!Float.isFinite(hysteresisFraction) || hysteresisFraction < 0.0f || hysteresisFraction >= 0.5f) {
            throw new IllegalArgumentException("LOD hysteresis must be in [0, 0.5)");
        }
        this.hysteresisFraction = hysteresisFraction;
    }

    public int levelCount() {
        return boundaries.length + 1;
    }

    public float hysteresisFraction() {
        return hysteresisFraction;
    }

    public float boundaryToCoarser(int level) {
        if (level < 0 || level >= boundaries.length) return 0.0f;
        return boundaries[level];
    }

    public float[] boundaries() {
        return Arrays.copyOf(boundaries, boundaries.length);
    }
}
