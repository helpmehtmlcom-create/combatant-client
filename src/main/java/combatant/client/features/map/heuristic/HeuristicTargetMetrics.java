/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.heuristic;

import java.util.List;
import java.util.UUID;

/** Immutable per-target telemetry snapshot used by diagnostics/UI while triangulation is still collecting. */
public record HeuristicTargetMetrics(
        UUID targetUuid,
        String targetName,
        List<HeuristicObservation> observations,
        HeuristicEstimate estimate,
        boolean queued,
        long lastSolvedAtMs,
        long segmentId
) {
    public HeuristicTargetMetrics {
        targetName = targetName == null ? "" : targetName;
        observations = observations == null ? List.of() : List.copyOf(observations);
    }

    public int sampleCount() {
        return observations.size();
    }
}
