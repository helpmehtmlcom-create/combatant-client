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

/** Explicit celestial-model registry. Consumers never infer a celestial model from world traits. */
public final class CelestialModelRegistry {
    private static final Map<Identifier, CelestialModel> MODELS = new ConcurrentHashMap<>();

    static {
        register(OverworldCelestialModel.INSTANCE);
    }

    private CelestialModelRegistry() {
    }

    public static void register(CelestialModel model) {
        if (model == null || model.id() == null) return;
        MODELS.put(model.id(), model);
    }

    public static void resetAll() {
        for (CelestialModel model : MODELS.values()) {
            try {
                model.reset();
            } catch (Throwable ignored) {
            }
        }
    }

    public static CelestialModel resolve(Identifier id) {
        return id == null ? null : MODELS.get(id);
    }
}
