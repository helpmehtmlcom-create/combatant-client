/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.MovementUtil;
import combatant.client.util.screen.ClientScreen;

@ModuleInfo(
        id = "fastswim",
        displayName = "FastSwim",
        category = ModuleCategory.MOVEMENT,
        description = "Accelerates swimming in water highways, oceans, and bubble columns."
)
public final class FastSwim extends Module {

    public enum Mode {
        BOOST,
        DOLPHIN,
        MOTION
    }

    private final EnumValue<Mode> mode = enumSetting("mode", "mode", Mode.BOOST);
    private final NumberValue<Double> speed = num("speed", "speed", 1.8, 1.0, 4.0);
    private final NumberValue<Double> verticalSpeed = num("verticalSpeed", "vertical_speed", 1.2, 0.5, 3.0);
    private final BooleanValue bubbleBoost = bool("bubbleBoost", "bubble_boost", true);
    private final BooleanValue lava = bool("lava", "lava", false);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;

        if (player.isFallFlying() || player.getAbilities().flying) return;
        if (!isInFluid(player)) return;

        // In DOLPHIN mode, apply a rhythmic buoyant kick when traveling in fluid
        if (mode.get() == Mode.DOLPHIN && MovementUtil.isMoving() && !isSneaking(player)) {
            Vec3 delta = player.getDeltaMovement();
            if (delta.y <= 0.08) {
                player.setDeltaMovement(delta.x, 0.16 * verticalSpeed.get(), delta.z);
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (event == null || event.getType() != MoverType.SELF) return;

        LocalPlayer player = mc.player;
        if (player.isFallFlying() || player.getAbilities().flying) return;
        if (!isInFluid(player)) return;

        Vec3 movement = event.getMovement();
        if (movement == null) movement = player.getDeltaMovement();

        double motionX = movement.x;
        double motionY = movement.y;
        double motionZ = movement.z;

        boolean jump = isJumping(player);
        boolean sneak = isSneaking(player);
        boolean moving = MovementUtil.isMoving();

        // 1. Horizontal movement amplification
        if (moving) {
            double baseSwimSpeed = (player.isSwimming() || player.isSprinting() ? 0.28 : 0.197) * speed.get();

            switch (mode.get()) {
                case MOTION -> {
                    double[] forward = MovementUtil.forward(baseSwimSpeed);
                    motionX = forward[0];
                    motionZ = forward[1];
                }
                case BOOST -> {
                    Vec2 moveVec = player.input != null ? player.input.getMoveVector() : Vec2.ZERO;
                    Vec3 inputVec = new Vec3(moveVec.x, 0.0, moveVec.y);
                    Vec3 strafed = MovementUtil.withStrafe(movement, inputVec, player.getYRot(), baseSwimSpeed, 0.85);
                    motionX = strafed.x;
                    motionZ = strafed.z;
                }
                case DOLPHIN -> {
                    double[] forward = MovementUtil.forward(baseSwimSpeed);
                    motionX = forward[0];
                    motionZ = forward[1];
                }
            }
        } else if (mode.get() == Mode.MOTION) {
            motionX = 0.0;
            motionZ = 0.0;
        }

        // 2. Vertical swim speed amplification
        if (jump && !sneak) {
            motionY = 0.18 * verticalSpeed.get();
        } else if (sneak && !jump) {
            motionY = -0.18 * verticalSpeed.get();
        } else if (moving && player.isSwimming() && Math.abs(player.getXRot()) > 15.0f) {
            // Pitch-assisted 3D diving and surfacing
            double pitchRad = Math.toRadians(player.getXRot());
            motionY = -Math.sin(pitchRad) * (0.22 * verticalSpeed.get());
        } else if (mode.get() == Mode.MOTION && !moving) {
            motionY = 0.0;
        }

        // 3. Bubble column boost: scales upward or downward velocity inside bubble columns
        if (bubbleBoost.get()) {
            Boolean dragDown = getBubbleColumnDrag(player);
            if (dragDown != null) {
                if (dragDown) {
                    // Downward bubble column (pull down)
                    double downMultiplier = sneak ? 1.5 : 1.0;
                    double targetDown = -0.4 * verticalSpeed.get() * downMultiplier;
                    motionY = Math.min(motionY, targetDown);
                } else {
                    // Upward bubble column (soul sand water highway)
                    double upMultiplier = jump ? 1.5 : 1.0;
                    double targetUp = 0.45 * verticalSpeed.get() * upMultiplier;
                    motionY = Math.max(motionY, targetUp);
                }
            }
        }

        Vec3 newMovement = new Vec3(motionX, motionY, motionZ);
        event.setMovement(newMovement);
        player.setDeltaMovement(newMovement);
    }

    private boolean isInFluid(LocalPlayer player) {
        if (player == null || mc.level == null) return false;

        boolean inWater = player.isInWater() || player.isUnderWater();
        if (inWater) return true;

        BlockPos feet = player.blockPosition();
        FluidState fluid = mc.level.getFluidState(feet);
        if (fluid.is(FluidTags.WATER)) return true;

        if (lava.get()) {
            if (player.isInLava() || fluid.is(FluidTags.LAVA)) return true;
        }

        // Also check if inside bubble column
        BlockState feetBlock = mc.level.getBlockState(feet);
        return feetBlock.is(Blocks.BUBBLE_COLUMN);
    }

    private Boolean getBubbleColumnDrag(LocalPlayer player) {
        if (player == null || mc.level == null) return null;

        BlockPos feet = player.blockPosition();
        BlockState feetState = mc.level.getBlockState(feet);
        if (feetState.is(Blocks.BUBBLE_COLUMN)) {
            return feetState.getValue(BubbleColumnBlock.DRAG_DOWN);
        }

        BlockPos eyes = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        BlockState eyeState = mc.level.getBlockState(eyes);
        if (eyeState.is(Blocks.BUBBLE_COLUMN)) {
            return eyeState.getValue(BubbleColumnBlock.DRAG_DOWN);
        }

        return null;
    }

    private boolean isJumping(LocalPlayer player) {
        if (player == null) return false;
        boolean keyHeld = ClientScreen.current() == null && mc.options != null && mc.options.keyJump.isDown();
        boolean inputJump = player.input != null && player.input.keyPresses != null && player.input.keyPresses.jump();
        return keyHeld || inputJump;
    }

    private boolean isSneaking(LocalPlayer player) {
        if (player == null) return false;
        boolean keyHeld = ClientScreen.current() == null && mc.options != null && mc.options.keyShift.isDown();
        boolean inputShift = player.input != null && player.input.keyPresses != null && player.input.keyPresses.shift();
        return keyHeld || inputShift;
    }
}
