/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Data-driven cloud profiles. Selection is explicit and never inferred from rendered state. */
public final class CloudProfileRegistry {
    private static final Map<Identifier, CloudProfile> PROFILES = new ConcurrentHashMap<>();

    static {
        CloudFamilyProfile stratus = family("stratiform", 0.65f, 0.75f, 0.15f, 0.0f,
                1.4f, 0.65f, 0.28f, 0.35f);
        CloudFamilyProfile cumulus = family("cumuliform", 1.15f, 1.65f, 1.0f, 0.08f,
                0.8f, 1.25f, 0.48f, 0.70f);
        CloudFamilyProfile ice = family("high_ice", 0.80f, 0.90f, 0.2f, 0.35f,
                2.3f, 0.45f, 0.18f, 0.35f);
        CloudFamilyProfile convective = family("deep_convective", 1.25f, 1.9f, 2.2f, 0.8f,
                0.85f, 1.65f, 0.52f, 0.72f);

        register(new CloudProfile(
                DimensionRenderProfileRegistry.OVERWORLD_CLOUDS,
                List.of(
                        domain(CloudDomainGroup.LOW_MID, stratus,
                                96.0f, 320.0f, 144.0f, 36.0f, 86.0f, 54.0f, 24.0f,
                                0.72f, -0.05f, 760.0f, 88.0f, 0.42f,
                                1.0f, 0.58f, 0.42f, 0.032f,
                                1.00f, 0.45f, 0.55f, 0.95f, 1.15f, 0.10f),
                        domain(CloudDomainGroup.LOW_MID, cumulus,
                                112.0f, 440.0f, 152.0f, 52.0f, 118.0f, 92.0f, 180.0f,
                                0.82f, -0.08f, 520.0f, 58.0f, 0.48f,
                                1.0f, 0.82f, 0.58f, 0.038f,
                                0.90f, 0.82f, 0.48f, 0.90f, 1.32f, 0.16f),
                        domain(CloudDomainGroup.HIGH, ice,
                                480.0f, 980.0f, 650.0f, 96.0f, 105.0f, 58.0f, 32.0f,
                                0.30f, -0.12f, 1450.0f, 180.0f, 0.36f,
                                0.65f, 0.35f, 0.30f, 0.018f,
                                0.75f, 0.18f, 0.72f, 1.75f, 2.25f, -0.35f),
                        domain(CloudDomainGroup.CONVECTIVE, convective,
                                104.0f, 1080.0f, 138.0f, 58.0f, 210.0f, 130.0f, 650.0f,
                                0.78f, -0.20f, 900.0f, 70.0f, 0.52f,
                                1.10f, 1.50f, 0.72f, 0.042f,
                                0.95f, 1.75f, 0.75f, 0.85f, 1.85f, 0.22f)
                ),
                2048.0f,
                0.08f,
                18.0f,
                true
        ));
    }

    private CloudProfileRegistry() {
    }

    public static void register(CloudProfile profile) {
        if (profile == null || profile.id() == null) return;
        PROFILES.put(profile.id(), profile);
    }

    public static CloudProfile resolve(Identifier id) {
        if (id == null) return CloudProfile.NONE;
        return PROFILES.getOrDefault(id, CloudProfile.NONE);
    }

    private static CloudFamilyProfile family(String path,
                                             float bottomExponent,
                                             float topExponent,
                                             float verticalDevelopment,
                                             float anvilTendency,
                                             float horizontalScale,
                                             float verticalScale,
                                             float erosion,
                                             float detail) {
        return new CloudFamilyProfile(
                Identifier.fromNamespaceAndPath("combatant", path),
                bottomExponent, topExponent, verticalDevelopment, anvilTendency,
                horizontalScale, verticalScale, erosion, detail
        );
    }

    private static CloudDomainProfile domain(CloudDomainGroup group,
                                             CloudFamilyProfile family,
                                             float minimumAltitude,
                                             float maximumAltitude,
                                             float meanBase,
                                             float baseVariation,
                                             float meanThickness,
                                             float thicknessVariation,
                                             float convectiveBoost,
                                             float density,
                                             float coverageBias,
                                             float macroScale,
                                             float detailScale,
                                             float anisotropy,
                                             float humidityResponse,
                                             float stormResponse,
                                             float frontResponse,
                                             float extinction,
                                             float baseWind,
                                             float topWind,
                                             float windShear,
                                             float detailAdvection,
                                             float coarseThreshold,
                                             float lightingDetail) {
        return new CloudDomainProfile(
                group, family,
                minimumAltitude, maximumAltitude, meanBase, baseVariation,
                meanThickness, thicknessVariation, convectiveBoost,
                density, coverageBias, macroScale, detailScale, anisotropy,
                0.985f, 0.55f, 0.35f, 0.5f,
                humidityResponse, stormResponse, frontResponse, extinction,
                0.82f, 0.22f,
                baseWind, topWind, windShear, detailAdvection,
                coarseThreshold, lightingDetail
        );
    }
}
