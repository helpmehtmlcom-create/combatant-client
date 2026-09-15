/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

/** Canonical texture semantics understood by the Combatant material system. */
public enum MaterialTextureSemantic {
    ALBEDO,
    NORMAL,
    AMBIENT_OCCLUSION,
    ROUGHNESS,
    METALLIC,
    SPECULAR,
    EMISSIVE,
    HEIGHT,
    ORM,
    LABPBR_NORMAL,
    LABPBR_SPECULAR
}
