/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

@ModuleInfo(
        id = "boatfly",
        displayName = "BoatFly",
        aliases = {"BoatFlight"},
        category = ModuleCategory.MOVEMENT,
        description = "Allows flying and controlling boats in the air."
)
public final class BoatFly extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> speed =
            num("boatFlySpeed", "speed", 1.5f, 0.5f, 10.0f);

    private final NumberValue<Float> upSpeed =
            num("boatFlyUpSpeed", "up_speed", 1.0f, 0.2f, 5.0f);

    private final NumberValue<Float> downSpeed =
            num("boatFlyDownSpeed", "down_speed", 1.0f, 0.2f, 5.0f);

    private final BooleanValue antiKick =
            bool("boatFlyAntiKick", "anti_kick", true);

    private int tickCount = 0;

    @Override
    public void onDisable() {
        tickCount = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof AbstractBoat boat)) {
            return;
        }

        tickCount++;

        boat.setYRot(player.getYRot());

        float yaw = player.getYRot();
        double rad = Math.toRadians(yaw);
        double sin = -Math.sin(rad);
        double cos = Math.cos(rad);

        double forward = 0.0;
        double strafe = 0.0;

        if (mc.options.keyUp.isDown()) forward += 1.0;
        if (mc.options.keyDown.isDown()) forward -= 1.0;
        if (mc.options.keyLeft.isDown()) strafe -= 1.0;
        if (mc.options.keyRight.isDown()) strafe += 1.0;

        double moveSpeed = speed.get();
        double mx = (forward * sin + strafe * cos) * moveSpeed;
        double mz = (forward * cos - strafe * sin) * moveSpeed;
        double my = 0.0;

        if (mc.options.keyJump.isDown()) {
            my = upSpeed.get();
        } else if (mc.options.keySprint.isDown()) {
            my = -downSpeed.get();
        } else if (antiKick.get()) {
            if (tickCount % 20 == 0) {
                my = -0.04;
            }
        }

        boat.setDeltaMovement(mx, my, mz);
    }
}
