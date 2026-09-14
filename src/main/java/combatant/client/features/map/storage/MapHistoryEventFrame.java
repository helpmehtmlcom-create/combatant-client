/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import java.util.UUID;

public record MapHistoryEventFrame(
        UUID targetUuid,
        String targetName,
        String worldKey,
        MapHistorySource source,
        String sourceKey,
        long observedAtMs,
        long sourceRevision,
        MapHistoryEventKind eventKind,
        long generation,
        long segmentId
) implements MapHistoryRecord {
    public MapHistoryEventFrame {
        if (targetUuid == null) throw new IllegalArgumentException("targetUuid");
        if (source == null) throw new IllegalArgumentException("source");
        if (eventKind == null) throw new IllegalArgumentException("eventKind");
        targetName = clean(targetName);
        worldKey = clean(worldKey);
        sourceKey = clean(sourceKey);
        if (worldKey.isBlank()) throw new IllegalArgumentException("worldKey");
        generation = Math.max(0L, generation);
        segmentId = Math.max(0L, segmentId);
    }

    @Override public MapHistoryRecordType type() { return MapHistoryRecordType.EVENT; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
