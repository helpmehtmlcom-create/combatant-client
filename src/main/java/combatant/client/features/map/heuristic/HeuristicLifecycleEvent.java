/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.heuristic;

import combatant.client.features.map.location.LocationSessionKey;

import java.util.UUID;

public record HeuristicLifecycleEvent(
        UUID targetUuid,
        String targetName,
        LocationSessionKey session,
        HeuristicLifecycleEventType type,
        long timestampMs,
        long segmentId
) {
    public HeuristicLifecycleEvent {
        targetName = targetName == null ? "" : targetName.trim();
    }
}
