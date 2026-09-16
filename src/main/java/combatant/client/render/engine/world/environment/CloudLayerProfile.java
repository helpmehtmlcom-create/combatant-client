/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

/** Numerical description of one cloud density layer. Visual styles can replace these values. */
public record CloudLayerProfile(
        float baseAltitudeBlocks,
        float topAltitudeBlocks,
        float densityScale,
        float coverageBias,
        float macroScaleBlocks,
        float detailScaleBlocks,
        float erosion,
        float anisotropy,
        float humidityResponse,
        float stormResponse,
        float frontResponse,
        float extinctionPerBlock,
        float bottomFadeFraction,
        float topFadeStartFraction,
        float clearCoverageThreshold,
        float overcastCoverageThreshold
) {
    public CloudLayerProfile {
        baseAltitudeBlocks = finite(baseAltitudeBlocks, 128.0f);
        topAltitudeBlocks = Math.max(baseAltitudeBlocks + 1.0f, finite(topAltitudeBlocks, baseAltitudeBlocks + 64.0f));
        densityScale = clamp(finite(densityScale, 1.0f), 0.0f, 8.0f);
        coverageBias = clamp(finite(coverageBias, 0.0f), -1.0f, 1.0f);
        macroScaleBlocks = clamp(finite(macroScaleBlocks, 512.0f), 8.0f, 8192.0f);
        detailScaleBlocks = clamp(finite(detailScaleBlocks, 64.0f), 2.0f, macroScaleBlocks);
        erosion = clamp(finite(erosion, 0.5f), 0.0f, 1.0f);
        anisotropy = clamp(finite(anisotropy, 0.45f), -0.9f, 0.9f);
        humidityResponse = clamp(finite(humidityResponse, 1.0f), 0.0f, 4.0f);
        stormResponse = clamp(finite(stormResponse, 0.5f), 0.0f, 4.0f);
        frontResponse = clamp(finite(frontResponse, 0.5f), 0.0f, 4.0f);
        extinctionPerBlock = clamp(finite(extinctionPerBlock, 0.04f), 0.0001f, 2.0f);
        bottomFadeFraction = clamp(finite(bottomFadeFraction, 0.15f), 0.0f, 0.95f);
        topFadeStartFraction = clamp(finite(topFadeStartFraction, 0.7f), bottomFadeFraction, 1.0f);
        clearCoverageThreshold = clamp(finite(clearCoverageThreshold, 0.82f), 0.0f, 1.0f);
        overcastCoverageThreshold = clamp(finite(overcastCoverageThreshold, 0.22f), 0.0f, 1.0f);
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
