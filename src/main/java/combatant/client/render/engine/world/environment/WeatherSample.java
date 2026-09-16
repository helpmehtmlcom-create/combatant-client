/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

/** One spatial sample produced by a registered weather model. */
public record WeatherSample(
        int blockX,
        int blockY,
        int blockZ,
        float biomeTemperature,
        float humidity,
        float pressureAnomaly,
        float stormPotential,
        float precipitationIntensity,
        PrecipitationKind precipitation,
        float windXBlocksPerSecond,
        float windYBlocksPerSecond,
        float windZBlocksPerSecond,
        float frontStrength,
        float frontVelocityXBlocksPerSecond,
        float frontVelocityZBlocksPerSecond,
        SurfaceDepositionKind deposition,
        float depositionIntensity,
        boolean valid
) {
    public static final WeatherSample UNKNOWN = new WeatherSample(
            0, 0, 0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, PrecipitationKind.NONE,
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, SurfaceDepositionKind.NONE, 0.0f, false
    );

    public WeatherSample {
        biomeTemperature = finite(biomeTemperature);
        humidity = clamp01(humidity);
        pressureAnomaly = clampSigned(pressureAnomaly);
        stormPotential = clamp01(stormPotential);
        precipitationIntensity = clamp01(precipitationIntensity);
        if (precipitation == null) precipitation = PrecipitationKind.NONE;
        windXBlocksPerSecond = finite(windXBlocksPerSecond);
        windYBlocksPerSecond = finite(windYBlocksPerSecond);
        windZBlocksPerSecond = finite(windZBlocksPerSecond);
        frontStrength = clamp01(frontStrength);
        frontVelocityXBlocksPerSecond = finite(frontVelocityXBlocksPerSecond);
        frontVelocityZBlocksPerSecond = finite(frontVelocityZBlocksPerSecond);
        if (deposition == null) deposition = SurfaceDepositionKind.NONE;
        depositionIntensity = clamp01(depositionIntensity);
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, finite(value)));
    }

    private static float clampSigned(float value) {
        return Math.max(-1.0f, Math.min(1.0f, finite(value)));
    }
}
