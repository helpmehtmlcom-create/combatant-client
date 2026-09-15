/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.map;

import java.util.Objects;

/** One directional bearing constraint in world X-Z space. */
public record MapBearingRay(String id,
                            double observerX,
                            double observerZ,
                            double bearingRadians,
                            String label,
                            int argb,
                            double thickness,
                            int priority) {
    public MapBearingRay {
        Objects.requireNonNull(id, "id");
        label = label == null ? "" : label.trim();
        if (!Double.isFinite(observerX) || !Double.isFinite(observerZ)
                || !Double.isFinite(bearingRadians) || !Double.isFinite(thickness) || thickness <= 0.0) {
            throw new IllegalArgumentException("Invalid bearing ray geometry.");
        }
    }
}
