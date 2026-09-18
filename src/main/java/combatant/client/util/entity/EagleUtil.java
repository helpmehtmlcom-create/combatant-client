/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.entity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.render.helpers.TickDelta;
public enum EagleUtil {
    ;
    private static final double STEP_HEIGHT = 0.5;
    private static final double MIN_INPUT_LENGTH_SQUARED = 1.0E-6;
    private static final double MIN_AXIS_COMPONENT = 0.18;
    private static final double MAX_RESCUE_LOOKAHEAD = 0.42;
    private static final double MIN_TICK_DELTA_LEAD = 0.25;
    private static final double CENTER_DEAD_ANGLE_COS = 0.34;
    private static final int DIAGONAL_RESCUE_TICKS = 2;
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public static EdgeCheck checkEdge(LocalPlayer player, MovementInputEvent event, double edgeDistance) {
        return checkEdge(player, event, edgeDistance, TickDelta.tickProgress(false));
    }

    public static EdgeCheck checkEdge(LocalPlayer player, MovementInputEvent event, double edgeDistance, double tickDelta) {
        if (player == null || event == null) {
            return EdgeCheck.empty();
        }

        Vec3 inputDirection = movementInputToWorld(player, event);
        if (inputDirection.lengthSqr() < MIN_INPUT_LENGTH_SQUARED) {
            return EdgeCheck.empty();
        }

        Vec3 movementDirection = inputDirection.normalize();
        Vec3 horizontalVelocity = new Vec3(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
        double velocityLength = horizontalVelocity.horizontalDistance();
        double distance = Math.max(0.0, edgeDistance);
        double lookAhead = Math.min(MAX_RESCUE_LOOKAHEAD, distance + velocityLength);
        double firstLead = Math.max(MIN_TICK_DELTA_LEAD, clamp01(tickDelta));
        Vec3 partialPos = player.position().add(horizontalVelocity.scale(firstLead));
        Vec3 nextPartialPos = player.position().add(horizontalVelocity.scale(firstLead + 0.5));

        boolean closeToEdge = wouldFallAlong(player, player.position(), movementDirection, distance)
                || wouldFallAlong(player, partialPos, movementDirection, distance)
                || wouldFallAlong(player, nextPartialPos, movementDirection, distance);

        boolean diagonalRescue = isDiagonal(event)
                && wouldDiagonalFall(player, movementDirection, horizontalVelocity, Math.max(distance, lookAhead), firstLead);

        return new EdgeCheck(closeToEdge || diagonalRescue, diagonalRescue, movementDirection);
    }

    public static boolean isCloseToEdge(LocalPlayer player, MovementInputEvent event, double edgeDistance) {
        return checkEdge(player, event, edgeDistance).closeToEdge();
    }

    public static boolean shouldDiagonalRescue(LocalPlayer player, MovementInputEvent event, double edgeDistance) {
        return checkEdge(player, event, edgeDistance).diagonalRescue();
    }
    /**
     * Checks if moving by the specified threshold in any horizontal cardinal direction puts the
     * player's bounding box edge or corner over air (i.e. would fall).
     *
     * @param player        the local player to test
     * @param edgeThreshold horizontal distance threshold in blocks to test in each direction
     * @return {@code true} if moving in any cardinal direction causes the player to fall or overhang air
     */
    public static boolean isStandingOnEdge(LocalPlayer player, double edgeThreshold) {
        if (player == null || player.level() == null) {
            return false;
        }
        double threshold = Math.max(0.0, edgeThreshold);
        for (Direction dir : HORIZONTAL_DIRECTIONS) {
            Vec3 testPos = player.position().add(dir.getStepX() * threshold, 0.0, dir.getStepZ() * threshold);
            if (wouldBeCloseToFallOff(player, testPos) || isLeadingEdgeOverAir(player, testPos, dir)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates North, South, East, and West to determine which horizontal direction has the nearest
     * cliff or edge relative to the player's current position.
     *
     * @param player the local player to evaluate
     * @return the nearest horizontal {@link Direction}, or {@code null} if no cliff/edge is found within range or player is null
     */
    public static Direction getNearestEdgeDirection(LocalPlayer player) {
        if (player == null || player.level() == null) {
            return null;
        }

        Direction nearest = null;
        double minDistance = Double.MAX_VALUE;
        double maxSearchDist = 2.5;
        double stepSize = 0.05;

        // First check outward steps along each cardinal direction
        for (Direction dir : HORIZONTAL_DIRECTIONS) {
            for (double dist = stepSize; dist <= maxSearchDist; dist += stepSize) {
                Vec3 testPos = player.position().add(dir.getStepX() * dist, 0.0, dir.getStepZ() * dist);
                if (isLeadingEdgeOverAir(player, testPos, dir)) {
                    if (dist < minDistance) {
                        minDistance = dist;
                        nearest = dir;
                    }
                    break;
                }
            }
        }

        // If player already overhangs at current position, check which leading edge is over air
        if (nearest == null) {
            for (Direction dir : HORIZONTAL_DIRECTIONS) {
                if (isLeadingEdgeOverAir(player, player.position(), dir)) {
                    return dir;
                }
            }
        }

        return nearest;
    }

    private static boolean isLeadingEdgeOverAir(LocalPlayer player, Vec3 position, Direction dir) {
        if (player == null || position == null || player.level() == null) {
            return false;
        }
        EntityDimensions dimensions = player.getDimensions(player.getPose());
        AABB box = dimensions.makeBoundingBox(position);
        double checkY = box.minY - 0.1;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;
        double inset = 0.08;

        double[][] corners = switch (dir) {
            case NORTH -> new double[][]{
                    {centerX, box.minZ},
                    {box.minX + inset, box.minZ},
                    {box.maxX - inset, box.minZ}
            };
            case SOUTH -> new double[][]{
                    {centerX, box.maxZ},
                    {box.minX + inset, box.maxZ},
                    {box.maxX - inset, box.maxZ}
            };
            case WEST -> new double[][]{
                    {box.minX, centerZ},
                    {box.minX, box.minZ + inset},
                    {box.minX, box.maxZ - inset}
            };
            case EAST -> new double[][]{
                    {box.maxX, centerZ},
                    {box.maxX, box.minZ + inset},
                    {box.maxX, box.maxZ - inset}
            };
            default -> new double[][]{{centerX, centerZ}};
        };
        for (double[] corner : corners) {
            mpos.set(corner[0], checkY, corner[1]);
            BlockState state = player.level().getBlockState(mpos);
            if (state.isAir() || state.getCollisionShape(player.level(), mpos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static double horizontalSpeed(LocalPlayer player) {
        if (player == null) {
            return 0.0;
        }

        Vec3 velocity = player.getDeltaMovement();
        return Math.hypot(velocity.x, velocity.z);
    }

    public static Vec3 movementInputToWorld(LocalPlayer player, MovementInputEvent event) {
        if (player == null || event == null) {
            return Vec3.ZERO;
        }

        float forward = impulse(event.isForward(), event.isBackward());
        float sideways = impulse(event.isRight(), event.isLeft());
        if (forward == 0.0f && sideways == 0.0f) {
            return Vec3.ZERO;
        }

        double rad = Math.toRadians(player.getYRot());
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double x = sideways * cos - forward * sin;
        double z = forward * cos + sideways * sin;
        return new Vec3(x, 0.0, z);
    }

    public static boolean wouldBeCloseToFallOff(LocalPlayer player, Vec3 position) {
        if (player == null || position == null) {
            return false;
        }

        EntityDimensions dimensions = player.getDimensions(player.getPose());
        AABB hitbox = dimensions.makeBoundingBox(position)
                .inflate(-0.05, 0.0, -0.05)
                .move(0.0, player.fallDistance - STEP_HEIGHT, 0.0);
        return player.level().noCollision(player, hitbox);
    }

    private static boolean wouldDiagonalFall(
            LocalPlayer player,
            Vec3 movementDirection,
            Vec3 horizontalVelocity,
            double distance,
            double firstLead
    ) {
        Vec3 base = player.position().add(horizontalVelocity.scale(firstLead));
        Vec3 predicted = player.position().add(horizontalVelocity.scale(firstLead + 0.5));
        Vec3 predictedSecond = player.position().add(horizontalVelocity.scale(firstLead + 1.0));

        if (wouldFallAlong(player, base, movementDirection, distance)
                || wouldFallAlong(player, predicted, movementDirection, distance)
                || wouldFallAlong(player, predictedSecond, movementDirection, distance)) {
            return true;
        }

        Vec3 xAxis = Math.abs(movementDirection.x) > MIN_AXIS_COMPONENT
                ? new Vec3(Math.signum(movementDirection.x), 0.0, 0.0)
                : Vec3.ZERO;
        Vec3 zAxis = Math.abs(movementDirection.z) > MIN_AXIS_COMPONENT
                ? new Vec3(0.0, 0.0, Math.signum(movementDirection.z))
                : Vec3.ZERO;

        return (xAxis != Vec3.ZERO && wouldFallAlong(player, predicted, xAxis, distance))
                || (zAxis != Vec3.ZERO && wouldFallAlong(player, predicted, zAxis, distance))
                || (xAxis != Vec3.ZERO && zAxis != Vec3.ZERO
                && wouldFallAlong(player, predicted, xAxis.add(zAxis).normalize(), distance));
    }

    private static boolean wouldFallAlong(LocalPlayer player, Vec3 base, Vec3 direction, double distance) {
        if (player == null || base == null || direction == null || direction.lengthSqr() < MIN_INPUT_LENGTH_SQUARED) {
            return false;
        }

        Vec3 normalized = direction.normalize();
        double clampedDistance = Math.max(0.0, distance);
        if (clampedDistance <= 1.0E-7) {
            return wouldBeCloseToFallOff(player, base);
        }

        for (int i = 1; i <= 3; i++) {
            double scale = clampedDistance * i / 3.0;
            Vec3 checkedPos = base.add(normalized.x * scale, 0.0, normalized.z * scale);
            if (wouldBeCloseToFallOff(player, checkedPos)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isDiagonal(MovementInputEvent event) {
        return event != null
                && event.isForward() != event.isBackward()
                && event.isLeft() != event.isRight();
    }

    private static float impulse(boolean positive, boolean negative) {
        if (positive == negative) return 0.0f;
        return positive ? 1.0f : -1.0f;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }

    public enum RecoveryMode {
        STOP,
        INVERT,
        CENTER
    }

    public static final class EdgeRecovery {
        private Vec3 center;
        private int overwriteTicks;
        private int sneakTicks;

        private static Vec3 blockBottomCenter(LocalPlayer player) {
            return new Vec3(
                    player.blockPosition().getX() + 0.5,
                    player.blockPosition().getY(),
                    player.blockPosition().getZ() + 0.5
            );
        }

        private static double horizontalDistanceSquared(Vec3 a, Vec3 b) {
            double x = a.x - b.x;
            double z = a.z - b.z;
            return x * x + z * z;
        }

        public boolean handleDiagonalRescue(LocalPlayer player, MovementInputEvent event, double edgeDistance) {
            if (player == null || event == null) {
                return false;
            }

            updateSafeCenter(player);

            EdgeCheck edgeCheck = checkEdge(player, event, edgeDistance);
            if (edgeCheck.diagonalRescue() && shouldOverwriteInput(player)) {
                overwriteTicks = Math.max(overwriteTicks, DIAGONAL_RESCUE_TICKS);
            }

            if (overwriteTicks <= 0) {
                return false;
            }

            overwriteTicks--;
            applyInputTowardsCenter(player, event);
            event.setJump(false);
            event.setSneak(true);
            event.setSprint(false);

            updateSafeCenter(player);
            return true;
        }

        public boolean handleOnEdge(
                LocalPlayer player,
                MovementInputEvent event,
                double edgeDistance,
                RecoveryMode mode,
                int keepTicks,
                int requestedSneakTicks,
                boolean jump
        ) {
            if (player == null || event == null) {
                return false;
            }

            updateSafeCenter(player);

            double distance = Math.min(horizontalSpeed(player), Math.max(0.0, edgeDistance));
            EdgeCheck edgeCheck = checkEdge(player, event, distance);
            if (edgeCheck.closeToEdge() && shouldOverwriteInput(player)) {
                overwriteTicks = Math.max(overwriteTicks, Math.max(1, keepTicks));
                sneakTicks = Math.max(sneakTicks, Math.max(0, requestedSneakTicks));
            }

            boolean applied = false;
            if (overwriteTicks > 0) {
                overwriteTicks--;
                applyRecoveryInput(player, event, mode);
                event.setJump(jump);
                event.setSprint(false);
                applied = true;
            }

            if (sneakTicks > 0) {
                sneakTicks--;
                event.setSneak(true);
                applied = true;
            }

            updateSafeCenter(player);
            return applied;
        }

        public void reset() {
            center = null;
            overwriteTicks = 0;
            sneakTicks = 0;
        }

        private boolean shouldOverwriteInput(LocalPlayer player) {
            Vec3 center = this.center;
            if (center == null) {
                return true;
            }

            Vec3 horizontalVelocity = new Vec3(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
            if (horizontalVelocity.horizontalDistanceSqr() <= 0.02 * 0.02) {
                return true;
            }

            double currentDistance = horizontalDistanceSquared(center, player.position());
            double nextDistance = horizontalDistanceSquared(center, player.position().add(horizontalVelocity));
            return nextDistance > currentDistance;
        }

        private void applyRecoveryInput(LocalPlayer player, MovementInputEvent event, RecoveryMode mode) {
            RecoveryMode resolvedMode = mode != null ? mode : RecoveryMode.CENTER;
            if (resolvedMode == RecoveryMode.INVERT) {
                boolean forward = event.isForward();
                boolean backward = event.isBackward();
                boolean left = event.isLeft();
                boolean right = event.isRight();
                event.setForward(backward);
                event.setBackward(forward);
                event.setLeft(right);
                event.setRight(left);
                event.setJump(false);
                return;
            }

            if (resolvedMode == RecoveryMode.STOP && horizontalSpeed(player) <= 0.05) {
                event.setForward(false);
                event.setBackward(false);
                event.setLeft(false);
                event.setRight(false);
                event.setJump(false);
                return;
            }

            applyInputTowardsCenter(player, event);
        }

        private void applyInputTowardsCenter(LocalPlayer player, MovementInputEvent event) {
            Vec3 center = this.center != null ? this.center : blockBottomCenter(player);
            Vec3 toCenter = center.subtract(player.position());
            Vec3 horizontal = new Vec3(toCenter.x, 0.0, toCenter.z);

            if (horizontal.lengthSqr() < 0.015 * 0.015) {
                event.setForward(false);
                event.setBackward(false);
                event.setLeft(false);
                event.setRight(false);
                return;
            }

            Vec3 direction = horizontal.normalize();
            double rad = Math.toRadians(player.getYRot());
            double sin = Math.sin(rad);
            double cos = Math.cos(rad);
            double localForward = direction.z * cos - direction.x * sin;
            double localSideways = direction.x * cos + direction.z * sin;

            boolean forward = localForward > CENTER_DEAD_ANGLE_COS;
            boolean backward = localForward < -CENTER_DEAD_ANGLE_COS;
            boolean left = localSideways < -CENTER_DEAD_ANGLE_COS;
            boolean right = localSideways > CENTER_DEAD_ANGLE_COS;

            if (!forward && !backward && !left && !right) {
                if (Math.abs(localForward) >= Math.abs(localSideways)) {
                    forward = localForward > 0.0;
                    backward = localForward < 0.0;
                } else {
                    left = localSideways < 0.0;
                    right = localSideways > 0.0;
                }
            }

            event.setForward(forward);
            event.setBackward(backward);
            event.setLeft(left);
            event.setRight(right);
        }

        private void updateSafeCenter(LocalPlayer player) {
            Vec3 blockCenter = blockBottomCenter(player);
            if (!wouldBeCloseToFallOff(player, blockCenter)) {
                center = blockCenter;
            }
        }
    }

    public record EdgeCheck(boolean closeToEdge, boolean diagonalRescue, Vec3 movementDirection) {
        private static EdgeCheck empty() {
            return new EdgeCheck(false, false, Vec3.ZERO);
        }
    }
}
