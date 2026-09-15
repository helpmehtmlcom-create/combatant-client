/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

/** Immutable tessellation request carried by the material contract. */
public record MaterialTessellationProfile(
        MaterialTessellationMode mode,
        float displacementScale,
        float minFactor,
        float maxFactor,
        float distanceFadeStart,
        float distanceFadeEnd
) {
    public static final MaterialTessellationProfile NONE = new MaterialTessellationProfile(
            MaterialTessellationMode.NONE, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f
    );
    public static final MaterialTessellationProfile WATER = new MaterialTessellationProfile(
            MaterialTessellationMode.WATER_SURFACE, 0.12f, 1.0f, 12.0f, 8.0f, 96.0f
    );

    public MaterialTessellationProfile {
        if (mode == null) mode = MaterialTessellationMode.NONE;
        displacementScale = Math.max(0.0f, displacementScale);
        minFactor = Math.max(1.0f, minFactor);
        maxFactor = Math.max(minFactor, maxFactor);
        distanceFadeStart = Math.max(0.0f, distanceFadeStart);
        distanceFadeEnd = Math.max(distanceFadeStart, distanceFadeEnd);
    }

    public static MaterialTessellationProfile height(float displacementScale) {
        return new MaterialTessellationProfile(
                MaterialTessellationMode.HEIGHT_DISPLACEMENT,
                displacementScale,
                1.0f,
                8.0f,
                6.0f,
                64.0f
        );
    }
}
