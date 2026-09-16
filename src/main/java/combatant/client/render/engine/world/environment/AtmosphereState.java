/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import net.minecraft.resources.Identifier;

/**
 * Explicit optical atmosphere contract. Values are physical/model parameters, not art grading.
 * Lengths are kilometres; scattering/extinction are inverse-kilometres.
 */
public record AtmosphereState(
        Identifier modelId,
        float planetRadiusKm,
        float atmosphereTopKm,
        float rayleighRed,
        float rayleighGreen,
        float rayleighBlue,
        float rayleighScaleHeightKm,
        float mieScatteringRed,
        float mieScatteringGreen,
        float mieScatteringBlue,
        float mieExtinctionRed,
        float mieExtinctionGreen,
        float mieExtinctionBlue,
        float mieScaleHeightKm,
        float mieAnisotropy,
        float ozoneAbsorptionRed,
        float ozoneAbsorptionGreen,
        float ozoneAbsorptionBlue,
        float ozoneCenterKm,
        float ozoneWidthKm,
        float groundAlbedoRed,
        float groundAlbedoGreen,
        float groundAlbedoBlue,
        boolean valid
) {
    public static final AtmosphereState NONE = new AtmosphereState(
            Identifier.fromNamespaceAndPath("combatant", "none"),
            6360.0f, 100.0f,
            0.0f, 0.0f, 0.0f, 8.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.2f, 0.0f,
            0.0f, 0.0f, 0.0f, 25.0f, 15.0f,
            0.0f, 0.0f, 0.0f, false
    );

    public AtmosphereState {
        if (modelId == null) modelId = NONE.modelId;
        planetRadiusKm = positive(planetRadiusKm, 6360.0f);
        atmosphereTopKm = positive(atmosphereTopKm, 100.0f);
        rayleighRed = nonNegative(rayleighRed);
        rayleighGreen = nonNegative(rayleighGreen);
        rayleighBlue = nonNegative(rayleighBlue);
        rayleighScaleHeightKm = positive(rayleighScaleHeightKm, 8.0f);
        mieScatteringRed = nonNegative(mieScatteringRed);
        mieScatteringGreen = nonNegative(mieScatteringGreen);
        mieScatteringBlue = nonNegative(mieScatteringBlue);
        mieExtinctionRed = Math.max(mieScatteringRed, nonNegative(mieExtinctionRed));
        mieExtinctionGreen = Math.max(mieScatteringGreen, nonNegative(mieExtinctionGreen));
        mieExtinctionBlue = Math.max(mieScatteringBlue, nonNegative(mieExtinctionBlue));
        mieScaleHeightKm = positive(mieScaleHeightKm, 1.2f);
        mieAnisotropy = Float.isFinite(mieAnisotropy) ? Math.max(-0.95f, Math.min(0.95f, mieAnisotropy)) : 0.0f;
        ozoneAbsorptionRed = nonNegative(ozoneAbsorptionRed);
        ozoneAbsorptionGreen = nonNegative(ozoneAbsorptionGreen);
        ozoneAbsorptionBlue = nonNegative(ozoneAbsorptionBlue);
        ozoneCenterKm = nonNegative(ozoneCenterKm);
        ozoneWidthKm = positive(ozoneWidthKm, 15.0f);
        groundAlbedoRed = clamp01(groundAlbedoRed);
        groundAlbedoGreen = clamp01(groundAlbedoGreen);
        groundAlbedoBlue = clamp01(groundAlbedoBlue);
    }

    public float atmosphereRadiusKm() {
        return planetRadiusKm + atmosphereTopKm;
    }

    private static float positive(float v, float fallback) {
        return Float.isFinite(v) && v > 0.0f ? v : fallback;
    }

    private static float nonNegative(float v) {
        return Float.isFinite(v) ? Math.max(0.0f, v) : 0.0f;
    }

    private static float clamp01(float v) {
        return Float.isFinite(v) ? Math.max(0.0f, Math.min(1.0f, v)) : 0.0f;
    }
}
