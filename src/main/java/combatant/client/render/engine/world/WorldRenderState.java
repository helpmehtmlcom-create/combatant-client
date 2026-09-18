/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import combatant.client.render.engine.world.environment.AtmosphereState;
import combatant.client.render.engine.world.environment.BiomeClimateState;
import combatant.client.render.engine.world.environment.CelestialState;
import combatant.client.render.engine.world.environment.MinecraftBaselineLightState;
import combatant.client.render.engine.world.environment.WeatherState;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Renderer-owned semantic state for one world frame.
 *
 * <p>Consumers receive already-selected providers/profile identifiers and never classify the
 * dimension from fog color, skylight, biome data or rendered pixels.</p>
 */
public record WorldRenderState(
        Identifier dimensionKey,
        Identifier dimensionProfileId,
        Identifier environmentModel,
        Identifier skyProvider,
        Identifier celestialModel,
        Identifier mediumProfile,
        Identifier weatherProvider,
        Identifier cloudProfile,
        Identifier ambientPalette,
        Identifier exposureProfile,
        Identifier postProfile,
        Identifier transitionState,
        AtmosphereState atmosphereState,
        BiomeClimateState biomeClimate,
        CelestialState celestialState,
        WeatherState weatherState,
        MinecraftBaselineLightState baselineLightState,
        DirectionalLightDescriptor directionalLight,
        long epoch
) {
    public static final Identifier UNKNOWN_DIMENSION = id("unknown_dimension");
    public static final Identifier UNKNOWN_PROFILE = id("unknown");
    public static final Identifier NONE = id("none");
    public static final Identifier NEUTRAL = id("neutral");

    public WorldRenderState {
        dimensionKey = Objects.requireNonNullElse(dimensionKey, UNKNOWN_DIMENSION);
        dimensionProfileId = Objects.requireNonNullElse(dimensionProfileId, UNKNOWN_PROFILE);
        environmentModel = Objects.requireNonNullElse(environmentModel, NONE);
        skyProvider = Objects.requireNonNullElse(skyProvider, NONE);
        celestialModel = Objects.requireNonNullElse(celestialModel, NONE);
        mediumProfile = Objects.requireNonNullElse(mediumProfile, NEUTRAL);
        weatherProvider = Objects.requireNonNullElse(weatherProvider, NONE);
        cloudProfile = Objects.requireNonNullElse(cloudProfile, NONE);
        ambientPalette = Objects.requireNonNullElse(ambientPalette, NEUTRAL);
        exposureProfile = Objects.requireNonNullElse(exposureProfile, NEUTRAL);
        postProfile = Objects.requireNonNullElse(postProfile, NEUTRAL);
        transitionState = Objects.requireNonNullElse(transitionState, NONE);
        atmosphereState = Objects.requireNonNullElse(atmosphereState, AtmosphereState.NONE);
        biomeClimate = Objects.requireNonNullElse(biomeClimate, BiomeClimateState.EMPTY);
        celestialState = Objects.requireNonNullElse(celestialState, CelestialState.NONE);
        weatherState = Objects.requireNonNullElse(weatherState, WeatherState.NONE);
        baselineLightState = Objects.requireNonNullElse(baselineLightState, MinecraftBaselineLightState.NEUTRAL);
        directionalLight = Objects.requireNonNullElse(directionalLight, DirectionalLightDescriptor.NONE);
    }

    public static WorldRenderState unknown(long epoch) {
        return new WorldRenderState(
                UNKNOWN_DIMENSION, UNKNOWN_PROFILE,
                NONE, NONE, NONE, NEUTRAL, NONE, NONE,
                NEUTRAL, NEUTRAL, NEUTRAL, NONE,
                AtmosphereState.NONE, BiomeClimateState.EMPTY, CelestialState.NONE, WeatherState.NONE,
                MinecraftBaselineLightState.NEUTRAL, DirectionalLightDescriptor.NONE, epoch
        );
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
