/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;

@ModuleInfo(
        id = "boatfly",
        displayName = "BoatFly",
        category = ModuleCategory.MOVEMENT,
        description = "Allows flying and full directional control while riding a boat or vehicle.",
        aliases = {"vehiclefly"}
)
public final class BoatFly extends Module {

    private final NumberValue<Double> horizontalSpeed = num("horizontal_speed", 1.5, 0.1, 5.0);
    private final NumberValue<Double> verticalSpeed = num("vertical_speed", 0.8, 0.1, 3.0);
    private final NumberValue<Double> glide = num("glide", 0.02, 0.0, 0.1);
    private final BooleanValue antiKick = bool("anti_kick", true);

    private final Minecraft mc = Minecraft.getInstance();
    private int antiKickTicks;

    public BoatFly() {
    }

    public NumberValue<Double> getHorizontalSpeed() {
        return horizontalSpeed;
    }

    public NumberValue<Double> getVerticalSpeed() {
        return verticalSpeed;
    }

    public NumberValue<Double> getGlide() {
        return glide;
    }

    public BooleanValue getAntiKick() {
        return antiKick;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null) return;

        LocalPlayer player = mc.player;

        // Synchronize vehicle yaw with player's look direction
        vehicle.setYRot(player.getYRot());

        // WASD movement inputs
        boolean forward = mc.options.keyUp.isDown();
        boolean backward = mc.options.keyDown.isDown();
        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();
        boolean jump = mc.options.keyJump.isDown();
        boolean sneak = mc.options.keyShift.isDown();

        double f = 0.0;
        double s = 0.0;
        if (forward) f += 1.0;
        if (backward) f -= 1.0;
        if (left) s += 1.0;
        if (right) s -= 1.0;

        double motionX = 0.0;
        double motionZ = 0.0;

        if (f != 0.0 || s != 0.0) {
            float moveYaw = player.getYRot();
            if (f > 0.0) {
                if (s > 0.0) moveYaw -= 45.0f;
                else if (s < 0.0) moveYaw += 45.0f;
            } else if (f < 0.0) {
                if (s > 0.0) moveYaw -= 135.0f;
                else if (s < 0.0) moveYaw += 135.0f;
                else moveYaw += 180.0f;
            } else {
                if (s > 0.0) moveYaw -= 90.0f;
                else if (s < 0.0) moveYaw += 90.0f;
            }

            double rad = Math.toRadians(moveYaw);
            double hSpeed = horizontalSpeed.get();
            motionX = -Math.sin(rad) * hSpeed;
            motionZ = Math.cos(rad) * hSpeed;
        }

        // Vertical velocity: spacebar up, shift down, downward glide otherwise
        double motionY = -glide.get();
        if (jump) {
            motionY = verticalSpeed.get();
        } else if (sneak) {
            motionY = -verticalSpeed.get();
        }

        // Anti-kick logic to prevent vehicle floating checks
        if (antiKick.get()) {
            antiKickTicks++;
            if (antiKickTicks >= 20) {
                antiKickTicks = 0;
                if (!jump && !sneak && glide.get() <= 0.0) {
                    motionY = -0.04;
                }
            }
        }

        vehicle.setDeltaMovement(motionX, motionY, motionZ);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || mc.player == null) return;

        if (event.getPacket() instanceof ServerboundMoveVehiclePacket) {
            // Retain valid vehicle movement state when flying
        }
    }

    @Override
    public void onDisable() {
        antiKickTicks = 0;
    }
}
