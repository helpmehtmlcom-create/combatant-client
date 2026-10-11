/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic.walk;

/**
 * A block grid as the schematic walker sees it: one class per cell, nothing else.
 *
 * <p>Kept free of game types so the pathfinder, the standing-spot chooser and the pillar planner can be tested
 * against small hand-drawn worlds.</p>
 */
@FunctionalInterface
public interface WalkTerrain {

    /** No collision: the body can pass through (air, grass, an open door). */
    byte OPEN = 0;
    /** Full collision that can be stood on and clicked against. */
    byte SOLID = 1;
    /** Never enter or stand on: lava, fire, cactus, magma, powder snow, water and the like. */
    byte DANGER = 2;
    /** Unloaded, outside the world or an odd shape (fence, wall): treated as an obstacle that cannot be stood on. */
    byte UNKNOWN = 3;

    byte cell(int x, int y, int z);

    default boolean open(int x, int y, int z) {
        return cell(x, y, z) == OPEN;
    }

    default boolean solid(int x, int y, int z) {
        return cell(x, y, z) == SOLID;
    }

    /** Feet cell and head cell free, a solid block underneath. */
    default boolean standable(int x, int y, int z) {
        return open(x, y, z) && open(x, y + 1, z) && solid(x, y - 1, z);
    }

    /** Packs a cell the way {@code BlockPos.asLong} does, so keys can be shared with game code. */
    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    static int unpackX(long key) {
        return (int) (key >> 38);
    }

    static int unpackY(long key) {
        return (int) (key << 52 >> 52);
    }

    static int unpackZ(long key) {
        return (int) (key << 26 >> 38);
    }
}
