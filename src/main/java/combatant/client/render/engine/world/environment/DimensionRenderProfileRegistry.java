/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.WorldRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit stable-key dimension profile registry. No environment inference is performed here. */
public final class DimensionRenderProfileRegistry {
    public static final Identifier OVERWORLD_PROFILE_ID = id("overworld");
    public static final Identifier NETHER_PROFILE_ID = id("nether");
    public static final Identifier END_PROFILE_ID = id("end");
    public static final Identifier OVERWORLD_ENVIRONMENT = id("overworld_environment");
    public static final Identifier OVERWORLD_CELESTIAL = id("overworld_celestial");
    public static final Identifier OVERWORLD_SKY = id("overworld_atmosphere");
    public static final Identifier OVERWORLD_WEATHER = id("overworld_weather");

    private static final DimensionRenderProfile UNKNOWN = new DimensionRenderProfile(
            WorldRenderState.UNKNOWN_PROFILE, WorldRenderState.NONE, WorldRenderState.NONE,
            WorldRenderState.NONE, WorldRenderState.NEUTRAL, WorldRenderState.NONE,
            WorldRenderState.NEUTRAL, WorldRenderState.NEUTRAL, WorldRenderState.NEUTRAL
    );
    private static final Map<Identifier, DimensionRenderProfile> PROFILES = new ConcurrentHashMap<>();

    static {
        register(Level.OVERWORLD, new DimensionRenderProfile(
                OVERWORLD_PROFILE_ID, OVERWORLD_ENVIRONMENT, OVERWORLD_SKY,
                OVERWORLD_CELESTIAL, WorldRenderState.NEUTRAL, OVERWORLD_WEATHER,
                WorldRenderState.NEUTRAL, WorldRenderState.NEUTRAL, WorldRenderState.NEUTRAL
        ));
        register(Level.NETHER, new DimensionRenderProfile(
                NETHER_PROFILE_ID, id("nether_environment"), WorldRenderState.NONE,
                WorldRenderState.NONE, id("nether_medium"), WorldRenderState.NONE,
                WorldRenderState.NEUTRAL, WorldRenderState.NEUTRAL, WorldRenderState.NEUTRAL
        ));
        register(Level.END, new DimensionRenderProfile(
                END_PROFILE_ID, id("end_environment"), WorldRenderState.NONE,
                WorldRenderState.NONE, id("end_medium"), WorldRenderState.NONE,
                WorldRenderState.NEUTRAL, WorldRenderState.NEUTRAL, WorldRenderState.NEUTRAL
        ));
    }

    private DimensionRenderProfileRegistry() {
    }

    public static void register(ResourceKey<Level> dimension, DimensionRenderProfile profile) {
        if (dimension == null || profile == null) return;
        register(dimension.identifier(), profile);
    }

    public static void register(Identifier dimensionKey, DimensionRenderProfile profile) {
        if (dimensionKey == null || profile == null) return;
        PROFILES.put(dimensionKey, profile);
    }

    public static DimensionRenderProfile resolve(ResourceKey<Level> dimension) {
        return dimension == null ? UNKNOWN : resolve(dimension.identifier());
    }

    public static DimensionRenderProfile resolve(Identifier dimensionKey) {
        return dimensionKey == null ? UNKNOWN : PROFILES.getOrDefault(dimensionKey, UNKNOWN);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
