/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.projectile;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.entity.simulation.MountedEntityPrediction;
import combatant.client.util.player.NetworkStatsUtil;
import combatant.client.util.player.simulation.PlayerSimulationCache;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public enum ProjectilePredictionUtil {
    ;

    public static final double ARROW_GRAVITY = 0.05;
    public static final double PEARL_GRAVITY = 0.03;
    public static final double DEFAULT_DRAG = 0.99;

    public static final double WIND_CHARGE_VELOCITY = 1.5;
    public static final double WIND_CHARGE_GRAVITY = 0.0;
    public static final double WIND_CHARGE_DRAG = 1.0;

    public static final double TRIDENT_VELOCITY = 2.5;
    public static final double TRIDENT_GRAVITY = 0.05;
    public static final double TRIDENT_DRAG = 0.99;

    public static final double POTION_VELOCITY = 0.5;
    public static final double POTION_GRAVITY = 0.05;
    public static final double POTION_DRAG = 0.99;

    public static final double SNOWBALL_VELOCITY = 1.5;
    public static final double SNOWBALL_GRAVITY = 0.03;
    public static final double SNOWBALL_DRAG = 0.99;
    /**
     * Calculates initial bow velocity based on pull ticks: Math.min(ticks / 20.0f, 1.0f) * 3.0f.
     */
    public static float getBowVelocity(int ticks) {
        return Math.min(ticks / 20.0f, 1.0f) * 3.0f;
    }

    /**
     * Calculates initial bow velocity based on pull ticks: Math.min(ticks / 20.0f, 1.0f) * 3.0f.
     */
    public static float getBowVelocity(float ticks) {
        return Math.min(ticks / 20.0f, 1.0f) * 3.0f;
    }

    /**
     * Normalizes charge input (either fractional 0.0-1.0 or tick count) to initial velocity.
     */
    public static float getBowVelocityForCharge(float charge) {
        if (charge <= 0.0f) {
            return 0.1f * 3.0f;
        }
        if (charge <= 1.0f) {
            return Math.min(charge, 1.0f) * 3.0f;
        }
        return getBowVelocity(charge);
    }

    /**
     * Unified 3D parabolic trajectory prediction considering gravity and drag.
     */
    public static List<Vec3> predictTrajectory(Vec3 startPos, Vec3 initialVelocity, double gravity, double drag, int maxTicks) {
        List<Vec3> path = new ArrayList<>(maxTicks + 1);
        Vec3 pos = startPos;
        Vec3 vel = initialVelocity;
        path.add(pos);
        for (int i = 0; i < maxTicks; i++) {
            pos = pos.add(vel);
            vel = vel.scale(drag).subtract(0.0, gravity, 0.0);
            path.add(pos);
        }
        return path;
    }

    /**
     * Predicts arrow trajectory from eye position, rotation angle, and bow charge.
     */
    public static List<Vec3> predictArrowTrajectory(Vec3 eyePos, Rotation rotation, float charge, int maxTicks) {
        float velocity = getBowVelocityForCharge(charge);
        double yawRad = Math.toRadians(rotation.yaw());
        double pitchRad = Math.toRadians(rotation.pitch());
        double vX = -Math.sin(yawRad) * Math.cos(pitchRad) * velocity;
        double vY = -Math.sin(pitchRad) * velocity;
        double vZ = Math.cos(yawRad) * Math.cos(pitchRad) * velocity;
        return predictTrajectory(eyePos, new Vec3(vX, vY, vZ), ARROW_GRAVITY, DEFAULT_DRAG, maxTicks);
    }

    /**
     * Predicts ender pearl trajectory from eye position and rotation angle.
     */
    public static List<Vec3> predictPearlTrajectory(Vec3 eyePos, Rotation rotation, int maxTicks) {
        double velocity = 1.5;
        double yawRad = Math.toRadians(rotation.yaw());
        double pitchRad = Math.toRadians(rotation.pitch());
        double vX = -Math.sin(yawRad) * Math.cos(pitchRad) * velocity;
        double vY = -Math.sin(pitchRad) * velocity;
        double vZ = Math.cos(yawRad) * Math.cos(pitchRad) * velocity;
        return predictTrajectory(eyePos, new Vec3(vX, vY, vZ), PEARL_GRAVITY, DEFAULT_DRAG, maxTicks);
    }

    /**
     * Predicts wind charge trajectory from eye position and rotation angle.
     */
    public static List<Vec3> predictWindChargeTrajectory(Vec3 eyePos, Rotation rotation, int maxTicks) {
        if (eyePos == null || rotation == null || maxTicks <= 0) return List.of();
        double yawRad = Math.toRadians(rotation.yaw());
        double pitchRad = Math.toRadians(rotation.pitch());
        double vX = -Math.sin(yawRad) * Math.cos(pitchRad) * WIND_CHARGE_VELOCITY;
        double vY = -Math.sin(pitchRad) * WIND_CHARGE_VELOCITY;
        double vZ = Math.cos(yawRad) * Math.cos(pitchRad) * WIND_CHARGE_VELOCITY;
        return predictTrajectory(eyePos, new Vec3(vX, vY, vZ), WIND_CHARGE_GRAVITY, WIND_CHARGE_DRAG, maxTicks);
    }

    /**
     * Predicts trident trajectory from eye position and rotation angle.
     */
    public static List<Vec3> predictTridentTrajectory(Vec3 eyePos, Rotation rotation, int maxTicks) {
        if (eyePos == null || rotation == null || maxTicks <= 0) return List.of();
        double yawRad = Math.toRadians(rotation.yaw());
        double pitchRad = Math.toRadians(rotation.pitch());
        double vX = -Math.sin(yawRad) * Math.cos(pitchRad) * TRIDENT_VELOCITY;
        double vY = -Math.sin(pitchRad) * TRIDENT_VELOCITY;
        double vZ = Math.cos(yawRad) * Math.cos(pitchRad) * TRIDENT_VELOCITY;
        return predictTrajectory(eyePos, new Vec3(vX, vY, vZ), TRIDENT_GRAVITY, TRIDENT_DRAG, maxTicks);
    }

    /**
     * Predicts splash/lingering potion trajectory from eye position and rotation angle.
     */
    public static List<Vec3> predictPotionTrajectory(Vec3 eyePos, Rotation rotation, int maxTicks) {
        if (eyePos == null || rotation == null || maxTicks <= 0) return List.of();
        double yawRad = Math.toRadians(rotation.yaw());
        // Vanilla ThrowablePotionItem throws with a -20.0 degree pitch adjustment
        double pitchRad = Math.toRadians(rotation.pitch() - 20.0f);
        double vX = -Math.sin(yawRad) * Math.cos(pitchRad) * POTION_VELOCITY;
        double vY = -Math.sin(pitchRad) * POTION_VELOCITY;
        double vZ = Math.cos(yawRad) * Math.cos(pitchRad) * POTION_VELOCITY;
        return predictTrajectory(eyePos, new Vec3(vX, vY, vZ), POTION_GRAVITY, POTION_DRAG, maxTicks);
    }

    /**
     * Predicts snowball trajectory from eye position and rotation angle.
     */
    public static List<Vec3> predictSnowballTrajectory(Vec3 eyePos, Rotation rotation, int maxTicks) {
        if (eyePos == null || rotation == null || maxTicks <= 0) return List.of();
        double yawRad = Math.toRadians(rotation.yaw());
        double pitchRad = Math.toRadians(rotation.pitch());
        double vX = -Math.sin(yawRad) * Math.cos(pitchRad) * SNOWBALL_VELOCITY;
        double vY = -Math.sin(pitchRad) * SNOWBALL_VELOCITY;
        double vZ = Math.cos(yawRad) * Math.cos(pitchRad) * SNOWBALL_VELOCITY;
        return predictTrajectory(eyePos, new Vec3(vX, vY, vZ), SNOWBALL_GRAVITY, SNOWBALL_DRAG, maxTicks);
    }

    /**
     * Performs continuous collision detection (CCD) step-by-step with level.clip
     * and bounding box checks for entities matching filter. Returns first hit result or MISS.
     */
    public static HitResult predictImpact(Level level,
                                          Vec3 startPos,
                                          Vec3 initialVelocity,
                                          double gravity,
                                          double drag,
                                          int maxTicks,
                                          Predicate<Entity> filter) {
        if (level == null || startPos == null || initialVelocity == null || maxTicks <= 0) {
            Vec3 fallback = startPos != null ? startPos : Vec3.ZERO;
            return BlockHitResult.miss(fallback, Direction.UP, BlockPos.containing(fallback));
        }

        Vec3 currentPos = startPos;
        Vec3 vel = initialVelocity;

        for (int i = 0; i < maxTicks; i++) {
            Vec3 nextPos = currentPos.add(vel);

            HitResult blockHit = level.clip(new ClipContext(
                    currentPos,
                    nextPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    CollisionContext.empty()
            ));

            Vec3 rayEnd = (blockHit != null && blockHit.getType() != HitResult.Type.MISS)
                    ? blockHit.getLocation()
                    : nextPos;

            AABB stepBox = new AABB(currentPos, rayEnd).inflate(1.0);
            List<Entity> candidateEntities = level.getEntities((Entity) null, stepBox, filter != null ? filter : e -> true);

            Entity closestEntity = null;
            Vec3 closestEntityHitPos = null;
            double closestDistSq = Double.MAX_VALUE;

            for (Entity entity : candidateEntities) {
                if (filter != null && !filter.test(entity)) {
                    continue;
                }
                AABB entityBox = entity.getBoundingBox().inflate(0.3);
                if (i == 0 && entityBox.contains(startPos)) {
                    // Avoid self-collision on launch tick
                    continue;
                }
                var clipHit = entityBox.clip(currentPos, rayEnd);
                if (clipHit.isPresent()) {
                    Vec3 hitPoint = clipHit.get();
                    double distSq = currentPos.distanceToSqr(hitPoint);
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq;
                        closestEntityHitPos = hitPoint;
                        closestEntity = entity;
                    }
                }
            }

            if (closestEntity != null) {
                return new EntityHitResult(closestEntity, closestEntityHitPos);
            }

            if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
                return blockHit;
            }

            currentPos = nextPos;
            vel = vel.scale(drag).subtract(0.0, gravity, 0.0);
        }

        return BlockHitResult.miss(currentPos, Direction.UP, BlockPos.containing(currentPos));
    }

    /**
     * Predicts target position with lead compensation factoring in target velocity and ping interpolation.
     */
    public static Vec3 predictTargetLead(Entity target, double flightTicks, double pingMs) {
        if (target == null) {
            return Vec3.ZERO;
        }
        double pingTicks = Math.max(0.0, pingMs) / 50.0;
        double totalLeadTicks = flightTicks + (pingTicks * 0.5);

        if (target.isPassenger()) {
            return MountedEntityPrediction.predictMountedPosition(target, (int) Math.round(totalLeadTicks));
        }

        if (target instanceof Player player) {
            var sim = PlayerSimulationCache.getSimulationForOtherPlayers(player);
            if (sim != null) {
                var snapshot = sim.getSnapshotAt((int) Math.round(totalLeadTicks));
                if (snapshot != null && snapshot.pos() != null) {
                    return snapshot.pos();
                }
            }
        }

        Vec3 velocity = target.getDeltaMovement();
        return target.position().add(velocity.scale(totalLeadTicks));
    }

    /**
     * Predicts target position with lead compensation using current player's ping.
     */
    public static Vec3 predictTargetLead(Entity target, double flightTicks) {
        Minecraft mc = Minecraft.getInstance();
        int ping = NetworkStatsUtil.getPing(mc);
        return predictTargetLead(target, flightTicks, ping > 0 ? ping : 0.0);
    }

    /**
     * Helper returning optimal pitch and yaw (Rotation) to hit target with a bow,
     * factoring in arrow gravity (0.05), drag (0.99), pull velocity, target velocity, and ping interpolation.
     */
    public static Rotation calculateBowAngle(Vec3 eyePos, Entity target, float charge) {
        if (eyePos == null || target == null) {
            return null;
        }

        float velocity = getBowVelocityForCharge(charge);
        if (velocity <= 0.05f) {
            velocity = 0.1f;
        }

        TrajectoryInfo info = TrajectoryInfo.BOW_FULL_PULL
                .withInitialVelocity(velocity)
                .withGravity(ARROW_GRAVITY)
                .withDrag(DEFAULT_DRAG);

        Minecraft mc = Minecraft.getInstance();
        int ping = NetworkStatsUtil.getPing(mc);
        double pingMs = ping > 0 ? ping : 0.0;

        ProjectileTarget projTarget = createLeadTarget(target, pingMs);
        Rotation rotation = SituationalProjectileAngleCalculator.INSTANCE.calculateAngleFor(info, eyePos, projTarget);
        if (rotation != null) {
            return rotation;
        }

        // Direct analytical ballistic fallback with lead estimation
        double directDist = eyePos.distanceTo(target.position());
        double estTicks = directDist / velocity;
        Vec3 leadPos = predictTargetLead(target, estTicks, pingMs);
        Vec3 aimPos = leadPos.add(0.0, target.getBbHeight() * 0.5, 0.0);
        Vec3 diff = aimPos.subtract(eyePos);
        double hDist = Math.hypot(diff.x, diff.z);
        if (hDist < 0.001) {
            return new Rotation(0.0f, diff.y < 0 ? 90.0f : -90.0f);
        }

        double vel2 = velocity * velocity;
        double vel4 = vel2 * vel2;
        double y = diff.y;
        double sqrtVal = vel4 - ARROW_GRAVITY * (ARROW_GRAVITY * hDist * hDist + 2.0 * y * vel2);
        float pitch;
        if (sqrtVal >= 0.0) {
            double pitchRad = Math.atan((vel2 - Math.sqrt(sqrtVal)) / (ARROW_GRAVITY * hDist));
            pitch = (float) -Math.toDegrees(pitchRad);
        } else {
            pitch = -45.0f;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0f, 90.0f));
    }

    private static ProjectileTarget createLeadTarget(Entity target, double pingMs) {
        double pingTicks = Math.max(0.0, pingMs) / 50.0;
        double pingOffset = pingTicks * 0.5;

        return new ProjectileTarget() {
            @Override
            public Vec3 getPositionInTicks(double ticks) {
                double total = ticks + pingOffset;
                if (target.isPassenger()) {
                    return MountedEntityPrediction.predictMountedPosition(target, (int) Math.round(total));
                }
                if (target instanceof Player player) {
                    var sim = PlayerSimulationCache.getSimulationForOtherPlayers(player);
                    if (sim != null) {
                        var snap = sim.getSnapshotAt((int) Math.round(total));
                        if (snap != null && snap.pos() != null) {
                            return snap.pos();
                        }
                    }
                }
                return target.position().add(target.getDeltaMovement().scale(total));
            }

            @Override
            public AABB getBoxInTicks(double ticks) {
                Vec3 predicted = getPositionInTicks(ticks);
                return target.getBoundingBox().move(predicted.subtract(target.position()));
            }
        };
    }

    public static Rotation calculateForHeldItem(Player player, LivingEntity target, boolean alwaysShowBow) {
        if (player == null || target == null) {
            return null;
        }

        Rotation mainHand = calculateForItem(player, player.getItemInHand(InteractionHand.MAIN_HAND), target, alwaysShowBow);
        if (mainHand != null) {
            return mainHand;
        }

        return calculateForItem(player, player.getItemInHand(InteractionHand.OFF_HAND), target, alwaysShowBow);
    }

    public static Rotation calculateForItem(Player player, ItemStack stack, LivingEntity target, boolean alwaysShowBow) {
        if (player == null || target == null || stack == null || stack.isEmpty()) {
            return null;
        }

        TrajectoryInfo.Typed typed = TrajectoryData.getRenderedTrajectoryInfo(player, stack, alwaysShowBow);
        if (typed == null) {
            return null;
        }

        return SituationalProjectileAngleCalculator.INSTANCE.calculateAngleForEntity(
                typed.info(),
                target,
                player.getEyePosition()
        );
    }

    public static Rotation calculateForCurrentPlayer(ItemStack stack, LivingEntity target, boolean alwaysShowBow) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : calculateForItem(mc.player, stack, target, alwaysShowBow);
    }

    public static LivingEntity getHypotheticalHit(Player player,
                                                  ItemStack stack,
                                                  Rotation rotation,
                                                  Predicate<LivingEntity> predicate) {
        return getHypotheticalHit(player, stack, rotation, predicate, false);
    }

    public static LivingEntity getHypotheticalHit(Player player,
                                                  ItemStack stack,
                                                  Rotation rotation,
                                                  Predicate<LivingEntity> predicate,
                                                  boolean alwaysShowBow) {
        if (player == null || rotation == null || stack == null || stack.isEmpty()) {
            return null;
        }

        TrajectoryInfo.Typed typed = TrajectoryData.getRenderedTrajectoryInfo(player, stack, alwaysShowBow);
        if (typed == null || !(player.level() instanceof net.minecraft.client.multiplayer.ClientLevel world)) {
            return null;
        }

        double velocity = typed.info().initialVelocity();
        float yaw = rotation.yaw();
        float pitch = rotation.pitch();

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double vX = -Math.sin(yawRad) * Math.cos(pitchRad) * velocity;
        double vY = -Math.sin(pitchRad) * velocity;
        double vZ = Math.cos(yawRad) * Math.cos(pitchRad) * velocity;
        Vec3 initialVelocity = new Vec3(vX, vY, vZ);
        if (typed.info().copiesPlayerVelocity()) {
            initialVelocity = initialVelocity.add(player.getDeltaMovement());
        }

        SimulatedArrow arrow = new SimulatedArrow(
                world,
                player.getEyePosition(),
                initialVelocity,
                false
        );

        List<SimulatedTarget> targets = findSimulatedTargets(player, predicate);
        if (targets.isEmpty()) {
            return null;
        }

        for (int i = 0; i < 40; i++) {
            Vec3 lastPos = arrow.getPos();
            HitResult hitResult = arrow.tick();
            Vec3 currentPos = arrow.getPos();

            for (SimulatedTarget target : targets) {
                Vec3 predictedPos = target.getPositionInTicks(i);
                AABB entityBox = target.entity().getBoundingBox()
                        .inflate(0.3)
                        .move(predictedPos.subtract(target.entity().position()));
                if (entityBox.clip(lastPos, currentPos).isPresent()) {
                    return target.entity();
                }
            }

            if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
                break;
            }
        }

        return null;
    }

    private static List<SimulatedTarget> findSimulatedTargets(Player player,
                                                              Predicate<LivingEntity> predicate) {
        List<SimulatedTarget> targets = new ArrayList<>();
        if (player == null || player.level() == null) {
            return targets;
        }

        AABB search = player.getBoundingBox().inflate(160.0);
        for (Entity entity : player.level().getEntities(player, search, entity -> entity instanceof LivingEntity)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living == player || !living.isAlive()) {
                continue;
            }
            if (predicate != null && !predicate.test(living)) {
                continue;
            }

            targets.add(new SimulatedTarget(living));
        }

        return targets;
    }

    private record SimulatedTarget(LivingEntity entity) {
        private Vec3 getPositionInTicks(int ticks) {
            if (entity.isPassenger()) {
                return MountedEntityPrediction.predictMountedPosition(entity, ticks);
            }

            if (entity instanceof Player) {
                return PlayerSimulationCache.getSimulationForOtherPlayers((Player) entity).getSnapshotAt(ticks).pos();
            }

            return entity.position().add(entity.getDeltaMovement().scale(ticks));
        }
    }
}
