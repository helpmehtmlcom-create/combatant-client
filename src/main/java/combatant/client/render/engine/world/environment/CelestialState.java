/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import net.minecraft.resources.Identifier;

/** Renderer-owned celestial state. Producer-owned environment attributes remain explicit facts. */
public record CelestialState(
        Identifier modelId,
        double worldClockTicks,
        float dayFraction,
        float sunAngleRadians,
        float moonAngleRadians,
        float starAngleRadians,
        float starBrightness,
        DirectionalLightDescriptor sun,
        DirectionalLightDescriptor moon,
        DirectionalLightDescriptor primaryDirectionalLight,
        int moonPhase,
        boolean environmentAttributesValid,
        boolean valid
) {
    public static final CelestialState NONE = new CelestialState(
            Identifier.fromNamespaceAndPath("combatant", "none"), 0.0, 0.0f,
            0.0f, 0.0f, 0.0f, 0.0f,
            DirectionalLightDescriptor.NONE, DirectionalLightDescriptor.NONE,
            DirectionalLightDescriptor.NONE, 0, false, false
    );

    public CelestialState {
        if (modelId == null) modelId = NONE.modelId;
        if (!Double.isFinite(worldClockTicks)) worldClockTicks = 0.0;
        dayFraction = Float.isFinite(dayFraction) ? dayFraction - (float) Math.floor(dayFraction) : 0.0f;
        sunAngleRadians = finiteAngle(sunAngleRadians);
        moonAngleRadians = finiteAngle(moonAngleRadians);
        starAngleRadians = finiteAngle(starAngleRadians);
        starBrightness = clamp01(starBrightness);
        if (sun == null) sun = DirectionalLightDescriptor.NONE;
        if (moon == null) moon = DirectionalLightDescriptor.NONE;
        if (primaryDirectionalLight == null) primaryDirectionalLight = DirectionalLightDescriptor.NONE;
        moonPhase = Math.floorMod(moonPhase, 8);
    }

    private static float finiteAngle(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
