/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import net.minecraft.resources.Identifier;

/**
 * Typed eye-adaptation policy selected by {@code WorldRenderState.exposureProfile()}.
 *
 * <p>EV values are scene-referred relative to middle grey (0.18). Brighten rate is used when
 * exposure must increase for a darker scene; darken rate is used when exposure must decrease for
 * a brighter scene.</p>
 */
public record ExposureProfile(
        Identifier id,
        float minEv,
        float maxEv,
        float lowPercentile,
        float highPercentile,
        float targetPercentile,
        MeteringPolicy meteringPolicy,
        float brightenRate,
        float darkenRate,
        float exposureCompensation,
        ResetPolicy resetPolicy,
        float initialEv,
        WeightingPolicy weightingPolicy,
        float centerWeight,
        float skyWeight
) {
    public ExposureProfile {
        if (id == null) throw new IllegalArgumentException("id");
        minEv = finite(minEv, -8.0f);
        maxEv = finite(maxEv, 16.0f);
        if (maxEv < minEv) {
            float swap = minEv;
            minEv = maxEv;
            maxEv = swap;
        }
        lowPercentile = clamp(finite(lowPercentile, 0.02f), 0.0f, 0.99f);
        highPercentile = clamp(finite(highPercentile, 0.98f), lowPercentile + 0.001f, 1.0f);
        targetPercentile = clamp(finite(targetPercentile, 0.50f), lowPercentile, highPercentile);
        meteringPolicy = meteringPolicy == null ? MeteringPolicy.CLIPPED_LOG_AVERAGE : meteringPolicy;
        brightenRate = Math.max(0.0f, finite(brightenRate, 1.5f));
        darkenRate = Math.max(0.0f, finite(darkenRate, 3.0f));
        exposureCompensation = finite(exposureCompensation, 0.0f);
        resetPolicy = resetPolicy == null ? ResetPolicy.TARGET_IMMEDIATE : resetPolicy;
        initialEv = clamp(finite(initialEv, 0.0f), minEv, maxEv);
        weightingPolicy = weightingPolicy == null ? WeightingPolicy.UNIFORM : weightingPolicy;
        centerWeight = Math.max(0.0f, finite(centerWeight, 1.0f));
        skyWeight = Math.max(0.0f, finite(skyWeight, 1.0f));
    }

    public enum MeteringPolicy {
        CLIPPED_LOG_AVERAGE,
        TARGET_PERCENTILE
    }

    public enum ResetPolicy {
        TARGET_IMMEDIATE,
        FIXED_INITIAL_EV
    }

    public enum WeightingPolicy {
        UNIFORM,
        CENTER_WEIGHTED,
        CENTER_AND_SKY
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
