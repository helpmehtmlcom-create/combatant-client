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

@ModuleInfo(
        id = "entitycontrol",
        displayName = "EntityControl",
        aliases = {"HorseControl", "PigControl"},
        category = ModuleCategory.MOVEMENT,
        description = "Allows full control and steering of all rideable entities without saddles or control items."
)
public final class EntityControl extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> speed =
            num("entityControlSpeed", "speed", 0.8f, 0.2f, 3.0f);

    private final NumberValue<Float> jumpStrength =
            num("entityControlJumpStrength", "jump_strength", 0.6f, 0.3f, 1.5f);

    private final BooleanValue allEntities =
            bool("entityControlAllEntities", "all_entities", true);

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        Entity vehicle = player.getVehicle();
        if (vehicle == null) return;

        vehicle.setYRot(player.getYRot());

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
        double my = vehicle.getDeltaMovement().y;

        if (mc.options.keyJump.isDown() && vehicle.onGround()) {
            my = jumpStrength.get();
        }

        if (forward != 0.0 || strafe != 0.0 || mc.options.keyJump.isDown()) {
            vehicle.setDeltaMovement(mx, my, mz);
        }
    }
}
