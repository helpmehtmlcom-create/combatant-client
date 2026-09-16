/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import java.util.List;

/** Spatial weather field around the primary camera. */
public record WeatherFieldState(
        List<WeatherSample> samples,
        int gridWidth,
        int gridDepth,
        int spacingBlocks,
        int originBlockX,
        int originBlockZ,
        int sampleBlockY,
        long revision,
        boolean valid
) {
    public static final WeatherFieldState EMPTY = new WeatherFieldState(
            List.of(), 0, 0, 0, 0, 0, 0, 0L, false
    );

    public WeatherFieldState {
        samples = samples == null ? List.of() : List.copyOf(samples);
        gridWidth = Math.max(0, gridWidth);
        gridDepth = Math.max(0, gridDepth);
        spacingBlocks = Math.max(0, spacingBlocks);
    }
}
