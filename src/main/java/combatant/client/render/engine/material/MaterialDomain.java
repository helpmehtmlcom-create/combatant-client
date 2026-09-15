/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

/**
 * Semantic surface domain supplied by the producer. This is not the same thing as
 * Sodium's terrain render pass: a glass or water surface may currently use Sodium's translucent
 * compatibility pass while still being identified exactly as GLASS/WATER to Combatant.
 */
public enum MaterialDomain {
    OPAQUE,
    CUTOUT,
    TRANSLUCENT,
    WATER,
    LAVA,
    GLASS,
    PORTAL,
    UNKNOWN;

    public int gpuCode() {
        return ordinal() & 0xF;
    }
}
