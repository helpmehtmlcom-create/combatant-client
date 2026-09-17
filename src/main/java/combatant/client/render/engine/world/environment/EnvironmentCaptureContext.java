/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Exact per-frame inputs shared by registered environment/celestial producers. */
public record EnvironmentCaptureContext(
        ClientLevel level,
        Camera camera,
        Vec3 cameraPosition,
        float partialTick,
        BiomeClimateState biomeClimate,
        WeatherState weather,
        long frameId
) {
    public EnvironmentCaptureContext {
        cameraPosition = cameraPosition == null ? Vec3.ZERO : cameraPosition;
        partialTick = Float.isFinite(partialTick) ? Math.max(0.0f, Math.min(1.0f, partialTick)) : 0.0f;
        biomeClimate = biomeClimate == null ? BiomeClimateState.EMPTY : biomeClimate;
        weather = weather == null ? WeatherState.NONE : weather;
    }
}
