/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import combatant.client.render.engine.world.WorldRenderState;
import combatant.client.render.engine.world.environment.AtmosphereState;
import combatant.client.render.engine.world.environment.BiomeClimateSampler;
import combatant.client.render.engine.world.environment.BiomeClimateState;
import combatant.client.render.engine.world.environment.CelestialState;
import combatant.client.render.engine.world.environment.DimensionRenderProfile;
import combatant.client.render.engine.world.environment.DimensionRenderProfileRegistry;
import combatant.client.render.engine.world.environment.OverworldAtmosphereModel;
import combatant.client.render.engine.world.environment.OverworldCelestialModel;
import combatant.client.render.engine.world.environment.WeatherProvider;
import combatant.client.render.engine.world.environment.WeatherProviderRegistry;
import combatant.client.render.engine.world.environment.WeatherState;
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
        AtmosphereState atmosphere = DimensionRenderProfileRegistry.OVERWORLD_ENVIRONMENT.equals(profile.environmentModel())
                ? OverworldAtmosphereModel.capture()
                : AtmosphereState.NONE;
        CelestialState celestial = DimensionRenderProfileRegistry.OVERWORLD_CELESTIAL.equals(profile.celestialModel())
                ? OverworldCelestialModel.capture(level, partialTick)
                : CelestialState.NONE;

        WeatherState weather = WeatherState.NONE;
        WeatherProvider weatherProvider = WeatherProviderRegistry.resolve(profile.weatherProvider());
        if (weatherProvider != null) {
            try {
                weather = weatherProvider.capture(level, camera, partialTick, climate, frameId);
            } catch (Throwable ignored) {
                weather = WeatherState.NONE;
            }
        }

        DirectionalLightDescriptor directional = celestial.valid()
                ? celestial.primaryDirectionalLight()
                : DirectionalLightDescriptor.NONE;
        if (directional.valid() && atmosphere.valid()) {
            directional = OverworldAtmosphereModel.attenuateDirectional(atmosphere, directional, camera.y);
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
        current = WorldRenderState.unknown(epoch);
    }

    private static float partialTick() {
        Minecraft minecraft = Minecraft.getInstance();
        DeltaTracker tracker = minecraft != null ? minecraft.getDeltaTracker() : null;
        if (tracker == null) return 0.0f;
        float value = tracker.getGameTimeDeltaPartialTick(true);
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.0f;
    }
}
