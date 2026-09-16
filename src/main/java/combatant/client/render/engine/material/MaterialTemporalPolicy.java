/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

/** Explicit producer-known temporal response used to build reactive masks. */
public enum MaterialTemporalPolicy {
    STABLE(0.0f),
    RESPONSIVE(0.5f),
    REJECT_HISTORY(1.0f);

    private final float reactiveValue;

    MaterialTemporalPolicy(float reactiveValue) {
        this.reactiveValue = reactiveValue;
    }

    public float reactiveValue() {
        return reactiveValue;
    }
}
