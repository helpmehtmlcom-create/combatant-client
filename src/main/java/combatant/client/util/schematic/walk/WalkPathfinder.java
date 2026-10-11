/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Walking routes over a {@link WalkTerrain}, in feet cells, using only moves a player makes with the movement
 * keys: a step to a neighbour on the same level, a jump up one block, a drop of up to {@code maxDrop} blocks and
 * a diagonal step when both corners it passes are themselves walkable (so the body never clips a corner or
 * hangs over a gap).
 */
public final class WalkPathfinder {

    static final double STEP_COST = 1.0;
    static final double DIAGONAL_COST = Math.sqrt(2.0);
    static final double JUMP_COST = 1.0;
    static final double DROP_COST = 0.5;
    /** Walking right next to lava or fire is legal, but one overshoot away from a burn; keep some distance. */
    static final double DANGER_NEIGHBOUR_COST = 2.0;

    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DZ = {0, 1, 0, -1};

    private WalkPathfinder() {
    }

    @FunctionalInterface
    interface MoveSink {
        void accept(int x, int y, int z, double cost);
    }

    /** Every move out of the feet cell {@code (x, y, z)}. */
    static void forEachMove(WalkTerrain terrain, int x, int y, int z, int maxDrop, MoveSink sink) {
        for (int d = 0; d < 4; d++) {
            int nx = x + DX[d];
            int nz = z + DZ[d];
            if (terrain.standable(nx, y, nz)) {
                sink.accept(nx, y, nz, STEP_COST + dangerPenalty(terrain, nx, y, nz));
            } else if (terrain.open(x, y + 2, z) && terrain.standable(nx, y + 1, nz)) {
                // The jump needs head room above the cell it starts from as well as above the one it lands in.
                sink.accept(nx, y + 1, nz, STEP_COST + JUMP_COST + dangerPenalty(terrain, nx, y + 1, nz));
            } else if (terrain.open(nx, y, nz) && terrain.open(nx, y + 1, nz)) {
                for (int k = 1; k <= maxDrop; k++) {
                    if (terrain.standable(nx, y - k, nz)) {
                        sink.accept(nx, y - k, nz, STEP_COST + DROP_COST * k + dangerPenalty(terrain, nx, y - k, nz));
                        break;
                    }
                    if (!terrain.open(nx, y - k, nz)) break;
                }
            }
        }
        for (int d = 0; d < 4; d++) {
            int ax = DX[d], az = DZ[d];
            int bx = DX[(d + 1) & 3], bz = DZ[(d + 1) & 3];
            int nx = x + ax + bx;
            int nz = z + az + bz;
            if (terrain.standable(x + ax, y, z + az) && terrain.standable(x + bx, y, z + bz)
                    && terrain.standable(nx, y, nz)) {
                sink.accept(nx, y, nz, DIAGONAL_COST + dangerPenalty(terrain, nx, y, nz));
            }
        }
    }

    private static double dangerPenalty(WalkTerrain terrain, int x, int y, int z) {
        for (int d = 0; d < 4; d++) {
            int nx = x + DX[d];
            int nz = z + DZ[d];
            if (terrain.cell(nx, y, nz) == WalkTerrain.DANGER || terrain.cell(nx, y - 1, nz) == WalkTerrain.DANGER) {
                return DANGER_NEIGHBOUR_COST;
            }
        }
        return 0.0;
    }

    /**
     * Cheapest route from {@code start} to {@code goal}, both feet cells packed with {@link WalkTerrain#pack}.
     *
     * @return the cells from start to goal inclusive, or {@code null} when the goal is not reachable within
     * {@code maxNodes} expansions
     */
    public static List<Long> find(WalkTerrain terrain, long start, long goal, int maxDrop, int maxNodes) {
        if (start == goal) return List.of(start);
        int gx = WalkTerrain.unpackX(goal), gy = WalkTerrain.unpackY(goal), gz = WalkTerrain.unpackZ(goal);
        Map<Long, Double> cost = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        PriorityQueue<Entry> open = new PriorityQueue<>();
        cost.put(start, 0.0);
        open.add(new Entry(heuristic(start, gx, gy, gz), 0.0, start));
        int expanded = 0;
        while (!open.isEmpty() && expanded < maxNodes) {
            Entry entry = open.poll();
            long node = entry.node;
            double g = entry.cost;
            if (g > cost.getOrDefault(node, Double.MAX_VALUE)) continue;
            if (node == goal) return trace(parent, start, goal);
            expanded++;
            int x = WalkTerrain.unpackX(node), y = WalkTerrain.unpackY(node), z = WalkTerrain.unpackZ(node);
            forEachMove(terrain, x, y, z, maxDrop, (nx, ny, nz, step) -> {
                long next = WalkTerrain.pack(nx, ny, nz);
                double nextCost = g + step;
                if (nextCost >= cost.getOrDefault(next, Double.MAX_VALUE)) return;
                cost.put(next, nextCost);
                parent.put(next, node);
                open.add(new Entry(nextCost + heuristic(next, gx, gy, gz), nextCost, next));
            });
        }
        return null;
    }

