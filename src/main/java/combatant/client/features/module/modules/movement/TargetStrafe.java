/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.KillAura;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.combat.SprintController;
import combatant.client.util.player.MovementUtil;
@ModuleInfo(
        id = "targetstrafe",
        displayName = "TargetStrafe",
        category = ModuleCategory.MOVEMENT,
        description = "Automatically circles and strafes around your combat target while maintaining optimal distance."
)
public final class TargetStrafe extends Module {

    private final Minecraft mc = Minecraft.getInstance();
    private final EnumValue<StrafeMode> mode =
            enumMode("mode", StrafeMode.MATRIX, StrafeMode.MATRIX, StrafeMode.GRIM, StrafeMode.ADAPTIVE);
    private final NumberValue<Float> radius =
            num("radius", 2.5f, 0.5f, 6.0f);
    private final NumberValue<Float> speed =
            num("speed", 0.28f, 0.1f, 1.0f);
    private final EnumValue<DirectionMode> directionMode =
            enumMode("direction_mode", DirectionMode.SMART,
                    DirectionMode.SMART, DirectionMode.CLOCKWISE, DirectionMode.COUNTERCLOCKWISE, DirectionMode.RANDOM);
    private final BooleanValue autoJump =
            bool("auto_jump", true);
    private final BooleanValue onlyKeyPressed =
            bool("only_key_pressed", false);

    private int currentDirection = 1;
    private long lastDirectionSwitchTime = 0;

    private static float resolveControlYaw() {
        var rotation = RotationManager.INSTANCE.getCurrentRotation();
        if (rotation != null) {
            return rotation.yaw();
        }

        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.getYRot() : 0.0f;
    }

    private static boolean hasForwardMovement(float angleDiff) {
        return angleDiff > -67.5f && angleDiff < 67.5f;
    }
    @Override
    public void onEnable() {
        currentDirection = 1;
        lastDirectionSwitchTime = 0;
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || (!isGrimMode() && !isAdaptiveMode())) return;

        LocalPlayer player = mc.player;
        LivingEntity target = currentTarget();
        if (player == null || mc.level == null || target == null || !target.isAlive()) return;
        if (onlyKeyPressed.get() && !isAnyMovementKeyPressed()) return;

        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();
        double r = radius.get();
        int dirMultiplier = resolveDirectionMultiplier(player);

        // Calculate next orbit target point
        Vec3 nextPoint = calculateOrbitPoint(playerPos, targetPos, r, dirMultiplier);
        checkObstacleAvoidance(player);

        Vec3 direction = nextPoint.subtract(playerPos);
        if (direction.lengthSqr() < 1.0E-6) return;
        direction = direction.normalize();

        float yaw = resolveControlYaw();
        float movementAngle = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0f;
        float angleDiff = RotationUtil.wrapDegrees(movementAngle - yaw);

        boolean forward = false;
        boolean backward = false;
        boolean left = false;
        boolean right = false;

        if (angleDiff >= -22.5f && angleDiff < 22.5f) {
            forward = true;
        } else if (angleDiff >= 22.5f && angleDiff < 67.5f) {
            forward = true;
            right = true;
        } else if (angleDiff >= 67.5f && angleDiff < 112.5f) {
            right = true;
        } else if (angleDiff >= 112.5f && angleDiff < 157.5f) {
            backward = true;
            right = true;
        } else if (angleDiff >= -67.5f && angleDiff < -22.5f) {
            forward = true;
            left = true;
        } else if (angleDiff >= -112.5f && angleDiff < -67.5f) {
            left = true;
        } else if (angleDiff >= -157.5f && angleDiff < -112.5f) {
            backward = true;
            left = true;
        } else {
            backward = true;
        }

        event.setForward(forward);
        event.setBackward(backward);
        event.setLeft(left);
        event.setRight(right);
        event.setSprint(SprintController.INSTANCE.canStartSprinting(player)
                && hasForwardMovement(angleDiff));

