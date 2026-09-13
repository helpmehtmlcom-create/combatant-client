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
        if (!Double.isFinite(x) || !Double.isFinite(z)) throw new IllegalArgumentException("position");
        uncertaintyMajor = finiteNonNegative(uncertaintyMajor);
        uncertaintyMinor = finiteNonNegative(uncertaintyMinor);
        uncertaintyAngleRadians = finite(uncertaintyAngleRadians);
        residualRms = finiteNonNegative(residualRms);
        confidence = clamp01(confidence);
        sampleCount = Math.max(0, sampleCount);
        inlierCount = Math.max(0, Math.min(sampleCount, inlierCount));
    }

    @Override public MapHistoryRecordType type() { return MapHistoryRecordType.ESTIMATE; }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static double finite(double value) { return Double.isFinite(value) ? value : 0.0; }
    private static double finiteNonNegative(double value) { return Math.max(0.0, finite(value)); }
    private static double clamp01(double value) { return Math.max(0.0, Math.min(1.0, finite(value))); }
}
