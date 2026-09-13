/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import java.util.UUID;

public record MapBearingFrame(
        UUID targetUuid,
        String targetName,
        String worldKey,
        MapHistorySource source,
        String sourceKey,
        long observedAtMs,
        long sourceRevision,
        double observerX,
        double observerZ,
        double bearingRadians,
        double weight
) implements MapHistoryRecord {
    public MapBearingFrame {
        if (targetUuid == null) throw new IllegalArgumentException("targetUuid");
        targetName = clean(targetName);
        worldKey = clean(worldKey);
        sourceKey = clean(sourceKey);
        if (source != MapHistorySource.LOCATOR_BEARING) throw new IllegalArgumentException("bearing source");
        if (!Double.isFinite(observerX) || !Double.isFinite(observerZ) || !Double.isFinite(bearingRadians)) {
            throw new IllegalArgumentException("bearing");
        }
        if (!(weight > 0.0) || !Double.isFinite(weight)) weight = 1.0;
    }

    @Override public MapHistoryRecordType type() { return MapHistoryRecordType.BEARING; }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
