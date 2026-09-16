/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import net.minecraft.resources.Identifier;

/** Exact biome-owned climate/surface metadata sampled at one world position. */
public record BiomeClimateSample(
        Identifier biomeKey,
        int blockX,
        int blockY,
        int blockZ,
        float baseTemperature,
        float localTemperature,
        boolean localTemperatureValid,
        float downfall,
        boolean downfallValid,
        boolean hasPrecipitation,
        PrecipitationKind precipitation,
        int waterColorRgb,
        int grassColorRgb,
        int foliageColorRgb,
        int dryFoliageColorRgb,
        boolean valid
) {
    public static final BiomeClimateSample UNKNOWN = new BiomeClimateSample(
            Identifier.fromNamespaceAndPath("combatant", "unknown_biome"),
            0, 0, 0, 0.0f, 0.0f, false, 0.0f, false, false, PrecipitationKind.NONE,
            0, 0, 0, 0, false
    );

    public BiomeClimateSample {
        if (biomeKey == null) biomeKey = UNKNOWN.biomeKey;
        if (precipitation == null) precipitation = PrecipitationKind.NONE;
        baseTemperature = finite(baseTemperature);
        localTemperature = finite(localTemperature);
        downfall = clamp01(downfall);
        waterColorRgb &= 0xFFFFFF;
        grassColorRgb &= 0xFFFFFF;
        foliageColorRgb &= 0xFFFFFF;
        dryFoliageColorRgb &= 0xFFFFFF;
    }

    public float effectiveTemperature() {
        return localTemperatureValid ? localTemperature : baseTemperature;
    }

    /** Minecraft downfall is the exact biome humidity/precipitation baseline used by the renderer. */
    public float humidityBaseline() {
        return downfallValid ? downfall : 0.5f;
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.0f;
    }
}
