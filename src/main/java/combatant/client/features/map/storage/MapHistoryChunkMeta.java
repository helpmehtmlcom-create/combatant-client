/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import java.util.UUID;

public record MapHistoryChunkMeta(
        long offset,
        int blockLength,
        UUID targetUuid,
        String targetName,
        String serverKey,
        String worldKey,
        MapHistorySource source,
        String sourceKey,
        MapHistoryRecordType type,
        long startMs,
        long endMs,
        int recordCount,
        int compressedLength,
        int uncompressedLength,
        int crc32
) {
    public boolean overlaps(long fromMs, long toMs) {
        return endMs >= fromMs && startMs <= toMs;
    }
}
