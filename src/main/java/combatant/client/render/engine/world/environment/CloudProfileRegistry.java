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

/** Data-driven cloud profiles. Selection is explicit and never inferred from rendered state. */
public final class CloudProfileRegistry {
    private static final Map<Identifier, CloudProfile> PROFILES = new ConcurrentHashMap<>();

    static {
        register(new CloudProfile(
                DimensionRenderProfileRegistry.OVERWORLD_CLOUDS,
                java.util.List.of(new CloudLayerProfile(
                        128.0f, 224.0f, 1.0f, 0.0f,
                        640.0f, 72.0f, 0.45f, 0.45f,
                        0.985f, 0.55f, 0.35f, 0.5f,
                        1.0f, 0.75f, 0.5f, 0.035f,
                        0.16f, 0.68f, 0.82f, 0.22f
                )),
                1536.0f,
                0.08f,
                14.0f,
                true
        ));
    }

    private CloudProfileRegistry() {
    }

    public static void register(CloudProfile profile) {
        if (profile == null || profile.id() == null) return;
        PROFILES.put(profile.id(), profile);
    }

    public static CloudProfile resolve(Identifier id) {
        if (id == null) return CloudProfile.NONE;
        return PROFILES.getOrDefault(id, CloudProfile.NONE);
    }
}
