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

/** Explicit atmosphere-model registry. Unknown ids stay unknown instead of becoming Overworld. */
public final class AtmosphereModelRegistry {
    private static final Map<Identifier, AtmosphereModel> MODELS = new ConcurrentHashMap<>();

    static {
        register(OverworldAtmosphereModel.INSTANCE);
    }

    private AtmosphereModelRegistry() {
    }

    public static void register(AtmosphereModel model) {
        if (model == null || model.id() == null) return;
        MODELS.put(model.id(), model);
    }

    public static void resetAll() {
        for (AtmosphereModel model : MODELS.values()) {
            try {
                model.reset();
            } catch (Throwable ignored) {
            }
        }
    }

    public static AtmosphereModel resolve(Identifier id) {
        return id == null ? null : MODELS.get(id);
    }
}
