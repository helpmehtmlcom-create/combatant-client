/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import java.util.function.LongPredicate;

/**
 * Where to jump-place a column of support blocks when no walkable spot reaches the wanted cells.
 *
 * <p>A pillar starts on a reachable feet cell and rises one block per jump. Each candidate column is judged by
 * the coverage its top would have (with the column itself counted as solid), then by the walk to its foot plus
 * a fixed price per block, so a short pillar next to the player beats a tall one or a long walk.</p>
 */
public final class PillarPlanner {

    /** Price of one pillar block in walk-cost units: a jump, a placement and later a block to break again. */
    static final double BLOCK_COST = 3.0;

    /** Column at {@code (x, z)} from feet cell {@code y} up {@code height} blocks; the player ends at {@code y + height}. */
    public record Pillar(int x, int y, int z, int height, int coverage, double cost) {
        public int topY() {
            return y + height;
        }
    }

    private PillarPlanner() {
    }

    /**
     * @param forbidden cells no support block may occupy (the build footprint), or {@code null}
     * @param reject    feet cells not to start a pillar from, or {@code null}
     * @return the cheapest pillar reaching a share of the best coverage, or {@code null} if none reaches anything
     */
    public static Pillar plan(WalkTerrain terrain, WalkPathfinder.Flood flood, long[] targets, int targetCount,
                              double reach, int maxHeight, LongPredicate forbidden, LongPredicate reject) {
        if (targetCount == 0 || maxHeight <= 0) return null;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 0; i < targetCount; i++) {
            minY = Math.min(minY, WalkTerrain.unpackY(targets[i]));
            maxY = Math.max(maxY, WalkTerrain.unpackY(targets[i]));
        }
        Pillar best = null;
        int bestCoverage = 0;
        Pillar[] options = new Pillar[flood.cells().size()];
        for (int i = 0; i < options.length; i++) {
            long cell = flood.cells().get(i);
            if (reject != null && reject.test(cell)) continue;
            options[i] = bestColumn(terrain, cell, flood.cost(cell), targets, targetCount, reach, maxHeight,
                    forbidden, minY, maxY);
            if (options[i] != null) bestCoverage = Math.max(bestCoverage, options[i].coverage());
        }
        if (bestCoverage == 0) return null;
        int needed = Math.max(1, (int) Math.ceil(bestCoverage * StandingSpotChooser.COVERAGE_SHARE));
        for (Pillar option : options) {
            if (option == null || option.coverage() < needed) continue;
            if (best == null || option.cost() < best.cost()) best = option;
        }
        return best;
    }

    /** The best height for a column at one feet cell, or {@code null} when no height reaches a target. */
    private static Pillar bestColumn(WalkTerrain terrain, long cell, double walkCost, long[] targets, int targetCount,
                                     double reach, int maxHeight, LongPredicate forbidden, int minTargetY, int maxTargetY) {
        int x = WalkTerrain.unpackX(cell), y = WalkTerrain.unpackY(cell), z = WalkTerrain.unpackZ(cell);
        Pillar best = null;
        for (int h = 1; h <= maxHeight; h++) {
            int block = y + h - 1;
            // The new block's cell must be free and allowed, and the jump onto it needs head room above the top.
            if (!terrain.open(x, block, z) || forbidden != null && forbidden.test(WalkTerrain.pack(x, block, z))) break;
            if (!terrain.open(x, block + 1, z) || !terrain.open(x, block + 2, z) || !terrain.open(x, block + 3, z)) break;
            int feet = y + h;
            // Standing far above every target never helps; neither does a top still below the lowest reachable band.
            if (ReachModel.eyeY(feet) - reach > maxTargetY + 1) break;
            if (ReachModel.eyeY(feet) + reach < minTargetY) continue;
            WalkTerrain withColumn = column(terrain, x, y, z, h);
            int coverage = StandingSpotChooser.coverage(withColumn, x, feet, z, targets, targetCount,
                    StandingSpotChooser.Mode.PLACE, reach);
            if (coverage == 0) continue;
            Pillar option = new Pillar(x, y, z, h, coverage, walkCost + h * BLOCK_COST);
            if (best == null || coverage > best.coverage()) best = option;
        }
        return best;
    }

    /** The terrain with support blocks at {@code (x, y .. y + height - 1, z)}. */
    static WalkTerrain column(WalkTerrain terrain, int x, int y, int z, int height) {
        return (cx, cy, cz) -> cx == x && cz == z && cy >= y && cy < y + height ? WalkTerrain.SOLID : terrain.cell(cx, cy, cz);
    }
}
