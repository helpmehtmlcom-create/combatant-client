/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Explicit support state with a declared consumer-facing fallback category. */
public record RhiFeatureSupport(boolean supported,
                                String reason,
                                Fallback fallback) {
    public enum Fallback {
        NONE,
        DISABLE_CONSUMER,
        REDUCE_RESOURCE,
        ALTERNATE_FORMAT,
        DROP_OPTIONAL_USAGE,
        RECREATE_WITH_REQUIRED_USAGE
    }

    public RhiFeatureSupport {
        reason = reason == null ? "" : reason;
        fallback = fallback == null ? (supported ? Fallback.NONE : Fallback.DISABLE_CONSUMER) : fallback;
        if (supported && fallback != Fallback.NONE) {
            throw new IllegalArgumentException("Supported feature cannot require a fallback");
        }
    }

    public static RhiFeatureSupport available() {
        return new RhiFeatureSupport(true, "", Fallback.NONE);
    }

    public static RhiFeatureSupport unsupported(String reason, Fallback fallback) {
        return new RhiFeatureSupport(false, reason, fallback);
    }
}
