/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

/**
 * Atmospheric cloud domain. Envelope altitudes bound traversal only; actual base/top are spatial
 * fields derived from weather and family parameters.
 */
public record CloudDomainProfile(
        CloudDomainGroup group,
        CloudFamilyProfile family,
        float minimumAltitudeBlocks,
        float maximumAltitudeBlocks,
        float meanBaseAltitudeBlocks,
        float baseVariationBlocks,
        float meanThicknessBlocks,
        float thicknessVariationBlocks,
        float convectiveThicknessBoostBlocks,
        float densityScale,
        float coverageBias,
        float macroScaleBlocks,
        float detailScaleBlocks,
        float anisotropy,
        float singleScatteringAlbedo,
        float multiScatteringEnergy,
        float multiScatteringExtinctionFactor,
        float multiScatteringAnisotropyFactor,
        float humidityResponse,
        float stormResponse,
        float frontResponse,
        float extinctionPerBlock,
        float clearCoverageThreshold,
        float overcastCoverageThreshold,
        float baseWindMultiplier,
        float topWindMultiplier,
        float windShear,
        float detailAdvectionMultiplier,
        float coarseDensityThreshold,
        float lightingDetailFraction
) {
    public CloudDomainProfile {
        if (group == null) group = CloudDomainGroup.LOW_MID;
        if (family == null) throw new IllegalArgumentException("family");
        minimumAltitudeBlocks = finite(minimumAltitudeBlocks, 96.0f);
        maximumAltitudeBlocks = Math.max(minimumAltitudeBlocks + 1.0f,
                finite(maximumAltitudeBlocks, minimumAltitudeBlocks + 128.0f));
        meanBaseAltitudeBlocks = clamp(finite(meanBaseAltitudeBlocks, minimumAltitudeBlocks),
                minimumAltitudeBlocks, maximumAltitudeBlocks - 1.0f);
        baseVariationBlocks = clamp(finite(baseVariationBlocks, 0.0f), 0.0f,
                maximumAltitudeBlocks - minimumAltitudeBlocks);
        meanThicknessBlocks = clamp(finite(meanThicknessBlocks, 64.0f), 1.0f,
                maximumAltitudeBlocks - minimumAltitudeBlocks);
        thicknessVariationBlocks = clamp(finite(thicknessVariationBlocks, 0.0f), 0.0f,
                maximumAltitudeBlocks - minimumAltitudeBlocks);
        convectiveThicknessBoostBlocks = clamp(finite(convectiveThicknessBoostBlocks, 0.0f), 0.0f,
                maximumAltitudeBlocks - minimumAltitudeBlocks);
        densityScale = clamp(finite(densityScale, 1.0f), 0.0f, 8.0f);
        coverageBias = clamp(finite(coverageBias, 0.0f), -1.0f, 1.0f);
        macroScaleBlocks = clamp(finite(macroScaleBlocks, 512.0f), 16.0f, 16384.0f);
        detailScaleBlocks = clamp(finite(detailScaleBlocks, 64.0f), 2.0f, macroScaleBlocks);
        anisotropy = clamp(finite(anisotropy, 0.45f), -0.9f, 0.9f);
        singleScatteringAlbedo = clamp(finite(singleScatteringAlbedo, 0.985f), 0.0f, 1.0f);
        multiScatteringEnergy = clamp(finite(multiScatteringEnergy, 0.55f), 0.0f, 0.98f);
        multiScatteringExtinctionFactor = clamp(finite(multiScatteringExtinctionFactor, 0.35f), 0.01f, 1.0f);
        multiScatteringAnisotropyFactor = clamp(finite(multiScatteringAnisotropyFactor, 0.5f), 0.0f, 1.0f);
        humidityResponse = clamp(finite(humidityResponse, 1.0f), 0.0f, 4.0f);
        stormResponse = clamp(finite(stormResponse, 0.5f), 0.0f, 4.0f);
        frontResponse = clamp(finite(frontResponse, 0.5f), 0.0f, 4.0f);
        extinctionPerBlock = clamp(finite(extinctionPerBlock, 0.04f), 0.0001f, 2.0f);
        clearCoverageThreshold = clamp(finite(clearCoverageThreshold, 0.82f), 0.0f, 1.0f);
        overcastCoverageThreshold = clamp(finite(overcastCoverageThreshold, 0.22f), 0.0f, 1.0f);
        baseWindMultiplier = clamp(finite(baseWindMultiplier, 1.0f), -4.0f, 4.0f);
        topWindMultiplier = clamp(finite(topWindMultiplier, baseWindMultiplier), -4.0f, 4.0f);
        windShear = clamp(finite(windShear, 0.0f), -4.0f, 4.0f);
        detailAdvectionMultiplier = clamp(finite(detailAdvectionMultiplier, 1.0f), 0.0f, 8.0f);
        coarseDensityThreshold = clamp(finite(coarseDensityThreshold, 0.02f), 0.0f, 1.0f);
        lightingDetailFraction = clamp(finite(lightingDetailFraction, 0.25f), 0.0f, 1.0f);
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
