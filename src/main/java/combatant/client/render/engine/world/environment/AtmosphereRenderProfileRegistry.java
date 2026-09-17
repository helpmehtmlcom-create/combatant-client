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

/** Mutable profile registry so resource/addon layers can replace art policy without shader edits. */
public final class AtmosphereRenderProfileRegistry {
    private static final Map<Identifier, AtmosphereRenderProfile> PROFILES = new ConcurrentHashMap<>();

    static {
        AtmosphereState base = new AtmosphereState(
                OverworldAtmosphereModel.ID,
                6360.0f, 100.0f,
                0.005802f, 0.013558f, 0.033100f, 8.0f,
                0.003996f, 0.003996f, 0.003996f,
                0.004440f, 0.004440f, 0.004440f, 1.2f, 0.8f,
                0.000650f, 0.001881f, 0.000085f, 25.0f, 15.0f,
                0.10f, 0.10f, 0.10f,
                true
        );
        register(new AtmosphereRenderProfile(
                DimensionRenderProfileRegistry.OVERWORLD_ENVIRONMENT,
                base,
                0.25f, 0.75f,
                0.55f,
                0.35f, 0.30f, 0.25f,
                0.85f, 1.80f,
                64,
                true
        ));
    }

    private AtmosphereRenderProfileRegistry() {
    }

    public static void register(AtmosphereRenderProfile profile) {
        if (profile == null || profile.id() == null) return;
        PROFILES.put(profile.id(), profile);
    }

    public static AtmosphereRenderProfile resolve(Identifier id) {
        return id == null ? null : PROFILES.get(id);
    }
}
