/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.visibility;

/** Logical visibility counters shared by GL/Vulkan. */
public record SceneVisibilityStatsSnapshot(
        long sectionsTested,
        long sectionsRejected,
        long recordsConsidered,
        long duplicateSectionRecordsSuppressed,
        long largeRecordsConsidered,
        long frustumRejected,
        long recordsVisible
) {
    public static final SceneVisibilityStatsSnapshot EMPTY = new SceneVisibilityStatsSnapshot(0, 0, 0, 0, 0, 0, 0);
}
