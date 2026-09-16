/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.material;

/**
 * Explicit material response to weather accumulation. Current wetness/snow amount is NOT encoded
 * here; it must come from rain exposure plus persistent accumulation/evaporation resources.
 */
public record MaterialWeatherResponse(
        float wetLayerStrength,
        float absorptionRate,
        float dryingRate,
        float runoffRate,
        float puddleCapacity,
        float snowRetention
) {
    public static final MaterialWeatherResponse NONE = new MaterialWeatherResponse(0, 0, 0, 0, 0, 0);

    public MaterialWeatherResponse {
        wetLayerStrength = clamp01(wetLayerStrength);
        absorptionRate = nonNegative(absorptionRate);
        dryingRate = nonNegative(dryingRate);
        runoffRate = nonNegative(runoffRate);
        puddleCapacity = clamp01(puddleCapacity);
        snowRetention = clamp01(snowRetention);
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.0f;
    }

    private static float nonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }
}
