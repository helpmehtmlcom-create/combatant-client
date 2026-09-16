/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import net.minecraft.resources.Identifier;

/** Parameterized cloud-family shape. It describes formation behaviour, not a texture preset. */
public record CloudFamilyProfile(
        Identifier id,
        float bottomProfileExponent,
        float topProfileExponent,
        float verticalDevelopment,
        float anvilTendency,
        float horizontalScaleMultiplier,
        float verticalScaleMultiplier,
        float erosionStrength,
        float detailStrength
) {
    public CloudFamilyProfile {
        if (id == null) id = Identifier.fromNamespaceAndPath("combatant", "unknown_cloud_family");
        bottomProfileExponent = clamp(finite(bottomProfileExponent, 1.0f), 0.25f, 8.0f);
        topProfileExponent = clamp(finite(topProfileExponent, 1.0f), 0.25f, 8.0f);
        verticalDevelopment = clamp(finite(verticalDevelopment, 0.0f), 0.0f, 4.0f);
        anvilTendency = clamp(finite(anvilTendency, 0.0f), 0.0f, 1.0f);
        horizontalScaleMultiplier = clamp(finite(horizontalScaleMultiplier, 1.0f), 0.1f, 8.0f);
        verticalScaleMultiplier = clamp(finite(verticalScaleMultiplier, 1.0f), 0.1f, 8.0f);
        erosionStrength = clamp(finite(erosionStrength, 0.5f), 0.0f, 1.0f);
        detailStrength = clamp(finite(detailStrength, 0.5f), 0.0f, 1.0f);
    }

    private static float finite(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
