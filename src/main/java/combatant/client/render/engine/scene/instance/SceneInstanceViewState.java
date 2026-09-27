/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.instance;

/** Immutable per-view visibility/LOD snapshot exposed to diagnostics and submission code. */
public record SceneInstanceViewState(
        long frameId,
        boolean visible,
        int lodLevel,
        float projectedDiameterPixels,
        float effectiveDiameterPixels
) {
    public static final SceneInstanceViewState UNINITIALIZED =
            new SceneInstanceViewState(Long.MIN_VALUE, false, -1, 0.0f, 0.0f);
}
