/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.material;

/**
 * Explicit material response to persistent weather state. The current deposited amounts are
 * renderer-owned state and are deliberately not reconstructed from rendered color or normals.
 */
public record MaterialWeatherResponse(
        float wetLayerStrength,
        float absorptionRate,
        float dryingRate,
        float runoffRate,
        float puddleCapacity,
        float snowRetention,
        float particulateRetention,
        float particulateWashOffRate
) {
    public static final MaterialWeatherResponse NONE = new MaterialWeatherResponse(0, 0, 0, 0, 0, 0, 0, 0);

    /** Backward-compatible constructor for existing six-field descriptors/callers. */
    public MaterialWeatherResponse(float wetLayerStrength, float absorptionRate, float dryingRate,
                                   float runoffRate, float puddleCapacity, float snowRetention) {
        this(wetLayerStrength, absorptionRate, dryingRate, runoffRate, puddleCapacity, snowRetention, 0.0f, 1.0f);
    }

    public MaterialWeatherResponse {
        wetLayerStrength = clamp01(wetLayerStrength);
        absorptionRate = nonNegative(absorptionRate);
        dryingRate = nonNegative(dryingRate);
        runoffRate = nonNegative(runoffRate);
        puddleCapacity = clamp01(puddleCapacity);
        snowRetention = clamp01(snowRetention);
        particulateRetention = clamp01(particulateRetention);
        particulateWashOffRate = nonNegative(particulateWashOffRate);
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.0f;
    }

    private static float nonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }
}
