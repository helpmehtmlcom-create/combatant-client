/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.WorldRenderState;
import net.minecraft.resources.Identifier;

/**
 * Optical contract for non-atmospheric participating media injected into the view froxel grid.
 * Coefficients are inverse blocks at density 1.0; weather and height only modulate that density.
 */
public record ParticipatingMediumProfile(
        Identifier id,
        float maxDistanceBlocks,
        float referenceHeightBlocks,
        float scaleHeightBlocks,
        float baseDensity,
        float humidityThreshold,
        float humidityDensity,
        float precipitationDensity,
        float stormDensity,
        float scatteringRed,
        float scatteringGreen,
        float scatteringBlue,
        float absorptionRed,
        float absorptionGreen,
        float absorptionBlue,
        float anisotropy,
        float ambientRadianceScale,
        float directionalRadianceScale,
        boolean valid
) {
    public static final ParticipatingMediumProfile NONE = new ParticipatingMediumProfile(
            WorldRenderState.NONE,
            64.0f, 64.0f, 96.0f,
            0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 1.0f, false
    );

    public ParticipatingMediumProfile {
        if (id == null) id = WorldRenderState.NONE;
        maxDistanceBlocks = positive(maxDistanceBlocks, 64.0f);
        referenceHeightBlocks = finite(referenceHeightBlocks);
        scaleHeightBlocks = positive(scaleHeightBlocks, 96.0f);
        baseDensity = nonNegative(baseDensity);
        humidityThreshold = clamp01(humidityThreshold);
        humidityDensity = nonNegative(humidityDensity);
        precipitationDensity = nonNegative(precipitationDensity);
        stormDensity = nonNegative(stormDensity);
        scatteringRed = nonNegative(scatteringRed);
        scatteringGreen = nonNegative(scatteringGreen);
        scatteringBlue = nonNegative(scatteringBlue);
        absorptionRed = nonNegative(absorptionRed);
        absorptionGreen = nonNegative(absorptionGreen);
        absorptionBlue = nonNegative(absorptionBlue);
        anisotropy = clamp(anisotropy, -0.95f, 0.95f);
        ambientRadianceScale = nonNegative(ambientRadianceScale);
        directionalRadianceScale = nonNegative(directionalRadianceScale);
        if (scatteringRed <= 0.0f && scatteringGreen <= 0.0f && scatteringBlue <= 0.0f
                && absorptionRed <= 0.0f && absorptionGreen <= 0.0f && absorptionBlue <= 0.0f) {
            valid = false;
        }
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static float positive(float value, float fallback) {
        return Float.isFinite(value) && value > 0.0f ? value : fallback;
    }

    private static float nonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0f, 1.0f);
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
