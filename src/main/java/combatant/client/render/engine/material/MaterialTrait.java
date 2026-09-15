/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

/** Orthogonal producer-known surface properties. Domain and traits must never be reconstructed from G-buffer color. */
public enum MaterialTrait {
    FOLIAGE(1 << 0),
    EMISSIVE(1 << 1),
    TRANSMISSIVE(1 << 2),
    REFRACTIVE(1 << 3),
    DOUBLE_SIDED(1 << 4),
    FLUID(1 << 5),
    PORTAL(1 << 6),
    GLASS(1 << 7);

    private final int bit;

    MaterialTrait(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public static int mask(MaterialTrait... traits) {
        int mask = 0;
        if (traits != null) {
            for (MaterialTrait trait : traits) {
                if (trait != null) mask |= trait.bit;
            }
        }
        return mask;
    }
}
