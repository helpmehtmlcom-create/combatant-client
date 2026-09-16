/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Thread-safe registry of explicit local-light producers. */
public final class DynamicLightRegistry {
    private static final CopyOnWriteArrayList<DynamicLightProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private DynamicLightRegistry() {
    }

    public static AutoCloseable register(DynamicLightProvider provider) {
        if (provider == null) return () -> { };
        PROVIDERS.addIfAbsent(provider);
        return () -> PROVIDERS.remove(provider);
    }

    public static void collect(DynamicLightProvider.Context context, Consumer<LightDescriptor> output) {
        if (output == null) return;
        for (DynamicLightProvider provider : PROVIDERS) {
            try {
                provider.collect(context, descriptor -> {
                    if (descriptor != null && descriptor.valid()) output.accept(descriptor);
                });
            } catch (Throwable ignored) {
                // One addon/provider must not invalidate the renderer's canonical lighting chain.
            }
        }
    }
}
