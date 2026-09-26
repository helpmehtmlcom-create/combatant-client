/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "fastswim",
        displayName = "FastSwim",
        aliases = {"SwimSpeed", "SpeedSwim"},
        category = ModuleCategory.MOVEMENT,
        description = "Accelerates swimming movement speed in water and lava."
)
public final class FastSwim extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> waterSpeed =
            num("fastSwimWaterSpeed", "water_speed", 1.5f, 1.0f, 5.0f);

    private final NumberValue<Float> lavaSpeed =
            num("fastSwimLavaSpeed", "lava_speed", 1.5f, 1.0f, 5.0f);

    private final NumberValue<Float> upFactor =
            num("fastSwimUpFactor", "up_factor", 1.3f, 1.0f, 3.0f);

    private final NumberValue<Float> downFactor =
            num("fastSwimDownFactor", "down_factor", 1.3f, 1.0f, 3.0f);

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || event.getMovement() == null) return;

        boolean inWater = player.isInWater();
        boolean inLava = player.isInLava();
        if (!inWater && !inLava) return;

        float horizontalMult = inWater ? waterSpeed.get() : lavaSpeed.get();
        Vec3 move = event.getMovement();

        double mx = move.x * horizontalMult;
        double my = move.y;
        double mz = move.z * horizontalMult;

        if (mc.options.keyJump.isDown()) {
            my *= upFactor.get();
        } else if (mc.options.keyShift.isDown()) {
            my *= downFactor.get();
        }

        event.setMovement(new Vec3(mx, my, mz));
    }
}
