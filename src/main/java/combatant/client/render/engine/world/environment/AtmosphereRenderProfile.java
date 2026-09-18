/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.WorldRenderState;
import net.minecraft.resources.Identifier;

/** Artist-facing optical baseline and controlled climate response for one atmosphere model. */
public record AtmosphereRenderProfile(
        Identifier id,
        AtmosphereState baseAtmosphere,
        float biomeHumidityWeight,
        float weatherHumidityWeight,
        float humidityPivot,
        float humidityMieGain,
        float precipitationMieGain,
        float stormMieGain,
        float minMieMultiplier,
        float maxMieMultiplier,
        int aerosolQuantizationSteps,
        boolean valid
) {
    public AtmosphereRenderProfile {
        if (id == null) id = WorldRenderState.NONE;
        if (baseAtmosphere == null) baseAtmosphere = AtmosphereState.NONE;
        biomeHumidityWeight = nonNegative(biomeHumidityWeight);
        weatherHumidityWeight = nonNegative(weatherHumidityWeight);
        humidityPivot = clamp01(humidityPivot);
        humidityMieGain = nonNegative(humidityMieGain);
        precipitationMieGain = nonNegative(precipitationMieGain);
        stormMieGain = nonNegative(stormMieGain);
        minMieMultiplier = Math.max(0.01f, finite(minMieMultiplier, 1.0f));
        maxMieMultiplier = Math.max(minMieMultiplier, finite(maxMieMultiplier, minMieMultiplier));
        aerosolQuantizationSteps = Math.max(1, aerosolQuantizationSteps);
        valid = valid && !WorldRenderState.NONE.equals(id) && baseAtmosphere.valid();
    }

    private static float nonNegative(float value) {
        return Math.max(0.0f, finite(value, 0.0f));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, finite(value, 0.0f)));
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }
}
