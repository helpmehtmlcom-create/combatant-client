/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.heuristic;

public record HeuristicRuntimeStats(
        MapTriangulationMode mode,
        long accepted,
        long rejectedMode,
        long rejectedAge,
        long rejectedDuplicate,
        long rejectedInformationGain,
        long rejectedCapacity,
        long solved,
        long segmentResets,
        int activeTargets,
        int queuedTargets
) {}
