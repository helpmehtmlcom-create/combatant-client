/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.placer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Pure ray tests mirroring GrimAC's RotationPlace: a block click is legitimate when a ray along the rotation the server
 * saw intersects the box of the clicked block. The face is not checked, and neither is the line of sight.
 */
public final class PlaceRayUtil {
    private PlaceRayUtil() {
    }

    /** The point where the ray first touches the unit cube at {@code pos}; the eye itself when it is inside. */
    public static Vec3 entryPoint(Vec3 eye, Vec3 direction, BlockPos pos, double range) {
        if (eye == null || direction == null || pos == null) {
            return null;
        }
        AABB box = new AABB(pos);
        if (box.contains(eye)) {
            return eye;
        }
        return box.clip(eye, eye.add(direction.scale(range))).orElse(null);
    }

    public static boolean rayHitsBlock(Vec3 eye, Vec3 direction, BlockPos pos, double range) {
        return entryPoint(eye, direction, pos, range) != null;
    }

    /**
     * A click that places a block at {@code target} against the nearest neighbour whose box the ray touches, on the
     * side facing {@code target}, or null when the ray touches none. {@code solid} says which neighbours can be clicked.
     */
    public static BlockHitResult visibleSupportClick(
            Vec3 eye, Vec3 direction, BlockPos target, double range, Predicate<BlockPos> solid) {
        if (eye == null || direction == null || target == null) {
            return null;
        }
        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction toward : Direction.values()) {
            BlockPos support = target.relative(toward);
            if (!solid.test(support)) {
                continue;
            }
            Vec3 point = entryPoint(eye, direction, support, range);
            if (point == null) {
                continue;
            }
            double distance = point.distanceToSqr(eye);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = new BlockHitResult(point, toward.getOpposite(), support, false);
            }
        }
        return best;
    }
}
