/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autoanchor;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class AutoAnchorInteractionUtil {
    private static final AABB UNIT_CUBE = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    private AutoAnchorInteractionUtil() {
    }

    public static Vec3 anchorVec(BlockPos pos) {
        return Vec3.atCenterOf(pos);
    }

    public static boolean hasSolidPlaceSupport(Level level, BlockPos placePos) {
        if (level == null || placePos == null) return false;
        for (Direction direction : Direction.values()) {
            BlockPos support = placePos.relative(direction);
            if (!level.isInWorldBounds(support)) continue;

            BlockState state = level.getBlockState(support);
            if (!state.isAir() && !state.canBeReplaced() && !state.getCollisionShape(level, support).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The click on the block at {@code pos} that a ray from {@code eye} towards {@code aim} makes: the face the ray
     * enters through and the point on it. Null when the eye is inside the block or the ray stops short of it.
     */
    public static BlockHitResult clipBlock(Vec3 eye, Vec3 aim, BlockPos pos) {
        if (eye == null || aim == null || pos == null) return null;
        BlockHitResult hit = AABB.clip(List.of(UNIT_CUBE), eye, aim, pos);
        return hit == null ? null : new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false);
    }

    /**
     * Whether a ray from {@code eye} along {@code direction} enters the unit cube at {@code pos} through
     * {@code face}. This is the test GrimAC's RotationPlace runs against the rotation the server last saw.
     */
    public static boolean rayEntersFace(Vec3 eye, Vec3 direction, BlockPos pos, Direction face, double range) {
        if (eye == null || direction == null || pos == null || face == null) return false;
        BlockHitResult hit = AABB.clip(List.of(UNIT_CUBE), eye, eye.add(direction.scale(range)), pos);
        return hit != null && hit.getDirection() == face;
    }

    /** Whether {@code eye} is on the outer side of {@code face} of the block at {@code pos}. */
    public static boolean isFaceFacingEye(Vec3 eye, BlockPos pos, Direction face) {
        if (eye == null || pos == null || face == null) return false;
        return switch (face) {
            case DOWN -> eye.y <= pos.getY();
            case UP -> eye.y >= pos.getY() + 1.0;
            case NORTH -> eye.z <= pos.getZ();
            case SOUTH -> eye.z >= pos.getZ() + 1.0;
            case WEST -> eye.x <= pos.getX();
            case EAST -> eye.x >= pos.getX() + 1.0;
        };
    }

    public static BlockHitResult getInteractResult(
            Level level,
            LocalPlayer player,
            BlockPos pos,
            boolean existingAnchor,
            boolean allowAirPlace,
            AutoAnchorPlacementMode mode,
            float range,
            float wallRange
    ) {
        if (existingAnchor || allowAirPlace) {
            return getAnchorInteractResult(level, player, pos, mode, range, wallRange);
        }
        return getPlaceSupportInteract(level, player, pos, mode, range, wallRange);
    }

    public static BlockHitResult getAnchorInteractResult(
            Level level,
            LocalPlayer player,
            BlockPos pos,
            AutoAnchorPlacementMode mode,
            float range,
            float wallRange
    ) {
        if (mode == AutoAnchorPlacementMode.STRICT) {
            return getStrictAnchorInteract(level, player, pos, range, wallRange);
        }
        if (mode == AutoAnchorPlacementMode.LEGIT) {
            return getLegitAnchorInteract(level, player, pos, range, wallRange);
        }
        return getDefaultAnchorInteract(level, player, pos, range, wallRange);
    }

    /** Aims at the middle of the block and clicks the face that ray enters through. */
    public static BlockHitResult getDefaultAnchorInteract(Level level,
                                                          LocalPlayer player,
                                                          BlockPos pos,
                                                          float range,
                                                          float wallRange) {
        if (level == null || player == null || pos == null) return null;

        Vec3 eyes = player.getEyePosition();
        BlockHitResult hit = clipBlock(eyes, Vec3.atCenterOf(pos), pos);
        return acceptable(level, player, eyes, hit, pos, range, wallRange) ? hit : null;
    }

    /** The centre of the nearest block face that looks at the eye. */
    public static BlockHitResult getStrictAnchorInteract(Level level,
                                                         LocalPlayer player,
                                                         BlockPos pos,
                                                         float range,
                                                         float wallRange) {
        if (level == null || player == null || pos == null) return null;

        Vec3 eyes = player.getEyePosition();
        double bestDistance = Double.MAX_VALUE;
        BlockHitResult best = null;

        for (Direction direction : Direction.values()) {
            if (!isFaceFacingEye(eyes, pos, direction)) continue;
            Vec3 normal = Vec3.atLowerCornerOf(direction.getUnitVec3i());
            Vec3 sample = Vec3.atCenterOf(pos).add(normal.scale(0.5));
            double distanceSq = eyes.distanceToSqr(sample);
            if (distanceSq >= bestDistance) continue;
            BlockHitResult hit = new BlockHitResult(sample, direction, pos, false);
            if (!acceptable(level, player, eyes, hit, pos, range, wallRange)) continue;

            bestDistance = distanceSq;
            best = hit;
        }

        return best;
    }

    /** The nearest of a grid of points inside the block, clicked through whichever face the ray to it enters. */
    public static BlockHitResult getLegitAnchorInteract(Level level,
                                                        LocalPlayer player,
                                                        BlockPos pos,
                                                        float range,
                                                        float wallRange) {
        if (level == null || player == null || pos == null) return null;

        Vec3 eyes = player.getEyePosition();
        double bestDistance = Double.MAX_VALUE;
        BlockHitResult best = null;
        for (float x = 0.1f; x <= 0.9f; x += 0.2f) {
            for (float y = 0.1f; y <= 0.9f; y += 0.2f) {
                for (float z = 0.1f; z <= 0.9f; z += 0.2f) {
                    Vec3 point = new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    BlockHitResult hit = clipBlock(eyes, point, pos);
                    if (hit == null) continue;
                    double distanceSq = eyes.distanceToSqr(hit.getLocation());
                    if (distanceSq >= bestDistance) continue;
                    if (!acceptable(level, player, eyes, hit, pos, range, wallRange)) continue;

                    bestDistance = distanceSq;
                    best = hit;
                }
            }
        }
        return best;
    }

    public static BlockHitResult getPlaceSupportInteract(Level level,
                                                         LocalPlayer player,
                                                         BlockPos placePos,
                                                         AutoAnchorPlacementMode mode,
                                                         float range,
                                                         float wallRange) {
        if (level == null || player == null || placePos == null) return null;

        Vec3 eyes = player.getEyePosition();
        double bestDistance = Double.MAX_VALUE;
        BlockHitResult best = null;
        for (Direction direction : Direction.values()) {
            BlockPos support = placePos.relative(direction);
            if (!level.isInWorldBounds(support)) continue;

            BlockState state = level.getBlockState(support);
            if (state.isAir() || state.canBeReplaced() || state.getCollisionShape(level, support).isEmpty()) {
                continue;
            }

            Direction face = direction.getOpposite();
            if (!isFaceFacingEye(eyes, support, face)) continue;
            BlockHitResult candidate = mode == AutoAnchorPlacementMode.LEGIT
                    ? getBestSupportFacePoint(level, player, support, face, range, wallRange)
                    : getSupportFaceCenter(level, player, support, face, range, wallRange);
            if (candidate == null) continue;

            double distanceSq = eyes.distanceToSqr(candidate.getLocation());
            if (distanceSq < bestDistance) {
                bestDistance = distanceSq;
                best = candidate;
            }
        }
        return best;
    }

    private static BlockHitResult getSupportFaceCenter(Level level,
                                                       LocalPlayer player,
                                                       BlockPos support,
                                                       Direction face,
                                                       float range,
                                                       float wallRange) {
        Vec3 eyes = player.getEyePosition();
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        Vec3 hitVec = Vec3.atCenterOf(support).add(normal.scale(0.5));
        BlockHitResult hit = new BlockHitResult(hitVec, face, support, false);
        return acceptable(level, player, eyes, hit, support, range, wallRange) ? hit : null;
    }

    private static BlockHitResult getBestSupportFacePoint(Level level,
                                                          LocalPlayer player,
                                                          BlockPos support,
                                                          Direction face,
                                                          float range,
                                                          float wallRange) {
        Vec3 eyes = player.getEyePosition();
        double bestDistance = Double.MAX_VALUE;
        Vec3 bestPoint = null;

        for (float a = 0.2f; a <= 0.8f; a += 0.3f) {
            for (float b = 0.2f; b <= 0.8f; b += 0.3f) {
                Vec3 point = pointOnFace(support, face, a, b);
                double distanceSq = eyes.distanceToSqr(point);
                if (distanceSq >= bestDistance) continue;
                if (!acceptable(level, player, eyes, new BlockHitResult(point, face, support, false), support,
                        range, wallRange)) continue;
                bestDistance = distanceSq;
                bestPoint = point;
            }
        }

        return bestPoint == null ? null : new BlockHitResult(bestPoint, face, support, false);
    }

    private static Vec3 pointOnFace(BlockPos pos, Direction face, float a, float b) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        return switch (face) {
            case DOWN -> new Vec3(x + a, y, z + b);
            case UP -> new Vec3(x + a, y + 1.0, z + b);
            case NORTH -> new Vec3(x + a, y + b, z);
            case SOUTH -> new Vec3(x + a, y + b, z + 1.0);
            case WEST -> new Vec3(x, y + a, z + b);
            case EAST -> new Vec3(x + 1.0, y + a, z + b);
        };
    }

    /** In reach, and either in plain sight or close enough for the wall range. */
    private static boolean acceptable(Level level,
                                      LocalPlayer player,
                                      Vec3 eyes,
                                      BlockHitResult hit,
                                      BlockPos expectedBlock,
                                      float range,
                                      float wallRange) {
        if (hit == null) return false;
        Vec3 point = hit.getLocation();
        double distanceSq = eyes.distanceToSqr(point);
        if (distanceSq > (double) range * range) return false;

        BlockHitResult wall = level.clip(new ClipContext(
                eyes,
                point,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (wall == null || wall.getType() != HitResult.Type.BLOCK || expectedBlock.equals(wall.getBlockPos())) {
            return true;
        }
        return distanceSq <= (double) wallRange * wallRange;
    }

    public static int anchorCharges(BlockState state) {
        if (state == null || !state.is(Blocks.RESPAWN_ANCHOR)) return 0;
        return state.getValue(RespawnAnchorBlock.CHARGE);
    }

    public static boolean isFullyCharged(int charges) {
        return charges >= RespawnAnchorBlock.MAX_CHARGES;
    }

    public static InteractionHand heldAnchorHand(LocalPlayer player) {
        if (player == null) return null;
        if (player.getOffhandItem().is(Items.RESPAWN_ANCHOR)) return InteractionHand.OFF_HAND;
        if (player.getMainHandItem().is(Items.RESPAWN_ANCHOR)) return InteractionHand.MAIN_HAND;
        return null;
    }

    public static InteractionHand heldGlowstoneHand(LocalPlayer player) {
        if (player == null) return null;
        if (player.getOffhandItem().is(Items.GLOWSTONE)) return InteractionHand.OFF_HAND;
        if (player.getMainHandItem().is(Items.GLOWSTONE)) return InteractionHand.MAIN_HAND;
        return null;
    }

    /**
     * The hand that detonates an anchor with {@code charges} charges, or null when none can: glowstone in the
     * offhand makes the anchor's main-hand use pass to it, and the offhand then only recharges an anchor that is not
     * yet full. A hand holding glowstone charges instead of detonating, so the other hand is used.
     */
    public static InteractionHand detonatorHand(LocalPlayer player, int charges) {
        if (player == null) return null;
        if (isGlowstone(player.getOffhandItem())) {
            return isFullyCharged(charges) ? InteractionHand.OFF_HAND : null;
        }
        if (!isGlowstone(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        return InteractionHand.OFF_HAND;
    }

    public static boolean isAnchor(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.RESPAWN_ANCHOR);
    }

    public static boolean isGlowstone(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(Items.GLOWSTONE);
    }

    public static boolean isDetonator(ItemStack stack) {
        return stack == null || stack.isEmpty() || !stack.is(Items.GLOWSTONE);
    }
}
