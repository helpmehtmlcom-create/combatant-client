/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.runtime;

/**
 * Client-observable limits around entity/player tracking for the current connection session.
 *
 * <p>The server-advertised simulation/view distances are exact protocol values available to the
 * client. Player entity tracking is different: the server does not send its global entity broadcast
 * scale, therefore {@code playerTrackingBoundaryBlocks} is an empirical estimate and
 * {@code confirmedPlayerTrackingLowerBoundBlocks} is the only safe lower bound learned from players
 * that were actually delivered to this client.</p>
 */
public record PlayerTrackingRangeSnapshot(
        long observedAtMs,
        int simulationDistanceChunks,
        double simulationDistanceBlocks,
        int viewDistanceChunks,
        double viewDistanceBlocks,
        double vanillaPlayerTrackingCapBlocks,
        double confirmedPlayerTrackingLowerBoundBlocks,
        double playerTrackingBoundaryBlocks,
        int boundarySampleCount
) {
    public static PlayerTrackingRangeSnapshot empty() {
        return new PlayerTrackingRangeSnapshot(0L, 0, 0.0, 0, 0.0, 0.0, 0.0, Double.NaN, 0);
    }

    public boolean hasEmpiricalBoundary() {
        return boundarySampleCount >= 3 && Double.isFinite(playerTrackingBoundaryBlocks)
                && playerTrackingBoundaryBlocks > 0.0;
    }

    /** Radius inside which local absence strongly contradicts a triangulated position. */
    public double conservativeGuaranteedVisibilityRadius(double safetyMarginBlocks) {
        return Math.max(0.0, confirmedPlayerTrackingLowerBoundBlocks - Math.max(0.0, safetyMarginBlocks));
    }
}
