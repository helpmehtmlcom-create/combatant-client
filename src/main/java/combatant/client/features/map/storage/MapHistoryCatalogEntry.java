/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import java.nio.file.Path;
import java.util.UUID;

public record MapHistoryCatalogEntry(
        Path file,
        String serverKey,
        UUID targetUuid,
        String targetName,
        String worldKey,
        MapHistorySource source,
        MapHistoryRecordType type,
        long startMs,
        long endMs,
        int recordCount
) {
}
