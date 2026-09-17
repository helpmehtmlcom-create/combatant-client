/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.WorldRenderState;
import net.minecraft.resources.Identifier;

/** Artist-facing celestial energy and high-frequency sky appearance policy. */
public record CelestialRenderProfile(
        Identifier id,
        float sunRadianceRed,
        float sunRadianceGreen,
        float sunRadianceBlue,
        float sunAngularRadiusRadians,
        float sunDiskRadianceScale,
        float sunLimbDarkening,
        float moonRadianceRed,
        float moonRadianceGreen,
        float moonRadianceBlue,
        float moonAngularRadiusRadians,
        float moonDiskRadianceScale,
        float moonEarthshine,
        float moonSurfacePhaseExponent,
        float moonDirectPhaseFloor,
        float moonDirectPhaseExponent,
        float starIntensity,
        float starDensity,
        float starSize,
        float starTwinkleStrength,
        float starTwinkleSpeed,
        float starWarmRed,
        float starWarmGreen,
        float starWarmBlue,
        float starCoolRed,
        float starCoolGreen,
        float starCoolBlue,
        float starHorizonFadeStart,
        float starHorizonFadeEnd,
        boolean valid
) {
    public CelestialRenderProfile {
        if (id == null) id = WorldRenderState.NONE;
        sunRadianceRed = nonNegative(sunRadianceRed);
        sunRadianceGreen = nonNegative(sunRadianceGreen);
        sunRadianceBlue = nonNegative(sunRadianceBlue);
        sunAngularRadiusRadians = nonNegative(sunAngularRadiusRadians);
        sunDiskRadianceScale = nonNegative(sunDiskRadianceScale);
        sunLimbDarkening = clamp01(sunLimbDarkening);
        moonRadianceRed = nonNegative(moonRadianceRed);
        moonRadianceGreen = nonNegative(moonRadianceGreen);
        moonRadianceBlue = nonNegative(moonRadianceBlue);
        moonAngularRadiusRadians = nonNegative(moonAngularRadiusRadians);
        moonDiskRadianceScale = nonNegative(moonDiskRadianceScale);
        moonEarthshine = clamp01(moonEarthshine);
        moonSurfacePhaseExponent = Math.max(0.01f, finite(moonSurfacePhaseExponent, 1.0f));
        moonDirectPhaseFloor = clamp01(moonDirectPhaseFloor);
        moonDirectPhaseExponent = Math.max(0.01f, finite(moonDirectPhaseExponent, 1.0f));
        starIntensity = nonNegative(starIntensity);
        starDensity = clamp01(starDensity);
        starSize = Math.max(0.001f, finite(starSize, 0.08f));
        starTwinkleStrength = clamp01(starTwinkleStrength);
        starTwinkleSpeed = nonNegative(starTwinkleSpeed);
        starWarmRed = nonNegative(starWarmRed);
        starWarmGreen = nonNegative(starWarmGreen);
        starWarmBlue = nonNegative(starWarmBlue);
        starCoolRed = nonNegative(starCoolRed);
        starCoolGreen = nonNegative(starCoolGreen);
        starCoolBlue = nonNegative(starCoolBlue);
        starHorizonFadeStart = finite(starHorizonFadeStart, 0.0f);
        starHorizonFadeEnd = Math.max(starHorizonFadeStart + 1.0e-4f, finite(starHorizonFadeEnd, 0.08f));
        valid = valid && !WorldRenderState.NONE.equals(id);
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
