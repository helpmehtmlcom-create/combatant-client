/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris;

/** Effective owner of world anti-aliasing for the current renderer/pack epoch. */
public enum IrisAaOwner {
    NONE,
    COMBATANT_MSAA,
    COMBATANT_TAA,
    SHADERPACK_FALLBACK
}
