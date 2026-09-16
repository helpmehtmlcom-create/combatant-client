/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.raycast;

import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.LiquidInteract;
import combatant.client.util.aiming.data.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Predicate;

/**
 * Centralized raycast helpers for entities, blocks, voxel clipping, and line-of-sight.
 */
public enum RaycastUtil {
    ;

    public static final double VANILLA_BLOCK_REACH = 4.5;
    public static final double VANILLA_ENTITY_REACH = 3.0;
    public static final double STRICT_ANTI_CHEAT_REACH = 3.0;
    public static final double DEFAULT_REACH = VANILLA_BLOCK_REACH;
    public static EntityHitResult findEntityInCrosshair(Entity viewer,
                                                        double range,
                                                        float yaw,
                                                        float pitch,
                                                        Predicate<Entity> predicate) {
        if (viewer == null) return null;

        Vec3 eyes = viewer.getEyePosition();
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw).normalize();
        Vec3 end = eyes.add(dir.scale(range));

        AABB box = viewer.getBoundingBox()
                .inflate(dir.x * range, dir.y * range, dir.z * range)
                .inflate(1.0, 1.0, 1.0);

        Predicate<Entity> base = EntitySelector.CAN_BE_PICKED;
        Predicate<Entity> filter = predicate != null ? base.and(predicate) : base;

