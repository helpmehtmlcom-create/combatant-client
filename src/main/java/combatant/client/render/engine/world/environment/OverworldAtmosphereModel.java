/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import net.minecraft.resources.Identifier;

/** Neutral Earth-like optical baseline for the Overworld. No artistic grading lives here. */
public final class OverworldAtmosphereModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("combatant", "overworld_atmosphere");

    // Standard clear-air coefficients near sea level, expressed in km^-1.
    private static final AtmosphereState STATE = new AtmosphereState(
            ID,
            6360.0f, 100.0f,
            0.005802f, 0.013558f, 0.033100f, 8.0f,
            0.003996f, 0.003996f, 0.003996f,
            0.004440f, 0.004440f, 0.004440f, 1.2f, 0.8f,
            0.000650f, 0.001881f, 0.000085f, 25.0f, 15.0f,
            0.10f, 0.10f, 0.10f,
            true
    );

    private OverworldAtmosphereModel() {
    }

    public static AtmosphereState capture() {
        return STATE;
    }

    /**
     * CPU-side clear-air direct transmittance used by the directional-light contract. The GPU LUT
     * is authoritative for sky/media rendering; this analytic path keeps direct lighting coupled
     * to the same optical coefficients without a GPU readback.
     */
    public static DirectionalLightDescriptor attenuateDirectional(
            AtmosphereState atmosphere,
            DirectionalLightDescriptor light,
            double observerAltitudeBlocks
    ) {
        if (atmosphere == null || !atmosphere.valid() || light == null || !light.valid()) return light;
        float mu = Math.max(0.0f, light.directionY());
        if (mu <= 0.0f) return DirectionalLightDescriptor.NONE;

        // Kasten-Young relative optical air mass; stable at the horizon unlike 1/mu.
        double zenithDegrees = Math.toDegrees(Math.acos(Math.max(0.0, Math.min(1.0, mu))));
        double airMass = 1.0 / (mu + 0.50572 * Math.pow(96.07995 - zenithDegrees, -1.6364));
        double altitudeKm = Math.max(0.0, observerAltitudeBlocks * 0.001);
        double rayleighDensity = Math.exp(-altitudeKm / atmosphere.rayleighScaleHeightKm());
        double mieDensity = Math.exp(-altitudeKm / atmosphere.mieScaleHeightKm());

        float tr = transmittance(atmosphere.rayleighRed(), atmosphere.mieExtinctionRed(), atmosphere.ozoneAbsorptionRed(),
                airMass, rayleighDensity, mieDensity, atmosphere);
        float tg = transmittance(atmosphere.rayleighGreen(), atmosphere.mieExtinctionGreen(), atmosphere.ozoneAbsorptionGreen(),
                airMass, rayleighDensity, mieDensity, atmosphere);
        float tb = transmittance(atmosphere.rayleighBlue(), atmosphere.mieExtinctionBlue(), atmosphere.ozoneAbsorptionBlue(),
                airMass, rayleighDensity, mieDensity, atmosphere);

        return DirectionalLightDescriptor.of(
                light.directionX(), light.directionY(), light.directionZ(),
                light.radianceRed() * tr, light.radianceGreen() * tg, light.radianceBlue() * tb,
                light.angularRadiusRadians(), light.castsShadow()
        );
    }

    private static float transmittance(float rayleigh, float mie, float ozone,
                                       double airMass, double rayleighDensity, double mieDensity,
                                       AtmosphereState atmosphere) {
        // Integrals of the exponential/triangular vertical density profiles in the same km units
        // used by the GPU atmosphere LUTs.
        double opticalDepth = airMass * (rayleigh * rayleighDensity * atmosphere.rayleighScaleHeightKm()
                + mie * mieDensity * atmosphere.mieScaleHeightKm()
                + ozone * atmosphere.ozoneWidthKm());
        return (float) Math.exp(-Math.max(0.0, opticalDepth));
    }
}
