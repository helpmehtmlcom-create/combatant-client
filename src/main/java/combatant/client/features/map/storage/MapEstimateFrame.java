/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import java.util.UUID;

public record MapEstimateFrame(
        UUID targetUuid,
        String targetName,
        String worldKey,
        MapHistorySource source,
        String sourceKey,
        long observedAtMs,
        long sourceRevision,
        double x,
        double z,
        double uncertaintyMajor,
        double uncertaintyMinor,
        double uncertaintyAngleRadians,
        double residualRms,
        double confidence,
        int sampleCount,
        int inlierCount,
        long segmentId
) implements MapHistoryRecord {
    public MapEstimateFrame {
        if (targetUuid == null) throw new IllegalArgumentException("targetUuid");
        targetName = clean(targetName);
        worldKey = clean(worldKey);
        sourceKey = clean(sourceKey);
        if (source != MapHistorySource.HEURISTIC_TRIANGULATED && source != MapHistorySource.DUPLEX_TRIANGULATED) {
            throw new IllegalArgumentException("Estimate source must be triangulated: " + source);
        }
        if (worldKey.isBlank()) throw new IllegalArgumentException("worldKey");
        if (observedAtMs <= 0L) throw new IllegalArgumentException("observedAtMs");
        if (!Double.isFinite(x) || !Double.isFinite(z)) throw new IllegalArgumentException("position");
        if (!Double.isFinite(uncertaintyMajor) || uncertaintyMajor < 0.0
                || !Double.isFinite(uncertaintyMinor) || uncertaintyMinor < 0.0
                || !Double.isFinite(uncertaintyAngleRadians)
                || !Double.isFinite(residualRms) || residualRms < 0.0
                || !Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("invalid estimate quality");
        }
        sampleCount = Math.max(0, sampleCount);
        inlierCount = Math.max(0, Math.min(sampleCount, inlierCount));
    }

    @Override public MapHistoryRecordType type() { return MapHistoryRecordType.ESTIMATE; }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