        return ProjectileUtil.getEntityHitResult(viewer, eyes, end, box, filter, range * range);
    }

    public static EntityHitResult findEntityInCrosshair(Entity viewer,
                                                        double range,
                                                        float yaw,
                                                        float pitch) {
        return findEntityInCrosshair(viewer, range, yaw, pitch, null);
    }

    public static EntityHitResult isLookingAtEntity(Entity from,
                                                    Entity to,
                                                    float yaw,
                                                    float pitch,
                                                    double range,
                                                    double throughWallsRange) {
        if (from == null || to == null) return null;
        EntityHitResult hit = findEntityInCrosshair(from, range, yaw, pitch, e -> e == to);
        if (hit == null || hit.getEntity() != to) return null;

        Vec3 eyes = from.getEyePosition();
        double distSq = eyes.distanceToSqr(hit.getLocation());

        if (distSq <= throughWallsRange * throughWallsRange) {
            return hit;
        }

        if (distSq <= range * range && hasLineOfSight(from, hit.getLocation())) {
            return hit;
        }

        return null;
    }

    public static EntityHitResult isLookingAtEntity(Entity from,
                                                    Entity to,
                                                    Rotation rotation,
                                                    double range,
                                                    double throughWallsRange) {
        if (rotation == null) return null;
        return isLookingAtEntity(from, to, rotation.yaw(), rotation.pitch(), range, throughWallsRange);
    }

    public static boolean hasLineOfSight(Entity viewer, Vec3 target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || viewer == null) return false;

        Vec3 eyes = viewer.getEyePosition();
        HitResult res = mc.level.clip(new ClipContext(
                eyes,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                viewer
        ));
        return res.getType() == HitResult.Type.MISS;
    }

    public static boolean hasLineOfSightPoint(Vec3 from, Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || from == null || to == null) return false;

        CollisionContext shapeContext = mc.player != null ? CollisionContext.of(mc.player) : CollisionContext.empty();
        HitResult res = mc.level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                shapeContext
        ));
        return res.getType() == HitResult.Type.MISS;
    }

    /**
     * Line of sight / wall collision detection from player eyes to a target point.
     *
     * @param player the viewing player
     * @param point the world coordinate
     * @return true if unobstructed by block colliders
     */
    public static boolean canSee(Player player, Vec3 point) {
        if (player == null || point == null) {
            return false;
        }
        Level level = player.level();
        if (level == null) {
            return false;
        }

        Vec3 eyes = player.getEyePosition();
        HitResult res = level.clip(new ClipContext(
                eyes,
                point,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (res.getType() == HitResult.Type.MISS) {
            return true;
        }

        if (res instanceof BlockHitResult blockHit) {
            if (blockHit.getLocation().distanceToSqr(point) < 0.01) {
                return true;
            }
            return blockHit.getBlockPos().equals(BlockPos.containing(point));
        }

        return false;
    }

    /**
     * Line of sight detection with maximum range and through-wall penetration bounds.
     *
     * @param player the viewing player
     * @param point the world coordinate
     * @param maxRange maximum interaction range
     * @param wallRange range within which walls are ignored
     * @return true if visible or within wall range
     */
    public static boolean canSee(Player player, Vec3 point, double maxRange, double wallRange) {
        if (player == null || point == null) return false;
        Vec3 eyes = player.getEyePosition();
        double distSq = eyes.distanceToSqr(point);
        if (distSq <= wallRange * wallRange) {
            return true;
        }
        if (distSq > maxRange * maxRange) {
            return false;
        }
        return canSee(player, point);
    }

    public static boolean canSee(Player player, BlockPos pos) {
        if (pos == null) return false;
        return canSee(player, Vec3.atCenterOf(pos));
    }

    public static boolean canSee(Player player, Entity entity) {
        if (player == null || entity == null) return false;
        return canSee(player, entity.getEyePosition());
    }

    /**
     * 3D voxel clipping raycast against a specific target block.
     * Evaluates exact voxel shapes and block interaction overrides (e.g. stairs, slabs, chests).
     *
     * @param level level instance
     * @param from ray start point
     * @param to ray end point
     * @param target target block position
     * @return BlockHitResult if clipped, or null if missed
     */
    public static BlockHitResult raycastBlock(Level level, Vec3 from, Vec3 to, BlockPos target) {
        if (level == null || from == null || to == null || target == null) {
            return null;
        }
        BlockState state = level.getBlockState(target);
        if (state.isAir()) {
            return null;
        }
        VoxelShape shape = state.getShape(level, target);
        if (shape.isEmpty()) {
            return null;
        }
        return level.clipWithInteractionOverride(from, to, target, shape, state);
    }

    /**
     * 3D voxel clipping raycast with optional intermediate obstruction verification.
     *
     * @param level level instance
     * @param from ray start point
     * @param to ray end point
     * @param target target block position
     * @param checkObstruction whether intermediate blocks obstruct the ray
     * @return BlockHitResult if clipped and unobstructed, or null
     */
    public static BlockHitResult raycastBlock(Level level, Vec3 from, Vec3 to, BlockPos target, boolean checkObstruction) {
        BlockHitResult hit = raycastBlock(level, from, to, target);
        if (hit == null) {
            return null;
        }
        if (checkObstruction) {
            HitResult obstacle = level.clip(new ClipContext(
                    from,
                    hit.getLocation(),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    CollisionContext.empty()
            ));
            if (obstacle.getType() == HitResult.Type.BLOCK && obstacle instanceof BlockHitResult obstacleHit) {
                if (!obstacleHit.getBlockPos().equals(target) && obstacleHit.getLocation().distanceToSqr(hit.getLocation()) > 0.01) {
                    return null;
                }
            }
        }
        return hit;
    }

    /**
     * Performs raycasting from player's eyes along (yaw, pitch) up to reach.
     * Automatically includes liquids if includeLiquids is true or LiquidInteract is enabled.
     */
    public static BlockHitResult raycastBlock(Player player, float yaw, float pitch, double reach, boolean includeLiquids) {
        if (player == null) return null;
        Level level = player.level();
        if (level == null) return null;

        Vec3 eyes = player.getEyePosition();
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw).normalize();
        Vec3 end = eyes.add(dir.scale(reach));

        boolean checkLiquids = includeLiquids || LiquidInteract.isLiquidInteractEnabled();
        if (checkLiquids) {
            BlockHitResult liquidHit = LiquidInteract.raycast(eyes, end);
            if (liquidHit != null && liquidHit.getType() != HitResult.Type.MISS) {
                return liquidHit;
            }
        }

        ClipContext context = new ClipContext(
                eyes,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        );
        return level.clip(context);
    }

    public static BlockHitResult raycastBlock(Player player, float yaw, float pitch, double reach) {
        return raycastBlock(player, yaw, pitch, reach, false);
    }

    public static BlockHitResult raycastBlock(Player player, BlockPos target, double maxReach) {
        if (player == null || target == null) return null;
        Level level = player.level();
        if (level == null) return null;
        Vec3 eyes = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(target);
        if (eyes.distanceToSqr(center) > maxReach * maxReach) return null;
        return raycastBlock(level, eyes, center, target, true);
    }

    public static EntityHitResult raycastEntity(Player player, float yaw, float pitch, double reach, Predicate<Entity> predicate) {
        return findEntityInCrosshair(player, reach, yaw, pitch, predicate);
    }

    public static EntityHitResult raycastEntity(Player player, float yaw, float pitch, double reach) {
        return findEntityInCrosshair(player, reach, yaw, pitch, null);
    }

    /**
     * Accurate hit result generation for blocks and entities with reach limits.
     * Vanilla block reach is 4.5, entity reach is 3.0; strict anti-cheat reach is 3.0.
     *
     * @param player viewing player
     * @param yaw look yaw
     * @param pitch look pitch
     * @param blockReach max block reach
     * @param entityReach max entity reach
     * @param includeLiquids whether to include liquids in block raycast
     * @return closest hit result or miss
     */
    public static HitResult getHitResult(Player player, float yaw, float pitch, double blockReach, double entityReach, boolean includeLiquids) {
        if (player == null) return null;

        Vec3 eyes = player.getEyePosition();
        BlockHitResult blockHit = raycastBlock(player, yaw, pitch, blockReach, includeLiquids);
        EntityHitResult entityHit = raycastEntity(player, yaw, pitch, entityReach);

        if (blockHit == null || blockHit.getType() == HitResult.Type.MISS) {
            return entityHit;
        }
        if (entityHit == null || entityHit.getType() == HitResult.Type.MISS) {
            return blockHit;
        }

        double blockDistSq = eyes.distanceToSqr(blockHit.getLocation());
        double entityDistSq = eyes.distanceToSqr(entityHit.getLocation());

        return entityDistSq < blockDistSq ? entityHit : blockHit;
    }

    public static HitResult getHitResult(Player player, float yaw, float pitch, double reach) {
        return getHitResult(player, yaw, pitch, reach, reach, LiquidInteract.isLiquidInteractEnabled());
    }

    public static HitResult getHitResult(Player player, boolean strictAntiCheat) {
        if (player == null) return null;
        double blockReach = strictAntiCheat ? STRICT_ANTI_CHEAT_REACH : VANILLA_BLOCK_REACH;
        double entityReach = strictAntiCheat ? STRICT_ANTI_CHEAT_REACH : VANILLA_ENTITY_REACH;
        return getHitResult(player, player.getYRot(), player.getXRot(), blockReach, entityReach, LiquidInteract.isLiquidInteractEnabled());
    }

    public static HitResult getCrosshairHit(Player player) {
        return getHitResult(player, false);
    }

    /**
     * Finds the best visible/closest BlockHitResult for interacting with a target block.
     */
    public static BlockHitResult getBestBlockHitResult(Player player, BlockPos pos, double maxReach, double wallRange) {
        if (player == null || pos == null) return null;
        Level level = player.level();
        if (level == null) return null;

        Vec3 eyes = player.getEyePosition();
        BlockHitResult best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Direction side : Direction.values()) {
            Vec3 normal = Vec3.atLowerCornerOf(side.getUnitVec3i());
            Vec3 faceCenter = Vec3.atCenterOf(pos).add(normal.scale(0.5));
            double distSq = eyes.distanceToSqr(faceCenter);
            if (distSq > maxReach * maxReach) continue;

            boolean visible = distSq <= wallRange * wallRange || canSee(player, faceCenter);
            if (!visible) continue;

            BlockHitResult clip = raycastBlock(level, eyes, faceCenter, pos);
            Vec3 hitVec = clip != null ? clip.getLocation() : faceCenter;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = new BlockHitResult(hitVec, side, pos, false);
            }
        }

        if (best == null && eyes.distanceToSqr(Vec3.atCenterOf(pos)) <= maxReach * maxReach) {
            Direction fallbackSide = player.getEyeY() >= pos.getY() + 0.5 ? Direction.UP : Direction.DOWN;
            return new BlockHitResult(Vec3.atCenterOf(pos), fallbackSide, pos, false);
        }

        return best;
    }
}
