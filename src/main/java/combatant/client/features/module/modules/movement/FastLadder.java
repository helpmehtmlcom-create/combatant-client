/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "fastladder",
        displayName = "FastLadder",
        aliases = {"SpeedLadder", "LadderBoost"},
        category = ModuleCategory.MOVEMENT,
        description = "Accelerates climbing and descending speed on ladders and climbable blocks."
)
public final class FastLadder extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> upSpeed =
            num("fastLadderUpSpeed", "up_speed", 0.28f, 0.15f, 1.0f);

    private final NumberValue<Float> downSpeed =
            num("fastLadderDownSpeed", "down_speed", 0.28f, 0.15f, 1.0f);

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        if (player.onClimbable()) {
            Vec3 v = player.getDeltaMovement();

            if (player.horizontalCollision || mc.options.keyUp.isDown()) {
                player.setDeltaMovement(v.x, upSpeed.get(), v.z);
            } else if (mc.options.keyDown.isDown()) {
                player.setDeltaMovement(v.x, -downSpeed.get(), v.z);
            }
        }
    }
}
