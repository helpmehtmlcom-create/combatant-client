/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.world.environment.CloudLayerProfile;
import combatant.client.render.engine.world.environment.CloudProfile;
import combatant.client.render.engine.world.environment.CloudProfileRegistry;
import combatant.client.render.engine.world.environment.WeatherFieldState;
import combatant.client.render.engine.world.environment.WeatherSample;
import combatant.client.render.engine.world.environment.WeatherState;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;

/** Shared GPU-side cloud field contract consumed by cloud radiance and cloud-shadow producers. */
final class DeferredCloudFieldSource implements AutoCloseable {
    static final Std430StructLayout WEATHER_LAYOUT = Std430StructLayout.builder()
            .member("climate", Std430Type.VEC4)
            .member("windFront", Std430Type.VEC4)
            .build();
    static final Std430StructLayout LAYER_LAYOUT = Std430StructLayout.builder()
            .member("altitudeDensity", Std430Type.VEC4)
            .member("scaleShape", Std430Type.VEC4)
            .member("weatherOptics", Std430Type.VEC4)
            .member("coverageShape", Std430Type.VEC4)
            .member("scatteringPolicy", Std430Type.VEC4)
            .build();

    private final DeferredCloudConfig config = DeferredCloudConfig.current();
    private CombatantRhi owner;
    private RhiStorageBuffer weatherData;
    private RhiStorageBuffer layerData;
    private long uploadedFrameId = Long.MIN_VALUE;
    private FrameData uploadedFrame = FrameData.EMPTY;

    FrameData prepareFrame(DeferredPassContext context) {
        ensureOwner(context.rhi());
        ensureBuffers();
        long frameId = context.frame().frameId();
        if (uploadedFrameId == frameId) return uploadedFrame;

        CloudProfile profile = CloudProfileRegistry.resolve(context.worldState().cloudProfile());
        WeatherState weather = context.worldState().weatherState();
        if (weather == null) weather = WeatherState.NONE;
        WeatherFieldState field = weather.field();
        if (field == null) field = WeatherFieldState.EMPTY;
        long requiredWeatherSamples = (long) field.gridWidth() * (long) field.gridDepth();
        boolean weatherFitsCapacity = requiredWeatherSamples > 0L
                && requiredWeatherSamples <= config.maxWeatherSamples();
        boolean active = config.enabled()
                && profile.valid()
                && weather.valid()
                && field.valid()
                && weatherFitsCapacity
                && cloudsEnabledByGame();
        int weatherCount = active ? (int) requiredWeatherSamples : 0;
        int layerCount = active ? Math.min(profile.layers().size(), config.maxLayers()) : 0;
        uploadWeather(field, weatherCount);
        uploadLayers(profile, layerCount);

        uploadedFrame = new FrameData(profile, weather, field, weatherCount, layerCount, active);
        uploadedFrameId = frameId;
        return uploadedFrame;
    }

    RhiStorageBuffer weatherData() {
        ensureBuffers();
        return weatherData;
    }

    RhiStorageBuffer layerData() {
        ensureBuffers();
        return layerData;
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        ensureBuffers();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void uploadWeather(WeatherFieldState field, int count) {
        if (count <= 0) return;
        Std430Writer writer = new Std430Writer(WEATHER_LAYOUT, count);
        for (int i = 0; i < count; i++) {
            WeatherSample sample = field.samples().get(i);
            writer.putVec4(i, "climate", sample.humidity(), sample.pressureAnomaly(),
                            sample.stormPotential(), sample.precipitationIntensity())
                    .putVec4(i, "windFront", sample.windXBlocksPerSecond(), sample.windZBlocksPerSecond(),
                            sample.frontStrength(), sample.valid() ? 1.0f : 0.0f);
        }
        weatherData.upload(writer.buffer(), 0L);
    }

    private void uploadLayers(CloudProfile profile, int count) {
        if (count <= 0) return;
        Std430Writer writer = new Std430Writer(LAYER_LAYOUT, count);
        for (int i = 0; i < count; i++) {
            CloudLayerProfile layer = profile.layers().get(i);
            writer.putVec4(i, "altitudeDensity", layer.baseAltitudeBlocks(), layer.topAltitudeBlocks(),
                            layer.densityScale(), layer.coverageBias())
                    .putVec4(i, "scaleShape", layer.macroScaleBlocks(), layer.detailScaleBlocks(),
                            layer.erosion(), layer.anisotropy())
                    .putVec4(i, "weatherOptics", layer.humidityResponse(), layer.stormResponse(),
                            layer.frontResponse(), layer.extinctionPerBlock())
                    .putVec4(i, "coverageShape", layer.bottomFadeFraction(), layer.topFadeStartFraction(),
                            layer.clearCoverageThreshold(), layer.overcastCoverageThreshold())
                    .putVec4(i, "scatteringPolicy", layer.singleScatteringAlbedo(), layer.multiScatteringEnergy(),
                            layer.multiScatteringExtinctionFactor(), layer.multiScatteringAnisotropyFactor());
        }
        layerData.upload(writer.buffer(), 0L);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
        uploadedFrameId = Long.MIN_VALUE;
        uploadedFrame = FrameData.EMPTY;
    }

    private void ensureBuffers() {
        if (owner == null) throw new IllegalStateException("Cloud field has no RHI owner");
        if (weatherData == null) weatherData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-cloud-weather-data", WEATHER_LAYOUT, config.maxWeatherSamples(), StorageAccess.READ_ONLY, false
        ));
        if (layerData == null) layerData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-cloud-layer-data", LAYER_LAYOUT, config.maxLayers(), StorageAccess.READ_ONLY, false
        ));
    }

    private void closeOwned() {
        close(weatherData); weatherData = null;
        close(layerData); layerData = null;
        uploadedFrameId = Long.MIN_VALUE;
        uploadedFrame = FrameData.EMPTY;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static boolean cloudsEnabledByGame() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.options != null && minecraft.options.getCloudStatus() != CloudStatus.OFF;
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Throwable ignored) { }
    }

    record FrameData(
            CloudProfile profile,
            WeatherState weather,
            WeatherFieldState field,
            int weatherCount,
            int layerCount,
            boolean active
    ) {
        static final FrameData EMPTY = new FrameData(
                CloudProfile.NONE, WeatherState.NONE, WeatherFieldState.EMPTY, 0, 0, false
        );
    }
}
