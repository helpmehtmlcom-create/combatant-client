/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import java.util.function.LongPredicate;

/**
 * Picks the feet cell to walk to so that as many of the wanted cells as possible can be clicked from it.
 *
 * <p>The spot that reaches the most targets wins in principle, but a spot reaching nearly as many for a much
 * shorter walk is preferred: every spot covering at least {@link #COVERAGE_SHARE} of the best coverage
 * competes on walk cost alone. The previous goal is kept while it still covers about as much as the new best,
 * so two near-equal spots do not make the player walk back and forth.</p>
 */
public final class StandingSpotChooser {

    /** Spots covering at least this share of the best coverage compete on walk cost. */
    static final double COVERAGE_SHARE = 0.75;
    /** The previous goal survives while it covers no fewer than the best minus this share of it. */
    static final double HYSTERESIS_SHARE = 0.25;
    static final int HYSTERESIS_MIN = 2;

    public enum Mode {
        PLACE,
        BREAK
    }

    /** A feet cell, how many targets it reaches and what walking there costs. */
    public record Spot(int x, int y, int z, int coverage, double cost) {
        public long key() {
            return WalkTerrain.pack(x, y, z);
        }
    }

    private StandingSpotChooser() {
    }

    /**
     * @param flood   cells reachable from the player, see {@link WalkPathfinder#flood}
     * @param targets wanted cells, packed with {@link WalkTerrain#pack}
     * @param reach   click reach, measured from the eyes of a player centred in the feet cell
     * @param current the goal chosen last time, or {@code null}
     * @param reject  spots not to use (ones that were tried and turned out useless), or {@code null}
     * @return the spot to walk to, or {@code null} when no reachable spot covers a single target
     */
    public static Spot choose(WalkTerrain terrain, WalkPathfinder.Flood flood, long[] targets, int targetCount,
                              Mode mode, double reach, Spot current, LongPredicate reject) {
        if (targetCount == 0) return null;
        int[] bounds = bounds(targets, targetCount, reach);
        Spot best = null;
        int bestCoverage = 0;
        int cells = flood.cells().size();
        int[] coverage = new int[cells];
        for (int i = 0; i < cells; i++) {
            long cell = flood.cells().get(i);
            int x = WalkTerrain.unpackX(cell), y = WalkTerrain.unpackY(cell), z = WalkTerrain.unpackZ(cell);
            if (!inside(bounds, x, y, z) || reject != null && reject.test(cell)) continue;
            coverage[i] = coverage(terrain, x, y, z, targets, targetCount, mode, reach);
            bestCoverage = Math.max(bestCoverage, coverage[i]);
        }
        if (bestCoverage == 0) return null;

        int needed = Math.max(1, (int) Math.ceil(bestCoverage * COVERAGE_SHARE));
        for (int i = 0; i < cells; i++) {
            if (coverage[i] < needed) continue;
            long cell = flood.cells().get(i);
            double cost = flood.cost(cell);
            if (best == null || cost < best.cost() || cost == best.cost() && coverage[i] > best.coverage()) {
                best = new Spot(WalkTerrain.unpackX(cell), WalkTerrain.unpackY(cell), WalkTerrain.unpackZ(cell),
                        coverage[i], cost);
            }
        }
        if (current != null && flood.reached(current.key()) && (reject == null || !reject.test(current.key()))) {
            int kept = coverage(terrain, current.x(), current.y(), current.z(), targets, targetCount, mode, reach);
            int slack = Math.max(HYSTERESIS_MIN, (int) Math.ceil(bestCoverage * HYSTERESIS_SHARE));
            if (kept > 0 && kept + slack >= bestCoverage) {
                return new Spot(current.x(), current.y(), current.z(), kept, flood.cost(current.key()));
            }
        }
        return best;
    }

    /** How many targets a player standing centred in {@code (fx, fy, fz)} can click. */
    public static int coverage(WalkTerrain terrain, int fx, int fy, int fz, long[] targets, int targetCount,
                               Mode mode, double reach) {
        double ex = fx + 0.5, ey = ReachModel.eyeY(fy), ez = fz + 0.5;
        double prefilter = (reach + 1.0) * (reach + 1.0);
        int count = 0;
        for (int i = 0; i < targetCount; i++) {
            long target = targets[i];
            int tx = WalkTerrain.unpackX(target), ty = WalkTerrain.unpackY(target), tz = WalkTerrain.unpackZ(target);
            // A block cannot go where the body stands.
            if (mode == Mode.PLACE && tx == fx && tz == fz && (ty == fy || ty == fy + 1)) continue;
            double dx = tx + 0.5 - ex, dy = ty + 0.5 - ey, dz = tz + 0.5 - ez;
            if (dx * dx + dy * dy + dz * dz > prefilter) continue;
            boolean ok = mode == Mode.PLACE
                    ? ReachModel.placeable(terrain, ex, ey, ez, tx, ty, tz, reach)
                    : ReachModel.breakable(terrain, ex, ey, ez, tx, ty, tz, reach);
            if (ok) count++;
        }
        return count;
    }

    /** Box of feet cells that could reach any target at all: targets grown by the reach plus the eye height. */
    private static int[] bounds(long[] targets, int count, double reach) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            long t = targets[i];
            minX = Math.min(minX, WalkTerrain.unpackX(t));
            maxX = Math.max(maxX, WalkTerrain.unpackX(t));
            minY = Math.min(minY, WalkTerrain.unpackY(t));
            maxY = Math.max(maxY, WalkTerrain.unpackY(t));
            minZ = Math.min(minZ, WalkTerrain.unpackZ(t));
            maxZ = Math.max(maxZ, WalkTerrain.unpackZ(t));
        }
        int grow = (int) Math.ceil(reach) + 1;
        int eye = (int) Math.ceil(ReachModel.EYE_HEIGHT);
        return new int[]{minX - grow, minY - grow - eye, minZ - grow, maxX + grow, maxY + grow, maxZ + grow};
    }

    private static boolean inside(int[] b, int x, int y, int z) {
        return x >= b[0] && y >= b[1] && z >= b[2] && x <= b[3] && y <= b[4] && z <= b[5];
    }
}
