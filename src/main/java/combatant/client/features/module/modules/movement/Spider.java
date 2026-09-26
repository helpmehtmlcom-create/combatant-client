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
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "spider",
        displayName = "Spider",
        aliases = {"WallClimb", "SpiderClimb"},
        category = ModuleCategory.MOVEMENT,
        description = "Allows you to climb vertical walls like a spider."
)
public final class Spider extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> climbSpeed =
            num("spiderClimbSpeed", "climb_speed", 0.25f, 0.1f, 1.0f);

    private final BooleanValue resetFallDistance =
            bool("spiderResetFallDistance", "reset_fall_distance", true);

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        if (player.horizontalCollision) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, climbSpeed.get(), v.z);

            if (resetFallDistance.get()) {
                player.fallDistance = 0.0f;
            }
        }
    }
}
