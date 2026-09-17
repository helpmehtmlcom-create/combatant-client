/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Celestial art-policy registry, independent from model selection and shader implementation. */
public final class CelestialRenderProfileRegistry {
    private static final Map<Identifier, CelestialRenderProfile> PROFILES = new ConcurrentHashMap<>();

    static {
        register(new CelestialRenderProfile(
                DimensionRenderProfileRegistry.OVERWORLD_CELESTIAL,
                1.0f, 1.0f, 1.0f, 0.00465f, 1.0f, 0.52f,
                0.0020f, 0.0021f, 0.0024f, 0.00436f, 1.0f, 0.035f, 0.85f,
                0.01f, 1.15f,
                0.018f, 0.018f, 0.105f, 0.10f, 0.65f,
                1.00f, 0.78f, 0.60f,
                0.68f, 0.80f, 1.00f,
                0.015f, 0.10f,
                true
        ));
    }

    private CelestialRenderProfileRegistry() {
    }

    public static void register(CelestialRenderProfile profile) {
        if (profile == null || profile.id() == null) return;
        PROFILES.put(profile.id(), profile);
    }

    public static CelestialRenderProfile resolve(Identifier id) {
        return id == null ? null : PROFILES.get(id);
    }
}
