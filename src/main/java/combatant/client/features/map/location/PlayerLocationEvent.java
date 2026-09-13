/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.location;

import java.util.UUID;

public record PlayerLocationEvent(
        long generation,
        long timestampMs,
        UUID playerUuid,
        String playerName,
        PlayerLocationEventType type,
        PlayerLocationSnapshot previous,
        PlayerLocationSnapshot current
) {
}
