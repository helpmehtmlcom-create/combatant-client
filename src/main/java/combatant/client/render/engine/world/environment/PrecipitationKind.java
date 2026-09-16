/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import net.minecraft.world.level.biome.Biome;

/** Renderer-owned precipitation semantic; never inferred from color or temperature thresholds. */
public enum PrecipitationKind {
    NONE,
    RAIN,
    SNOW;

    public static PrecipitationKind fromMinecraft(Biome.Precipitation precipitation) {
        if (precipitation == Biome.Precipitation.RAIN) return RAIN;
        if (precipitation == Biome.Precipitation.SNOW) return SNOW;
        return NONE;
    }
}