        if (autoJump.get() && player.onGround()) {
            event.setJump(true);
        }
    }

    @EventHandler
    private void onTick(GameTickEvent event) {
        if (!isEnabled() || (!isMatrixMode() && !isAdaptiveMode())) return;

        LocalPlayer player = mc.player;
        LivingEntity target = currentTarget();
        if (player == null || mc.level == null || target == null || !target.isAlive()) return;
        if (onlyKeyPressed.get() && !isAnyMovementKeyPressed()) return;

        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();
        double r = radius.get();

        // Automatic obstacle avoidance jump
        if (autoJump.get() && player.onGround()) {
            float jumpMotion = MovementUtil.getJumpMotion(player);
            player.setDeltaMovement(player.getDeltaMovement().x, jumpMotion, player.getDeltaMovement().z);
        }

        int dirMultiplier = resolveDirectionMultiplier(player);
        checkObstacleAvoidance(player);

        // Automatic friction and potion scaling
        double effectiveSpeed = speed.get();
        effectiveSpeed = MovementUtil.applySpeedPotionEffects(player, effectiveSpeed);
        if (MovementUtil.isOnIce(player)) {
            float friction = MovementUtil.getBlockFriction(player);
            effectiveSpeed *= (0.6f / Math.max(0.6f, friction));
        }

        double angle = Math.atan2(playerPos.z - targetPos.z, playerPos.x - targetPos.x);
        double dist = Math.max(0.001, Math.max(playerPos.distanceTo(targetPos), r));
        angle += dirMultiplier * effectiveSpeed / dist;
        double x = targetPos.x + r * Math.cos(angle);
        double z = targetPos.z + r * Math.sin(angle);

        // Calculate rotation yaw towards tangent orbit point using RotationUtil
        float[] rots = RotationUtil.calculateRotations(playerPos, new Vec3(x, playerPos.y, z));
        float yaw = rots[0];

        // Apply motion using unified MovementUtil
        MovementUtil.setMotion(player, effectiveSpeed, yaw);
        requestSprintForMovementYaw(player, yaw);
    }

    private LivingEntity currentTarget() {
        KillAura killAura = Modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) {
            return null;
        }
        return killAura.getCurrentTarget();
    }

    public boolean shouldDisableAuraFreeCorrection() {
        return isEnabled() && isGrimMode() && currentTarget() != null;
    }

    private int resolveDirectionMultiplier(LocalPlayer player) {
        return switch (directionMode.get()) {
            case COUNTERCLOCKWISE -> -1;
            case CLOCKWISE -> 1;
            case RANDOM -> ((System.currentTimeMillis() / 3000L) % 2L == 0L) ? 1 : -1;
            case SMART -> currentDirection;
        };
    }

    private void checkObstacleAvoidance(LocalPlayer player) {
        if (player.horizontalCollision) {
            // Auto-jump over low obstacles if on ground
            if (autoJump.get() && player.onGround()) {
                float jumpMotion = MovementUtil.getJumpMotion(player);
                player.setDeltaMovement(player.getDeltaMovement().x, jumpMotion, player.getDeltaMovement().z);
            }

            // Auto-reverse direction if stuck against an obstacle
            long now = System.currentTimeMillis();
            if (now - lastDirectionSwitchTime > 350L) {
                currentDirection = -currentDirection;
                lastDirectionSwitchTime = now;
            }
        }
    }

    private Vec3 calculateOrbitPoint(Vec3 playerPos, Vec3 targetPos, double radius, int directionMultiplier) {
        double currentAngle = Math.atan2(playerPos.z - targetPos.z, playerPos.x - targetPos.x);
        double nextAngle = currentAngle + directionMultiplier * 0.45;
        return new Vec3(
                targetPos.x + Math.cos(nextAngle) * radius,
                playerPos.y,
                targetPos.z + Math.sin(nextAngle) * radius
        );
    }

    private boolean isAnyMovementKeyPressed() {
        return mc.options != null
                && (mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown());
    }

    private void requestSprintForMovementYaw(LocalPlayer player, float movementYaw) {
        float controlYaw = resolveControlYaw();
        float angleDiff = Mth.wrapDegrees(movementYaw - controlYaw);
        SprintController.INSTANCE.requestStartSprinting(mc, player, hasForwardMovement(angleDiff));
    }

    private boolean isMatrixMode() {
        return mode.get() == StrafeMode.MATRIX;
    }

    private boolean isGrimMode() {
        return mode.get() == StrafeMode.GRIM;
    }

    private boolean isAdaptiveMode() {
        return mode.get() == StrafeMode.ADAPTIVE;
    }

    @Getter
    @RequiredArgsConstructor
    private enum StrafeMode implements EnumValue.IdProvider {
        MATRIX("Matrix"),
        GRIM("Grim"),
        ADAPTIVE("Adaptive");

        private final String id;
    }

    @Getter
    @RequiredArgsConstructor
    private enum DirectionMode implements EnumValue.IdProvider {
        SMART("Smart"),
        CLOCKWISE("Clockwise"),
        COUNTERCLOCKWISE("Counterclockwise"),
        RANDOM("Random");

        private final String id;
    }
}
