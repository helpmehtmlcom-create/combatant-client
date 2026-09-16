/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.water;

/**
 * Neutral technological profile for the forward-water contract. Values are deliberately generic;
 * artistic wave/color/foam/caustic policy belongs to later dimension/material profiles.
 */
public record WaterForwardProfile(
        float displacementAmplitudeFactor,
        float displacementSpatialFrequency,
        float displacementTemporalFrequency,
        float windCoupling,
        float flowCoupling,
        float rainRippleContribution,
        float windX,
        float windZ,
        float absorptionR,
        float absorptionG,
        float absorptionB,
        float scatteringR,
        float scatteringG,
        float scatteringB,
        float refractionProbeDistance
) {
    public static final WaterForwardProfile FOUNDATION = new WaterForwardProfile(
            0.08f, 1.0f, 1.0f,
            0.0f, 1.0f, 0.0f,
            0.0f, 0.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f,
            1.0f
    );
}
