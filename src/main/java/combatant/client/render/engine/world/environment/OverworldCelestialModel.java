/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

/** Neutral Overworld orbital model. Atmosphere owns spectral tinting; this model owns geometry/energy. */
public final class OverworldCelestialModel {
    private static final Identifier ID = DimensionRenderProfileRegistry.OVERWORLD_CELESTIAL;
    private static final double DAY_TICKS = 24000.0;
    private static final float SUN_ANGULAR_RADIUS = 0.00465f;
    private static final float MOON_ANGULAR_RADIUS = 0.00436f;
    private static final float SUN_RADIANCE = 1.0f;
    private static final float MOON_RADIANCE = 0.002f;

    private OverworldCelestialModel() {
    }

    public static CelestialState capture(ClientLevel level, float partialTick) {
        if (level == null) return CelestialState.NONE;
        double clock = level.getOverworldClockTime() + clamp01(partialTick);
        double dayTime = floorMod(clock, DAY_TICKS);
        float fraction = (float) (dayTime / DAY_TICKS);
        double phase = fraction * Math.PI * 2.0;

        // Combatant convention: tick 0 sunrise, 6000 zenith, 12000 sunset, 18000 midnight.
        float sunX = (float) Math.cos(phase);
        float sunY = (float) Math.sin(phase);
        float sunZ = 0.0f;
        float moonX = -sunX;
        float moonY = -sunY;
        float moonZ = -sunZ;

        boolean sunAboveHorizon = sunY > 0.0f;
        boolean moonAboveHorizon = moonY > 0.0f;
        // Celestial geometry remains valid below the horizon so atmosphere integration can produce
        // continuous twilight. Only the primary direct-light/shadow contract is horizon-clipped.
        DirectionalLightDescriptor sun = DirectionalLightDescriptor.of(
                sunX, sunY, sunZ,
                SUN_RADIANCE, SUN_RADIANCE, SUN_RADIANCE, SUN_ANGULAR_RADIUS, sunAboveHorizon
        );
        DirectionalLightDescriptor moon = DirectionalLightDescriptor.of(
                moonX, moonY, moonZ,
                MOON_RADIANCE, MOON_RADIANCE, MOON_RADIANCE, MOON_ANGULAR_RADIUS, moonAboveHorizon
        );
        DirectionalLightDescriptor primary = sunAboveHorizon ? sun : (moonAboveHorizon ? moon : DirectionalLightDescriptor.NONE);
        long dayIndex = (long) Math.floor(clock / DAY_TICKS);
        int moonPhase = (int) Math.floorMod(dayIndex, 8L);
        return new CelestialState(ID, clock, fraction, sun, moon, primary, moonPhase, true);
    }

    private static double floorMod(double value, double modulus) {
        return value - Math.floor(value / modulus) * modulus;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
