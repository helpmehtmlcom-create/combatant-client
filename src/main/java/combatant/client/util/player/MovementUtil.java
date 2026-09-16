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

package combatant.client.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.Vec2;
import combatant.client.util.aiming.RotationManager;

public enum MovementUtil {
    ;

    /**
     * Returns the horizontal velocity magnitude of the player.
     */
    public static double getHorizontalMotion(Player player) {
        if (player == null) return 0.0;
        Vec3 delta = player.getDeltaMovement();
        return Math.hypot(delta.x, delta.z);
    }

    /**
     * Sets the horizontal velocity of the player based on yaw angle and speed.
     */
    public static void setMotion(Player player, double speed, float yaw) {
        if (player == null) return;
        double rad = Math.toRadians(yaw);
        double x = -Math.sin(rad) * speed;
        double z = Math.cos(rad) * speed;
        player.setDeltaMovement(x, player.getDeltaMovement().y, z);
    }

    /**
     * Sets the horizontal velocity of the player using current movement direction.
     */
    public static void setMotion(Player player, double speed) {
        if (player == null) return;
        float yaw = player instanceof LocalPlayer localPlayer
                ? getMovementDirectionYaw(localPlayer, localPlayer.getYRot())
                : player.getYRot();
        setMotion(player, speed, yaw);
    }

    /**
     * Calculates potion effect multiplier for speed (+20% per level) and slowness (-15% per level).
     */
    public static double getSpeedEffectMultiplier(Player player) {
        if (player == null) return 1.0;
        double multiplier = 1.0;
        MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
        if (speed != null) {
            multiplier += 0.2 * (speed.getAmplifier() + 1);
        }
        MobEffectInstance slowness = player.getEffect(MobEffects.SLOWNESS);
        if (slowness != null) {
            multiplier -= 0.15 * (slowness.getAmplifier() + 1);
        }
        return Math.max(0.0, multiplier);
    }

    /**
     * Scales a base speed according to current speed and slowness potion effects.
     */
    public static double applySpeedPotionEffects(Player player, double baseSpeed) {
        return baseSpeed * getSpeedEffectMultiplier(player);
    }

    /**
     * Returns base sprinting speed scaled by potion effects (speed +20%/lvl, slowness -15%/lvl).
     */
    public static double getBaseMoveSpeed(Player player) {
        double base = 0.2873;
        return applySpeedPotionEffects(player, base);
    }

    public static double getBaseMoveSpeed(LocalPlayer player) {
        return getBaseMoveSpeed((Player) player);
    }

    /**
     * Resolves the block position affecting the player's movement.
     */
    public static BlockPos getVelocityAffectingPos(Player player) {
        if (player == null) return BlockPos.ZERO;
        return BlockPos.containing(player.getX(), player.getBoundingBox().minY - 0.5000001, player.getZ());
    }

    /**
     * Returns the slipperiness / friction of the block beneath the player,
     * accounting for custom friction on blocks like ice, slime, and soul sand.
     */
    public static float getBlockFriction(Player player) {
        if (player == null || player.level() == null) return 0.6f;
        BlockPos pos = getVelocityAffectingPos(player);
        BlockState state = player.level().getBlockState(pos);
        if (state.is(Blocks.SLIME_BLOCK)) {
            return 0.8f;
        }
        return state.getBlock().getFriction();
    }

