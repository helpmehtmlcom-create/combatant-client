/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import net.minecraft.resources.Identifier;

/** Earth-like Overworld optical model. Numerical/art response comes from a replaceable profile. */
public final class OverworldAtmosphereModel implements AtmosphereModel {
    public static final OverworldAtmosphereModel INSTANCE = new OverworldAtmosphereModel();
    public static final Identifier ID = DimensionRenderProfileRegistry.OVERWORLD_ENVIRONMENT;

    private OverworldAtmosphereModel() {
    }

    @Override
    public Identifier id() {
        return ID;
    }

    /** Compatibility accessor for consumers that only need the configured clear-air baseline. */
    public static AtmosphereState capture() {
        AtmosphereRenderProfile profile = AtmosphereRenderProfileRegistry.resolve(ID);
        return profile != null && profile.valid() ? profile.baseAtmosphere() : AtmosphereState.NONE;
    }

    @Override
    public AtmosphereState capture(EnvironmentCaptureContext context) {
        AtmosphereRenderProfile profile = AtmosphereRenderProfileRegistry.resolve(ID);
        if (profile == null || !profile.valid()) return AtmosphereState.NONE;
        AtmosphereState base = profile.baseAtmosphere();
        if (context == null) return base;

        float biomeHumidity = context.biomeClimate().camera().valid()
                ? context.biomeClimate().camera().humidityBaseline()
                : 0.5f;
        WeatherSample weather = context.weather().camera();
        boolean hasWeather = context.weather().valid() && weather.valid();
        float weatherHumidity = hasWeather ? weather.humidity() : biomeHumidity;

        float biomeWeight = profile.biomeHumidityWeight();
        float weatherWeight = profile.weatherHumidityWeight();
        float totalWeight = biomeWeight + weatherWeight;
        float humidity = totalWeight > 1.0e-6f
                ? (biomeHumidity * biomeWeight + weatherHumidity * weatherWeight) / totalWeight
                : weatherHumidity;
        float humidityResponse = remapAbove(humidity, profile.humidityPivot());
        float precipitation = hasWeather ? weather.precipitationIntensity() : context.weather().globalRainIntensity();
        float storm = hasWeather ? weather.stormPotential() : context.weather().globalThunderIntensity();

        float multiplier = 1.0f
                + humidityResponse * profile.humidityMieGain()
                + precipitation * profile.precipitationMieGain()
                + storm * profile.stormMieGain();
        multiplier = clamp(multiplier, profile.minMieMultiplier(), profile.maxMieMultiplier());
        if (Math.abs(multiplier - 1.0f) <= 1.0e-5f) return base;
        multiplier = quantize(multiplier, profile.minMieMultiplier(), profile.maxMieMultiplier(), profile.aerosolQuantizationSteps());
        return new AtmosphereState(
                base.modelId(),
                base.planetRadiusKm(), base.atmosphereTopKm(),
                base.rayleighRed(), base.rayleighGreen(), base.rayleighBlue(), base.rayleighScaleHeightKm(),
                base.mieScatteringRed() * multiplier,
                base.mieScatteringGreen() * multiplier,
                base.mieScatteringBlue() * multiplier,
                base.mieExtinctionRed() * multiplier,
                base.mieExtinctionGreen() * multiplier,
                base.mieExtinctionBlue() * multiplier,
                base.mieScaleHeightKm(), base.mieAnisotropy(),
                base.ozoneAbsorptionRed(), base.ozoneAbsorptionGreen(), base.ozoneAbsorptionBlue(),
                base.ozoneCenterKm(), base.ozoneWidthKm(),
                base.groundAlbedoRed(), base.groundAlbedoGreen(), base.groundAlbedoBlue(),
                true
        );
    }

    /**
     * CPU-side clear-air direct transmittance used by the directional-light contract. The GPU LUT
     * is authoritative for sky/media rendering; this analytic path keeps direct lighting coupled
     * to the same optical coefficients without a GPU readback.
     */
    @Override
    public DirectionalLightDescriptor attenuateDirectLight(
            AtmosphereState atmosphere,
            DirectionalLightDescriptor light,
            double observerAltitudeBlocks
    ) {
        return attenuate(atmosphere, light, observerAltitudeBlocks);
    }

    public static DirectionalLightDescriptor attenuate(
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

    /** Kept for source compatibility with the pre-registry implementation. */
    public static DirectionalLightDescriptor attenuateDirectional(
            AtmosphereState atmosphere,
            DirectionalLightDescriptor light,
            double observerAltitudeBlocks
    ) {
        return attenuate(atmosphere, light, observerAltitudeBlocks);
    }

    private static float transmittance(float rayleigh, float mie, float ozone,
                                       double airMass, double rayleighDensity, double mieDensity,
                                       AtmosphereState atmosphere) {
        double opticalDepth = airMass * (rayleigh * rayleighDensity * atmosphere.rayleighScaleHeightKm()
                + mie * mieDensity * atmosphere.mieScaleHeightKm()
                + ozone * atmosphere.ozoneWidthKm());
        return (float) Math.exp(-Math.max(0.0, opticalDepth));
    }

    private static float remapAbove(float value, float pivot) {
        if (value <= pivot) return 0.0f;
        return clamp((value - pivot) / Math.max(1.0f - pivot, 1.0e-5f), 0.0f, 1.0f);
    }

    private static float quantize(float value, float minimum, float maximum, int steps) {
        if (steps <= 1 || maximum <= minimum) return value;
        float normalized = clamp((value - minimum) / (maximum - minimum), 0.0f, 1.0f);
        float quantized = Math.round(normalized * steps) / (float) steps;
        return minimum + quantized * (maximum - minimum);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
