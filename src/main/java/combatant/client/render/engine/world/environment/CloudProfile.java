/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.WorldRenderState;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Explicit cloud-domain contract selected by the dimension profile. */
public record CloudProfile(
        Identifier id,
        List<CloudLayerProfile> layers,
        float maxRayDistanceBlocks,
        float ambientResponse,
        float lightSampleDistanceBlocks,
        boolean valid
) {
    public static final CloudProfile NONE = new CloudProfile(
            WorldRenderState.NONE, List.of(), 0.0f, 0.0f, 1.0f, false
    );

    public CloudProfile {
        if (id == null) id = WorldRenderState.NONE;
        layers = layers == null ? List.of() : List.copyOf(layers);
        maxRayDistanceBlocks = finiteNonNegative(maxRayDistanceBlocks);
        ambientResponse = clamp(finite(ambientResponse, 0.0f), 0.0f, 4.0f);
        lightSampleDistanceBlocks = clamp(finite(lightSampleDistanceBlocks, 1.0f), 0.25f, 256.0f);
        valid = valid && !layers.isEmpty() && maxRayDistanceBlocks > 0.0f;
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
