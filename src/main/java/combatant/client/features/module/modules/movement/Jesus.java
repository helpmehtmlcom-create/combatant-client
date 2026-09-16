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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "jesus",
        displayName = "Jesus",
        category = ModuleCategory.MOVEMENT,
        description = "Allows walking across water and lava as if they were solid surfaces."
)
public final class Jesus extends Module {

    public enum Mode {
        SOLID,
        BOUNCE,
        DOLPHIN
    }

    private final EnumValue<Mode> mode = enumMode("mode", Mode.SOLID);
    private final BooleanValue water = bool("water", true);
    private final BooleanValue lava = bool("lava", true);
    private final NumberValue<Double> speed = num("speed", 1.0, 0.5, 2.5);
    private final NumberValue<Double> dip = num("dip", 0.05, 0.0, 0.3);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        LocalPlayer player = mc.player;

        if (player.isFallFlying() || player.getAbilities().flying) return;
        if (mc.options.keyShift.isDown()) return; // Allow sinking when sneaking

        boolean inFluid = isTargetFluid(player.blockPosition());
        boolean aboveFluid = isTargetFluid(player.blockPosition().below());

        if (inFluid || aboveFluid) {
            Vec3 delta = player.getDeltaMovement();

            switch (mode.get()) {
                case SOLID -> {
                    if (inFluid) {
                        player.setDeltaMovement(delta.x, 0.11, delta.z);
                    } else if (aboveFluid && delta.y < 0) {
                        player.setDeltaMovement(delta.x, -dip.get(), delta.z);
                        player.setOnGround(true);
                    }
                }
                case BOUNCE -> {
                    if (inFluid || (aboveFluid && delta.y < 0)) {
                        player.setDeltaMovement(delta.x, 0.42, delta.z);
                    }
                }
                case DOLPHIN -> {
                    if (inFluid) {
                        player.setDeltaMovement(delta.x, 0.25, delta.z);
                    }
                }
            }

            // Apply surface speed multiplier if moving
            if (speed.get() > 1.0 && (mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown())) {
                float yaw = player.getYRot();
                double f = 0.0;
                double s = 0.0;
                if (mc.options.keyUp.isDown()) f += 1.0;
                if (mc.options.keyDown.isDown()) f -= 1.0;
                if (mc.options.keyLeft.isDown()) s += 1.0;
                if (mc.options.keyRight.isDown()) s -= 1.0;

                double forward = f;
                double strafe = s;
                if (forward != 0.0) {
                    if (strafe > 0.0) {
                        yaw += (forward > 0.0 ? -45 : 45);
                    } else if (strafe < 0.0) {
                        yaw += (forward > 0.0 ? 45 : -45);
                    }
                    strafe = 0.0;
                    forward = forward > 0.0 ? 1.0 : -1.0;
                } else if (strafe != 0.0) {
                    yaw += (strafe > 0.0 ? -90 : 90);
                }

                double rad = Math.toRadians(yaw);
                double targetSpeed = 0.2873 * speed.get();
                double mx = -Math.sin(rad) * targetSpeed;
                double mz = Math.cos(rad) * targetSpeed;
                player.setDeltaMovement(mx, player.getDeltaMovement().y, mz);
            }
        }
    }

    private boolean isTargetFluid(BlockPos pos) {
        FluidState state = mc.level.getFluidState(pos);
        if (state.isEmpty()) return false;
        if (water.get() && !state.isEmpty() && state.getType().toString().contains("water")) return true;
        if (lava.get() && !state.isEmpty() && state.getType().toString().contains("lava")) return true;
        return false;
    }
}
