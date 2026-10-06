/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.scaffold;

import combatant.client.util.aiming.data.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The geometry a block placement has to satisfy to look like a vanilla click, as strict anticheats check it:
 * <ul>
 *   <li>the eye is on the outer side of the clicked face's plane (GrimAC PositionPlace, zero slack, measured at the
 *       position of the last movement packet, which at the start of a tick is the position the client has now);</li>
 *   <li>a ray along the sent rotation hits the supporting block, measured from the position of the movement packet
 *       that FOLLOWS the click (GrimAC RotationPlace). At the start of a tick that is the position after this tick's
 *       movement, so the ray has to hit from where the player is now and from where he is about to be.</li>
 * </ul>
 * Aiming at the middle of the clicked face from the current eye satisfies the first rule and breaks the second one
 * for a bridging player: the eye hangs a few centimetres over the block's edge, the aim is nearly straight down, and
 * one tick of walking later the same ray passes in front of the block.
 */
public final class ScaffoldPlacementGeometry {
    /** Grim's eye box has no tolerance while position packets arrive every tick, so stay a hair on the right side. */
    public static final double FACE_MARGIN = 0.002;

    private static final double RAY_LENGTH = 12.0;
    /** Fraction of the movement step added on top of the prediction when scoring candidate rotations. */
    private static final double OVERSHOOT = 0.35;
    private static final double SAFE_INSET = 0.08;
    private static final double[] INSETS = {0.35, 0.25, 0.15, SAFE_INSET, 0.0};
    private static final double[] DEPTH_SAMPLES = {0.1, 0.3, 0.5, 0.7, 0.9};

    private ScaffoldPlacementGeometry() {
    }

    /** How far the eye lies on the outer side of the face plane of {@code shape}; negative when it is behind it. */
    public static double outwardDistance(Vec3 eye, Direction face, AABB shape) {
        return switch (face) {
            case UP -> eye.y - shape.maxY;
            case DOWN -> shape.minY - eye.y;
            case NORTH -> shape.minZ - eye.z;
            case SOUTH -> eye.z - shape.maxZ;
            case WEST -> shape.minX - eye.x;
            case EAST -> eye.x - shape.maxX;
        };
    }

    public static boolean rayHitsBox(Vec3 eye, Vec3 direction, AABB box) {
        if (box.contains(eye)) {
            return true;
        }
        return box.clip(eye, eye.add(direction.scale(RAY_LENGTH))).isPresent();
    }

