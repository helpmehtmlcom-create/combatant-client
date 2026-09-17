/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

/** Generic display transforms understood by the single deferred debug compositor. */
public enum DeferredDebugDecodeMode {
    REGULAR_COLOR(0),
    SCALAR(1),
    SIGNED_VELOCITY(2),
    DEPTH(3),
    INTEGER_ID(4),
    CONFIDENCE_MASK(5),
    NORMAL_OCT(6),
    VOLUME_COLOR(7),
    VOLUME_SCALAR(8),
    EXPOSURE_VALUE(9),
    HISTOGRAM(10);

    private final int shaderId;

    DeferredDebugDecodeMode(int shaderId) {
        this.shaderId = shaderId;
    }

    int shaderId() {
        return shaderId;
    }
}