    private static double heuristic(long node, int gx, int gy, int gz) {
        double dx = WalkTerrain.unpackX(node) - gx;
        double dz = WalkTerrain.unpackZ(node) - gz;
        int dy = WalkTerrain.unpackY(node) - gy;
        // Octile distance never overestimates a route of unit and diagonal steps; climbing costs at least a jump.
        double ax = Math.abs(dx), az = Math.abs(dz);
        double flat = Math.max(ax, az) + (DIAGONAL_COST - 1.0) * Math.min(ax, az);
        double vertical = dy < 0 ? -dy * JUMP_COST : dy * DROP_COST;
        return flat + vertical;
    }

    /**
     * Every feet cell reachable from {@code start} for at most {@code maxCost}, cheapest first. The result keeps
     * the parents, so the route to any reached cell comes for free.
     */
    public static Flood flood(WalkTerrain terrain, long start, int maxDrop, double maxCost, int maxNodes) {
        Map<Long, Double> cost = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        List<Long> order = new ArrayList<>();
        PriorityQueue<Entry> open = new PriorityQueue<>();
        cost.put(start, 0.0);
        open.add(new Entry(0.0, 0.0, start));
        while (!open.isEmpty() && order.size() < maxNodes) {
            Entry entry = open.poll();
            long node = entry.node;
            double g = entry.cost;
            if (g > cost.getOrDefault(node, Double.MAX_VALUE)) continue;
            order.add(node);
            int x = WalkTerrain.unpackX(node), y = WalkTerrain.unpackY(node), z = WalkTerrain.unpackZ(node);
            forEachMove(terrain, x, y, z, maxDrop, (nx, ny, nz, step) -> {
                long next = WalkTerrain.pack(nx, ny, nz);
                double nextCost = g + step;
                if (nextCost > maxCost || nextCost >= cost.getOrDefault(next, Double.MAX_VALUE)) return;
                cost.put(next, nextCost);
                parent.put(next, node);
                open.add(new Entry(nextCost, nextCost, next));
            });
        }
        return new Flood(start, order, cost, parent);
    }

    private static List<Long> trace(Map<Long, Long> parent, long start, long goal) {
        List<Long> path = new ArrayList<>();
        long node = goal;
        path.add(node);
        while (node != start) {
            node = parent.get(node);
            path.add(node);
        }
        Collections.reverse(path);
        return path;
    }

    private record Entry(double priority, double cost, long node) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            return Double.compare(priority, other.priority);
        }
    }

    /** Result of {@link #flood}: the reached cells in cost order and the cheapest way to each. */
    public static final class Flood {
        private final long start;
        private final List<Long> order;
        private final Map<Long, Double> cost;
        private final Map<Long, Long> parent;

        Flood(long start, List<Long> order, Map<Long, Double> cost, Map<Long, Long> parent) {
            this.start = start;
            this.order = order;
            this.cost = cost;
            this.parent = parent;
        }

        /** Reached cells, cheapest first, the start included. */
        public List<Long> cells() {
            return order;
        }

        public boolean reached(long cell) {
            return cost.containsKey(cell);
        }

        public double cost(long cell) {
            return cost.getOrDefault(cell, Double.MAX_VALUE);
        }

        public List<Long> pathTo(long cell) {
            if (cell != start && !parent.containsKey(cell)) return null;
            return trace(parent, start, cell);
        }
    }
}
