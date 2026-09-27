/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.instance;

public record SceneInstanceStatsSnapshot(
        int instances,
        long frameId,
        long visibleVisits,
        long lodChanges,
        long transformUpdates,
        long spatialUpdates,
        long discontinuities
) {
}
