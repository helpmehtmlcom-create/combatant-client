/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.lod;

/** Immutable result suitable for diagnostics and instance LOD state updates. */
public record SceneLodSelection(
        int level,
        float projectedDiameterPixels,
        float effectiveDiameterPixels,
        boolean changed
) {
}
