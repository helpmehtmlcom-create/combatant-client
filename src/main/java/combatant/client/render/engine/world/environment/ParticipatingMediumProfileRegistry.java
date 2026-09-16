/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.WorldRenderState;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit medium-profile registry. Unknown identifiers resolve to a neutral no-medium contract. */
public final class ParticipatingMediumProfileRegistry {
    public static final Identifier OVERWORLD = id("overworld_medium");

    private static final Map<Identifier, ParticipatingMediumProfile> PROFILES = new ConcurrentHashMap<>();

    static {
        register(new ParticipatingMediumProfile(
                OVERWORLD,
                1536.0f,
                64.0f,
                96.0f,
                0.0f,
                0.65f,
                0.30f,
                0.80f,
                0.25f,
                3.4e-4f,
                3.6e-4f,
                3.8e-4f,
                2.0e-5f,
                2.0e-5f,
                2.0e-5f,
                0.55f,
                0.35f,
                1.0f,
                true
        ));
    }

    private ParticipatingMediumProfileRegistry() {
    }

    public static void register(ParticipatingMediumProfile profile) {
        if (profile == null || profile.id() == null || WorldRenderState.NONE.equals(profile.id())) return;
        PROFILES.put(profile.id(), profile);
    }

    public static ParticipatingMediumProfile resolve(Identifier id) {
        if (id == null || WorldRenderState.NONE.equals(id) || WorldRenderState.NEUTRAL.equals(id)) {
            return ParticipatingMediumProfile.NONE;
        }
        return PROFILES.getOrDefault(id, ParticipatingMediumProfile.NONE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