    /**
     * Checks if the block beneath the player is an ice block.
     */
    public static boolean isOnIce(Player player) {
        if (player == null || player.level() == null) return false;
        BlockPos pos = getVelocityAffectingPos(player);
        BlockState state = player.level().getBlockState(pos);
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE);
    }

    /**
     * Returns effective friction depending on in-water, in-lava, on-ground, and block slipperiness.
     */
    public static float getEffectiveFriction(Player player) {
        if (player == null) return 0.91f;
        if (player.isInWater()) return 0.8f;
        if (player.isInLava()) return 0.5f;
        BlockPos pos = getVelocityAffectingPos(player);
        if (player.level() != null && player.level().getBlockState(pos).is(Blocks.SOUL_SAND)) {
            return 0.4f * 0.91f;
        }
        float slipperiness = getBlockFriction(player);
        return player.onGround() ? slipperiness * 0.91f : 0.91f;
    }

    /**
     * Calculates horizontal motion scaling in water.
     */
    public static double calculateInWaterMotion(Player player, double baseSpeed) {
        if (player == null) return baseSpeed;
        double speed = baseSpeed * 0.8;
        if (player.hasEffect(MobEffects.DOLPHINS_GRACE)) {
            speed *= 1.2;
        }
        return speed;
    }

    /**
     * Calculates horizontal motion scaling in lava.
     */
    public static double calculateInLavaMotion(Player player, double baseSpeed) {
        if (player == null) return baseSpeed;
        return baseSpeed * 0.5;
    }

    /**
     * Calculates horizontal motion scaling on ice.
     */
    public static double calculateOnIceMotion(Player player, double baseSpeed) {
        if (player == null) return baseSpeed;
        float friction = getBlockFriction(player);
        return baseSpeed * (friction / 0.6f);
    }

    /**
     * Jump motion calculation (0.42f + jump boost amplifier * 0.1f, scaled by block jump factor).
     */
    public static float getJumpMotion(Player player) {
        return getJumpMotion(player, 0.42f);
    }

    /**
     * Jump motion calculation with custom base motion taking into account Jump Boost.
     */
    public static float getJumpMotion(Player player, float baseJumpMotion) {
        if (player == null) return baseJumpMotion;
        float jumpMotion = baseJumpMotion;
        if (player.level() != null) {
            BlockPos pos = getVelocityAffectingPos(player);
            jumpMotion *= player.level().getBlockState(pos).getBlock().getJumpFactor();
        }
        MobEffectInstance jumpBoost = player.getEffect(MobEffects.JUMP_BOOST);
        if (jumpBoost != null) {
            jumpMotion += (jumpBoost.getAmplifier() + 1) * 0.1f;
        }
        return jumpMotion;
    }

    public static boolean isMoving() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        return player != null
                && player.input != null
                && (player.input.getMoveVector().x != 0.0F || player.input.getMoveVector().y != 0.0F);
    }

    public static float getMovementDirectionYaw(LocalPlayer player, float fallbackYaw) {
        if (player == null || player.input == null) return fallbackYaw;

        var move = player.input.getMoveVector();
        float forward = move.y;
        float strafe = move.x;
        if (forward == 0.0f && strafe == 0.0f) return fallbackYaw;

        float yaw = resolveControlYaw(player, fallbackYaw);
        double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        double x = forward * cos + strafe * sin;
        double z = forward * sin - strafe * cos;
        if (Math.abs(x) < 1.0E-6 && Math.abs(z) < 1.0E-6) return fallbackYaw;

        return (float) Math.toDegrees(Math.atan2(z, x)) - 90.0f;
    }

    public static double[] forward(double speed) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player == null || player.input == null) return new double[]{0.0, 0.0};

        var move = player.input.getMoveVector();
        float forward = move.y;
        float strafe = move.x;
        float yaw = resolveControlYaw(player, player.getYRot());

        if (forward != 0.0f) {
            if (strafe > 0.0f) {
                yaw += (forward > 0.0f ? -45f : 45f);
            } else if (strafe < 0.0f) {
                yaw += (forward > 0.0f ? 45f : -45f);
            }
            strafe = 0.0f;
            if (forward > 0.0f) forward = 1.0f;
            else if (forward < 0.0f) forward = -1.0f;
        }

        double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        double x = forward * speed * cos + strafe * speed * sin;
        double z = forward * speed * sin - strafe * speed * cos;
        return new double[]{x, z};
    }

    public static double[] forwardWithoutStrafe(double speed) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player == null) return new double[]{0.0, 0.0};

        float yaw = resolveControlYaw(player, player.getYRot());
        double x = speed * Math.cos(Math.toRadians(yaw + 90.0f));
        double z = speed * Math.sin(Math.toRadians(yaw + 90.0f));
        return new double[]{x, z};
    }

    private static float resolveControlYaw(LocalPlayer player, float fallbackYaw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && player != null && player == mc.player) {
            var rotation = RotationManager.INSTANCE.getMovementRotation();
            if (rotation != null) {
                return rotation.yaw();
            }
        }
        return fallbackYaw;
    }

    /**
     * Strafe helper inspired by LiquidBounce.
     * Applies directional input to current horizontal speed with strength blend.
     */
    public static Vec3 withStrafe(Vec3 currentVelocity, Vec3 movementInput, float yaw, double speed, double strength) {
        if (currentVelocity == null) currentVelocity = Vec3.ZERO;
        if (movementInput == null || movementInput.lengthSqr() < 1.0E-7) {
            return new Vec3(0.0, currentVelocity.y, 0.0);
        }

        if (strength < 0.0) strength = 0.0;
        if (strength > 1.0) strength = 1.0;

        Vec3 input = movementInput;
        double lenSq = input.lengthSqr();
        if (lenSq > 1.0) {
            input = input.normalize();
        }

        double prevX = currentVelocity.x * (1.0 - strength);
        double prevZ = currentVelocity.z * (1.0 - strength);
        double useSpeed = speed * strength;

        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);
        double x = (input.x * cos - input.z * sin) * useSpeed + prevX;
        double z = (input.z * cos + input.x * sin) * useSpeed + prevZ;

        return new Vec3(x, currentVelocity.y, z);
    }

    /**
     * Strafe helper that uses a world-space direction vector.
     */
    public static Vec3 withStrafeDirection(Vec3 currentVelocity, Vec3 direction, double speed, double strength) {
        if (currentVelocity == null) currentVelocity = Vec3.ZERO;
        if (direction == null) {
            return new Vec3(0.0, currentVelocity.y, 0.0);
        }
        double dirLen = Math.hypot(direction.x, direction.z);
        if (dirLen < 1.0E-7) {
            return new Vec3(0.0, currentVelocity.y, 0.0);
        }

        if (strength < 0.0) strength = 0.0;
        if (strength > 1.0) strength = 1.0;

        double dirX = direction.x / dirLen;
        double dirZ = direction.z / dirLen;

        double prevX = currentVelocity.x * (1.0 - strength);
        double prevZ = currentVelocity.z * (1.0 - strength);
        double useSpeed = speed * strength;

        double x = dirX * useSpeed + prevX;
        double z = dirZ * useSpeed + prevZ;
        return new Vec3(x, currentVelocity.y, z);
    }

    /**
     * Uniform directional velocity adjustment (strafe).
     */
    public static void strafe(LocalPlayer player, double speed) {
        if (player == null) return;
        if (!isMoving()) {
            player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
            return;
        }
        float yaw = getMovementDirectionYaw(player, player.getYRot());
        setMotion(player, speed, yaw);
    }

    /**
     * Uniform directional velocity adjustment (strafe) with blend strength.
     */
    public static void strafe(LocalPlayer player, double speed, double strength) {
        if (player == null || player.input == null) return;
        if (!isMoving()) {
            player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
            return;
        }
        Vec2 input = player.input.getMoveVector();
        Vec3 movementInput = new Vec3(input.x, 0.0, input.y);
        Vec3 strafed = withStrafe(player.getDeltaMovement(), movementInput, player.getYRot(), speed, strength);
        player.setDeltaMovement(strafed.x, player.getDeltaMovement().y, strafed.z);
    }
}
