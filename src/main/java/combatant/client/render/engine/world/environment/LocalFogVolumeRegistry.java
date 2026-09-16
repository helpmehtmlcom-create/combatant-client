/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Thread-safe registry for explicit world-space local participating-medium producers. */
public final class LocalFogVolumeRegistry {
    private static final CopyOnWriteArrayList<LocalFogVolumeProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    private LocalFogVolumeRegistry() {
    }

    public static AutoCloseable register(LocalFogVolumeProvider provider) {
        if (provider == null) return () -> { };
        PROVIDERS.addIfAbsent(provider);
        return () -> PROVIDERS.remove(provider);
    }

    public static void collect(LocalFogVolumeProvider.Context context,
                               Consumer<LocalFogVolumeDescriptor> output) {
        if (output == null) return;
        for (LocalFogVolumeProvider provider : PROVIDERS) {
            try {
                provider.collect(context, descriptor -> {
                    if (descriptor != null && descriptor.valid()) output.accept(descriptor);
                });
            } catch (Throwable ignored) {
                // A third-party medium producer cannot invalidate the canonical renderer chain.
            }
        }
    }
}
