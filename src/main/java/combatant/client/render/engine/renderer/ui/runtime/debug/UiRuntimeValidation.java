/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.debug;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Central switch for strict UI authoring/runtime contract validation. */
public enum UiRuntimeValidation {
    ;

    private static final String PROPERTY = "combatant.ui.validation";
    private static final Logger LOGGER = LoggerFactory.getLogger("Combatant/UI");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final boolean ENABLED = resolveEnabled();

    public static boolean enabled() {
        return ENABLED;
    }

    private static boolean resolveEnabled() {
        String configured = System.getProperty(PROPERTY);
        if (configured != null) return Boolean.parseBoolean(configured);
        try {
            return FabricLoader.getInstance().isDevelopmentEnvironment();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition && enabled()) {
            throw new IllegalStateException(message != null ? message : "UI runtime contract violation.");
        }
    }

    public static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message != null ? message : "Invalid UI runtime value.");
    }

    /** Emits a development-only contract diagnostic without turning optional/fallback paths fatal. */
    public static void warnOnce(String key, String message) {
        if (!enabled()) return;
        String resolvedKey = key != null ? key : String.valueOf(message);
        if (WARNED.add(resolvedKey)) {
            LOGGER.warn("[Combatant][UI validation] {}", message);
        }
    }
}
