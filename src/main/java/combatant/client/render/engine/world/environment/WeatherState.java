/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import net.minecraft.resources.Identifier;

/** Explicit current weather contract; source/model validity is not inferred by consumers. */
public record WeatherState(
        Identifier providerId,
        WeatherSample camera,
        WeatherFieldState field,
        float globalRainIntensity,
        float globalThunderIntensity,
        long modelSeed,
        long modelTimeTicks,
        boolean valid
) {
    public static final WeatherState NONE = new WeatherState(
            Identifier.fromNamespaceAndPath("combatant", "none"), WeatherSample.UNKNOWN,
            WeatherFieldState.EMPTY, 0.0f, 0.0f, 0L, 0L, false
    );

    public WeatherState {
        if (providerId == null) providerId = NONE.providerId;
        if (camera == null) camera = WeatherSample.UNKNOWN;
        if (field == null) field = WeatherFieldState.EMPTY;
        globalRainIntensity = clamp01(globalRainIntensity);
        globalThunderIntensity = clamp01(globalThunderIntensity);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
