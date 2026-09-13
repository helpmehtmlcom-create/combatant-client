/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import java.util.UUID;

public sealed interface MapHistoryRecord permits MapEstimateFrame, MapExactPointFrame, MapBearingFrame {
    UUID targetUuid();
    String targetName();
    String worldKey();
    MapHistorySource source();
    String sourceKey();
    long observedAtMs();
    long sourceRevision();
    MapHistoryRecordType type();
}
