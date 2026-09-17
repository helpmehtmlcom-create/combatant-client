/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import combatant.client.render.engine.world.WorldRenderState;
import combatant.client.render.engine.world.environment.AtmosphereModel;
import combatant.client.render.engine.world.environment.AtmosphereModelRegistry;
import combatant.client.render.engine.world.environment.AtmosphereState;
import combatant.client.render.engine.world.environment.BiomeClimateSampler;
import combatant.client.render.engine.world.environment.BiomeClimateState;
import combatant.client.render.engine.world.environment.CelestialModel;
import combatant.client.render.engine.world.environment.CelestialModelRegistry;
import combatant.client.render.engine.world.environment.CelestialState;
import combatant.client.render.engine.world.environment.DimensionRenderProfile;
import combatant.client.render.engine.world.environment.DimensionRenderProfileRegistry;
import combatant.client.render.engine.world.environment.EnvironmentCaptureContext;
import combatant.client.render.engine.world.environment.WeatherProvider;
import combatant.client.render.engine.world.environment.WeatherProviderRegistry;
import combatant.client.render.engine.world.environment.WeatherState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Builds one explicit renderer-world contract before graph consumers execute. */
final class DeferredWorldRenderStateSource {
    private final BiomeClimateSampler biomeClimate = new BiomeClimateSampler();

    private Object worldOwner;
    private long epoch;
    private WorldRenderState current = WorldRenderState.unknown(0L);

    WorldRenderState capture(ClientLevel level, DeferredPrimaryViewSource.FrameView view) {
        if (worldOwner != level) {
            worldOwner = level;
            epoch++;
            biomeClimate.reset();
            WeatherProviderRegistry.resetAll();
            AtmosphereModelRegistry.resetAll();
            CelestialModelRegistry.resetAll();
        }
        if (level == null) {
            current = WorldRenderState.unknown(epoch);
            return current;
        }

        Identifier dimensionKey = level.dimension().identifier();
        DimensionRenderProfile profile = DimensionRenderProfileRegistry.resolve(level.dimension());
        Vec3 camera = view != null ? view.cameraPosition() : Vec3.ZERO;
        float partialTick = partialTick();
        long frameId = view != null ? view.frameId() : Long.MIN_VALUE;

        BiomeClimateState climate = biomeClimate.capture(level, camera);

        WeatherState weather = WeatherState.NONE;
        WeatherProvider weatherProvider = WeatherProviderRegistry.resolve(profile.weatherProvider());
        if (weatherProvider != null) {
            try {
                weather = weatherProvider.capture(level, camera, partialTick, climate, frameId);
            } catch (Throwable ignored) {
                weather = WeatherState.NONE;
            }
        }

        Camera minecraftCamera = mainCamera(level);
        EnvironmentCaptureContext environmentContext = new EnvironmentCaptureContext(
                level, minecraftCamera, camera, partialTick, climate, weather, frameId
        );

        CelestialState celestial = CelestialState.NONE;
        CelestialModel celestialModel = CelestialModelRegistry.resolve(profile.celestialModel());
        if (celestialModel != null) {
            try {
                celestial = celestialModel.capture(environmentContext);
            } catch (Throwable ignored) {
                celestial = CelestialState.NONE;
            }
        }

        AtmosphereState atmosphere = AtmosphereState.NONE;
        AtmosphereModel atmosphereModel = AtmosphereModelRegistry.resolve(profile.environmentModel());
        if (atmosphereModel != null) {
            try {
                atmosphere = atmosphereModel.capture(environmentContext);
            } catch (Throwable ignored) {
                atmosphere = AtmosphereState.NONE;
            }
        }

        DirectionalLightDescriptor directional = celestial.valid()
                ? celestial.primaryDirectionalLight()
                : DirectionalLightDescriptor.NONE;
        if (directional.valid() && atmosphere.valid() && atmosphereModel != null) {
            directional = atmosphereModel.attenuateDirectLight(atmosphere, directional, camera.y);
        }

        current = new WorldRenderState(
                dimensionKey,
                profile.id(),
                profile.environmentModel(),
                profile.skyProvider(),
                profile.celestialModel(),
                profile.mediumProfile(),
                profile.weatherProvider(),
                profile.cloudProfile(),
                profile.ambientPalette(),
                profile.exposureProfile(),
                profile.postProfile(),
                WorldRenderState.NONE,
                atmosphere,
                climate,
                celestial,
                weather,
                directional,
                epoch
        );
        return current;
    }

    WorldRenderState current() {
        return current;
    }

    void reset() {
        worldOwner = null;
        epoch++;
        biomeClimate.reset();
        WeatherProviderRegistry.resetAll();
        AtmosphereModelRegistry.resetAll();
        CelestialModelRegistry.resetAll();
        current = WorldRenderState.unknown(epoch);
    }

    private static Camera mainCamera(ClientLevel expectedLevel) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null) return null;
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return null;
        if (camera.entity() != null && camera.entity().level() != expectedLevel) return null;
        return camera;
    }

    private static float partialTick() {
        Minecraft minecraft = Minecraft.getInstance();
        DeltaTracker tracker = minecraft != null ? minecraft.getDeltaTracker() : null;
        if (tracker == null) return 0.0f;
        float value = tracker.getGameTimeDeltaPartialTick(true);
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.0f;
    }
}
