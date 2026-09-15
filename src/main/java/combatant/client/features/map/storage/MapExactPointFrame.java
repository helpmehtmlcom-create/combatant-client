/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import java.util.UUID;

public record MapExactPointFrame(
        UUID targetUuid,
        String targetName,
        String worldKey,
        MapHistorySource source,
        String sourceKey,
        long observedAtMs,
        long sourceRevision,
        double x,
        double y,
        double z,
        double accuracyRadius
) implements MapHistoryRecord {
    public MapExactPointFrame {
        if (targetUuid == null) throw new IllegalArgumentException("targetUuid");
        targetName = clean(targetName);
        worldKey = clean(worldKey);
        sourceKey = clean(sourceKey);
        if (source != MapHistorySource.LOCAL_ENTITY_EXACT
                && source != MapHistorySource.LOCATOR_EXACT
                && source != MapHistorySource.MAPLINK_EXACT
                && source != MapHistorySource.LOCATOR_APPROXIMATE) {
            throw new IllegalArgumentException("Exact source required: " + source);
        }
        if (worldKey.isBlank()) throw new IllegalArgumentException("worldKey");
        if (observedAtMs <= 0L) throw new IllegalArgumentException("observedAtMs");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("position");
        }
        if (!Double.isFinite(accuracyRadius) || accuracyRadius < 0.0) {
            throw new IllegalArgumentException("accuracyRadius");
        }
    }

    @Override public MapHistoryRecordType type() { return MapHistoryRecordType.EXACT_POINT; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