    /** Face of the unit block at {@code pos} that the ray enters through, or null when it misses. */
    public static Direction firstHitFace(Vec3 eye, Vec3 direction, BlockPos pos) {
        BlockHitResult hit = AABB.clip(List.of(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)), eye, eye.add(direction.scale(RAY_LENGTH)), pos);
        return hit == null ? null : hit.getDirection();
    }

    /**
     * Whether clicking {@code face} of the block at {@code pos} now, with {@code rotation} going out in this tick's
     * movement packet, passes both rules. {@code eyeNext} is the predicted eye position after this tick's movement.
     */
    public static boolean isSafe(Vec3 eyeNow, Vec3 eyeNext, Rotation rotation, BlockPos pos, Direction face, AABB shape) {
        if (eyeNow == null || eyeNext == null || rotation == null || shape == null) {
            return false;
        }
        if (outwardDistance(eyeNow, face, shape) < FACE_MARGIN) {
            return false;
        }
        Vec3 direction = rotation.directionVector();
        AABB cube = new AABB(pos);
        return rayHitsBox(eyeNow, direction, cube) && rayHitsBox(eyeNext, direction, cube);
    }

    /**
     * Picks the rotation that keeps the ray on the block from now until the end of the tick with the most room to
     * spare, preferring (in this order) enough room to absorb a movement misprediction, a ray that enters through the
     * clicked face, more room, and the smallest turn from {@code current}. Returns null when no aim works.
     */
    public static Rotation chooseRotation(Vec3 eyeNow, Vec3 eyeNext, BlockPos pos, Direction face, Rotation current) {
        Vec3 mid = eyeNow.add(eyeNext).scale(0.5);
        Vec3 overshoot = eyeNext.add(eyeNext.subtract(eyeNow).scale(OVERSHOOT));
        Vec3[] eyes = {eyeNow, eyeNext, overshoot};

        double[] xs = samples(Direction.Axis.X, face, mid.x - pos.getX());
        double[] ys = samples(Direction.Axis.Y, face, mid.y - pos.getY());
        double[] zs = samples(Direction.Axis.Z, face, mid.z - pos.getZ());

        Rotation best = null;
        boolean bestSafe = false;
        boolean bestFace = false;
        double bestInset = -1.0;
        float bestTurn = Float.MAX_VALUE;

        for (double sx : xs) {
            for (double sy : ys) {
                for (double sz : zs) {
                    Vec3 aim = new Vec3(pos.getX() + sx, pos.getY() + sy, pos.getZ() + sz);
                    Rotation rotation = aimAt(aim, mid, current);
                    Vec3 direction = rotation.directionVector();
                    double inset = robustInset(eyes, direction, pos);
                    if (inset < 0.0) {
                        continue;
                    }
                    boolean safe = inset >= SAFE_INSET;
                    boolean faceFirst = firstHitFace(eyeNow, direction, pos) == face;
                    float turn = current == null ? 0.0f : current.angleTo(rotation);
                    if (best == null || better(safe, faceFirst, inset, turn, bestSafe, bestFace, bestInset, bestTurn)) {
                        best = rotation;
                        bestSafe = safe;
                        bestFace = faceFirst;
                        bestInset = inset;
                        bestTurn = turn;
                    }
                }
            }
        }
        return best;
    }

    /**
     * The point of the clicked face to put in the packet: where the view ray meets the block, moved onto the face.
     * Always inside the face, so cursor checks (InvalidPlaceCursor, FabricatedPlace) and the server's own bounds
     * check are satisfied whatever the ray does.
     */
    public static Vec3 clickPoint(Vec3 eyeNow, Rotation rotation, BlockPos pos, Direction face, AABB shape) {
        Vec3 hit = new AABB(pos).clip(eyeNow, eyeNow.add(rotation.directionVector().scale(RAY_LENGTH))).orElse(shape.getCenter());
        double x = face.getAxis() == Direction.Axis.X ? (face == Direction.EAST ? shape.maxX : shape.minX) : inside(hit.x, shape.minX, shape.maxX);
        double y = face.getAxis() == Direction.Axis.Y ? (face == Direction.UP ? shape.maxY : shape.minY) : inside(hit.y, shape.minY, shape.maxY);
        double z = face.getAxis() == Direction.Axis.Z ? (face == Direction.SOUTH ? shape.maxZ : shape.minZ) : inside(hit.z, shape.minZ, shape.maxZ);
        return new Vec3(x, y, z);
    }

    private static double inside(double value, double min, double max) {
        double lo = min + 0.01;
        double hi = max - 0.01;
        return lo > hi ? (min + max) * 0.5 : Mth.clamp(value, lo, hi);
    }

    /**
     * Candidate aim coordinates along one axis (relative to the block). Along the face normal and vertically the
     * whole block depth is tried; along the remaining horizontal axis the aim stays level with the eye so the view
     * points straight at the block instead of wandering sideways.
     */
    private static double[] samples(Direction.Axis axis, Direction face, double eyeRelative) {
        if (axis == Direction.Axis.Y || axis == face.getAxis()) {
            return DEPTH_SAMPLES;
        }
        return new double[]{Mth.clamp(eyeRelative, 0.12, 0.88)};
    }

    private static Rotation aimAt(Vec3 aim, Vec3 from, Rotation current) {
        Vec3 delta = aim.subtract(from);
        Rotation rotation = Rotation.lookingAt(aim, from);
        if (Math.hypot(delta.x, delta.z) < 1.0E-3 && current != null) {
            // Looking straight down or up: any yaw does it, so keep the one already on the server.
            return new Rotation(current.yaw(), rotation.pitch(), false);
        }
        return rotation;
    }

    /** Largest inset of the block for which every eye's ray still hits it, or -1 when even the full block is missed. */
    private static double robustInset(Vec3[] eyes, Vec3 direction, BlockPos pos) {
        AABB cube = new AABB(pos);
        for (double inset : INSETS) {
            AABB core = cube.inflate(-inset);
            boolean all = true;
            for (Vec3 eye : eyes) {
                if (!rayHitsBox(eye, direction, core)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return inset;
            }
        }
        return -1.0;
    }

    private static boolean better(boolean safe, boolean faceFirst, double inset, float turn,
                                  boolean bestSafe, boolean bestFace, double bestInset, float bestTurn) {
        if (safe != bestSafe) {
            return safe;
        }
        if (faceFirst != bestFace) {
            return faceFirst;
        }
        if (Math.abs(inset - bestInset) > 1.0E-9) {
            return inset > bestInset;
        }
        return turn < bestTurn;
    }
}
