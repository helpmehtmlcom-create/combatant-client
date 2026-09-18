/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world.environment;

/**
 * Frame-local Minecraft lighting semantics required by the neutral deferred baseline.
 *
 * <p>This is not a sampled vanilla lightmap and it is not a final lighting result. Terrain already
 * carries authoritative block/sky light coordinates in the G-buffer; this state supplies the
 * world/environment factors that turn those coordinates into a stable neutral irradiance baseline.</p>
 */
public record MinecraftBaselineLightState(
        boolean valid,
        boolean hasSkyLight,
        float skyFactor,
        float dimensionAmbient,
        float skyLightR,
        float skyLightG,
        float skyLightB,
        float blockLightR,
        float blockLightG,
        float blockLightB,
        float ambientR,
        float ambientG,
        float ambientB,
        float skyBackgroundR,
        float skyBackgroundG,
        float skyBackgroundB
) {
    public static final MinecraftBaselineLightState NEUTRAL = new MinecraftBaselineLightState(
            false, false, 0.0f, 0.0f,
            1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,
            0.0f, 0.0f, 0.0f,
            0.035f, 0.045f, 0.065f
    );

    public MinecraftBaselineLightState {
        skyFactor = finiteClamp(skyFactor, 0.0f, 4.0f);
        dimensionAmbient = finiteClamp(dimensionAmbient, 0.0f, 1.0f);
        skyLightR = finiteNonNegative(skyLightR);
        skyLightG = finiteNonNegative(skyLightG);
        skyLightB = finiteNonNegative(skyLightB);
        blockLightR = finiteNonNegative(blockLightR);
        blockLightG = finiteNonNegative(blockLightG);
        blockLightB = finiteNonNegative(blockLightB);
        ambientR = finiteNonNegative(ambientR);
        ambientG = finiteNonNegative(ambientG);
        ambientB = finiteNonNegative(ambientB);
        skyBackgroundR = finiteNonNegative(skyBackgroundR);
        skyBackgroundG = finiteNonNegative(skyBackgroundG);
        skyBackgroundB = finiteNonNegative(skyBackgroundB);
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }

    private static float finiteClamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
