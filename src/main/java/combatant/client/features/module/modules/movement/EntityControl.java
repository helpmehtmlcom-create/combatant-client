/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "entitycontrol",
        displayName = "EntityControl",
        category = ModuleCategory.MOVEMENT
)
public final class EntityControl extends Module {

    private final BooleanValue controlUncontrolled = bool("control_uncontrolled", true);
    private final NumberValue<Double> speed = num("speed", 1.0, 0.2, 5.0);
    private final NumberValue<Double> jumpStrength = num("jump_strength", 1.0, 0.5, 2.0);
    private final BooleanValue noDismount = bool("no_dismount", false);
    private final BooleanValue boatFlyAssist = bool("boat_fly_assist", false);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null) return;
        LocalPlayer player = mc.player;
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;
        // Horse / rideable jump control
        if (vehicle instanceof net.minecraft.world.entity.PlayerRideableJumping jumpable) {
            if (jumpStrength.get() > 1.0 && mc.options.keyJump.isDown()) {
                jumpable.onPlayerJump((int) (jumpStrength.get() * 100));
            }
        }

        // Speed modification & steering for rideable entities
        if (speed.get() > 1.0 || boatFlyAssist.get()) {
            Vec3 delta = vehicle.getDeltaMovement();
            if (boatFlyAssist.get() && vehicle instanceof AbstractBoat && mc.options.keyJump.isDown()) {
                vehicle.setDeltaMovement(delta.x, 0.3, delta.z);
            }

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
                }

                double rad = Math.toRadians(yaw);
                double targetSpeed = 0.2873 * speed.get();
                double mx = -Math.sin(rad) * targetSpeed;
                double mz = Math.cos(rad) * targetSpeed;
                vehicle.setDeltaMovement(mx, vehicle.getDeltaMovement().y, mz);
            }
        }

        // Steer entity rotation to match player
        vehicle.setYRot(player.getYRot());
        if (vehicle instanceof LivingEntity living) {
            living.yBodyRot = player.getYRot();
            living.yHeadRot = player.getYRot();
        }
    }

    @EventHandler
    public void onInput(MovementInputEvent event) {
        if (!isEnabled() || mc.player == null || mc.player.getVehicle() == null) return;

        // Cancel accidental dismount when moving fast
        if (noDismount.get() && event.isSneak()) {
            Entity vehicle = mc.player.getVehicle();
            if (vehicle.getDeltaMovement().horizontalDistanceSqr() > 0.05) {
                event.setSneak(false);
            }
        }
    }

    public boolean canControlVehicle(Entity vehicle) {
        return isEnabled() && controlUncontrolled.get();
    }
}
