/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.spatial;

/** Logical spatial-index counters independent from GL/Vulkan. */
public record SceneSpatialStatsSnapshot(
        int records,
        int occupiedSections,
        int largeObjects,
        int maxSectionsPerRecord,
        long worldEpoch
) {
}
