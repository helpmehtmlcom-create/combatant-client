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

/** Explicit weather provider registry used by DimensionRenderProfile.weatherProvider. */
public final class WeatherProviderRegistry {
    private static final Map<Identifier, WeatherProvider> PROVIDERS = new ConcurrentHashMap<>();

    static {
        register(new OverworldWeatherProvider());
    }

    private WeatherProviderRegistry() {
    }

    public static void register(WeatherProvider provider) {
        if (provider == null || provider.id() == null) return;
        PROVIDERS.put(provider.id(), provider);
    }

    public static WeatherProvider resolve(Identifier id) {
        if (id == null || WorldRenderState.NONE.equals(id)) return null;
        return PROVIDERS.get(id);
    }

    public static void resetAll() {
        for (WeatherProvider provider : PROVIDERS.values()) provider.reset();
    }
}
